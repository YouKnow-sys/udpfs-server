package com.udpfs.app.core

import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import com.udpfs.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

sealed interface ServerStatus {
    data object Idle : ServerStatus

    data object Starting : ServerStatus

    data object Running : ServerStatus

    data object Stopping : ServerStatus
}

enum class RepoMessage(
    @StringRes val resId: Int,
) {
    SELF_STOPPED(R.string.error_server_self_stopped),
    STOP_INCOMPLETE(R.string.error_stop_incomplete),
}

class ServerRepository(
    private val controller: BridgeController,
    private val configSource: Flow<ServerConfig>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val issueText: (ConfigIssueReason) -> String = { it.name },
    private val messageText: (RepoMessage) -> String = { it.name },
    private val persistConfig: suspend (ServerConfig) -> Unit = {},
) {
    private var pollJob: Job? = null

    private val bridgeMutex = Mutex()

    private val startJob = AtomicReference<Job?>(null)

    private val _status = MutableStateFlow<ServerStatus>(ServerStatus.Idle)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _config = MutableStateFlow(ServerConfig())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    private val writeMutex = Mutex()
    private val pendingWrites = AtomicInteger(0)
    private val configLock = Any()

    private val persistSignal = Channel<Unit>(capacity = Channel.CONFLATED)

    private val configLoaded = CompletableDeferred<Unit>()

    private val _stats = MutableStateFlow(StatsSnapshot())
    val stats: StateFlow<StatsSnapshot> = _stats.asStateFlow()

    private val _mount = MutableStateFlow(MountSnapshot())
    val mount: StateFlow<MountSnapshot> = _mount.asStateFlow()

    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    val logs: StateFlow<List<LogLine>> = _logs.asStateFlow()

    val errors = Channel<String>(Channel.BUFFERED)

    private val lastError = AtomicReference<String?>(null)

    private val logBuffer = LogRingBuffer(MAX_LOG_LINES)
    private var flushJob: Job? = null

    private val logSignal = Channel<Unit>(capacity = Channel.CONFLATED)

    init {
        controller.setLogger { level, message -> onBridgeLog(level, message) }
        scope.launch {
            guardCancellations({
                configSource.collect { disk ->
                    writeMutex.withLock {
                        synchronized(configLock) {
                            if (pendingWrites.get() == 0) _config.value = disk
                        }
                        configLoaded.complete(Unit)
                    }
                }
            }) { e ->
                emitError("Failed to load settings: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        scope.launch {
            status
                .map { it is ServerStatus.Running }
                .distinctUntilChanged()
                .collect { shouldPoll ->
                    if (shouldPoll) startPolling() else stopPolling()
                }
        }
        scope.launch {
            _logs.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .collect { active ->
                    flushJob?.cancel()
                    flushJob = null
                    if (active) {
                        flushJob =
                            launch {
                                while (true) {
                                    logSignal.receive()
                                    delay(LOG_FLUSH_MS)
                                    _logs.value = logBuffer.snapshot().asReversed()
                                }
                            }
                    }
                }
        }
        scope.launch {
            for (kick in persistSignal) {
                delay(PERSIST_DEBOUNCE_MS)
                persistNow()
            }
        }
    }

    private fun emitError(message: String) {
        if (lastError.getAndSet(message) == message) return
        if (errors.trySend(message).isFailure) Log.w("ServerRepository", message)
    }

    fun updateConfig(transform: (ServerConfig) -> ServerConfig) {
        if (!configLoaded.isCompleted) {
            scope.launch {
                configLoaded.await()
                updateConfig(transform)
            }
            return
        }
        synchronized(configLock) {
            val current = _config.value
            val next = transform(current)
            if (next == current) return
            _config.value = next
            pendingWrites.incrementAndGet()
        }
        persistSignal.trySend(Unit)
    }

    private suspend fun persistNow() {
        writeMutex.withLock {
            val folded =
                synchronized(configLock) {
                    val pending = pendingWrites.get()
                    if (pending == 0) return@withLock
                    pending
                }
            try {
                persistConfig(_config.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitError("Failed to save settings: ${e.message ?: e.javaClass.simpleName}")
                val disk =
                    guardCancellations({ configSource.first() }) { loadFailure ->
                        emitError("Failed to load settings: ${loadFailure.message ?: loadFailure.javaClass.simpleName}")
                    } ?: return@withLock
                synchronized(configLock) {
                    if (pendingWrites.get() == folded) _config.value = disk
                }
            } finally {
                pendingWrites.addAndGet(-folded)
            }
        }
    }

    fun start() {
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                persistNow()

                val cfg =
                    guardCancellations({ awaitConfig() }) { e ->
                        _status.compareAndSet(ServerStatus.Starting, ServerStatus.Idle)
                        emitError("Failed to load settings: ${e.message ?: e.javaClass.simpleName}")
                    } ?: return@launch

                val issues = cfg.validate()
                if (issues.isNotEmpty()) {
                    _status.compareAndSet(ServerStatus.Starting, ServerStatus.Idle)
                    emitError(issues.joinToString("\n", transform = issueText))
                    return@launch
                }

                val effective = cfg.copy(readOnly = cfg.readOnly || forcesReadOnlyFor(cfg))

                guardCancellations({ withContext(Dispatchers.IO) { controller.start(effective) } }) { e ->
                    bridgeMutex.withLock { _status.compareAndSet(ServerStatus.Starting, ServerStatus.Idle) }
                    emitError(e.message ?: "Failed to start server")
                } ?: return@launch

                val claimed =
                    bridgeMutex.withLock {
                        if (_status.compareAndSet(ServerStatus.Starting, ServerStatus.Running)) {
                            _stats.value = StatsSnapshot(running = true)
                            true
                        } else {
                            false
                        }
                    }
                if (!claimed) {
                    withContext(Dispatchers.IO) { controller.stop() }
                    return@launch
                }

                val mount =
                    guardCancellations({ withContext(Dispatchers.IO) { controller.mount() } }) { e ->
                        emitError(e.message ?: "Failed to read mount info")
                    }
                if (mount == null) {
                    withContext(Dispatchers.IO) { controller.stop() }
                    bridgeMutex.withLock {
                        if (_status.compareAndSet(ServerStatus.Running, ServerStatus.Idle)) {
                            _stats.value = StatsSnapshot()
                            _mount.value = MountSnapshot()
                        }
                    }
                    return@launch
                }
                bridgeMutex.withLock {
                    if (_status.value is ServerStatus.Running) _mount.value = mount
                }
            }
        startJob.set(job)
        if (!_status.compareAndSet(ServerStatus.Idle, ServerStatus.Starting)) {
            startJob.compareAndSet(job, null)
            job.cancel()
            return
        }
        job.start()
        job.invokeOnCompletion { startJob.compareAndSet(job, null) }
    }

    fun stop() {
        val wasStarting = _status.compareAndSet(ServerStatus.Starting, ServerStatus.Stopping)
        if (!wasStarting && !_status.compareAndSet(ServerStatus.Running, ServerStatus.Stopping)) return
        scope.launch {
            try {
                if (wasStarting) startJob.get()?.join()
                withContext(Dispatchers.IO) { controller.stop() }
                bridgeMutex.withLock {
                    _stats.value = StatsSnapshot()
                    _mount.value = MountSnapshot()
                    _status.value = ServerStatus.Idle
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val alive =
                    guardCancellations({ withContext(Dispatchers.IO) { controller.isRunning() } }) { } ?: false
                if (alive) {
                    _status.compareAndSet(ServerStatus.Stopping, ServerStatus.Running)
                    emitError(messageText(RepoMessage.STOP_INCOMPLETE))
                } else {
                    _stats.value = StatsSnapshot()
                    _mount.value = MountSnapshot()
                    _status.value = ServerStatus.Idle
                    emitError(e.message ?: "Failed to stop server")
                }
            }
        }
    }

    fun clearLogs() {
        logBuffer.clear()
        _logs.value = emptyList()
    }

    suspend fun awaitConfig(): ServerConfig = configSource.first()

    private suspend fun forcesReadOnlyFor(cfg: ServerConfig): Boolean =
        coroutineScope {
            val fsRoot = async { forcesReadOnly(cfg.fsRoot) }
            val blockDevice = async { forcesReadOnly(cfg.blockDevice) }
            fsRoot.await() || blockDevice.await()
        }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob =
            scope.launch {
                while (isActive) {
                    val startedAt = SystemClock.elapsedRealtime()
                    val snapshot =
                        guardCancellations({ withContext(Dispatchers.IO) { controller.stats() } }) { e ->
                            emitError("Stats update failed: ${e.message ?: e.javaClass.simpleName}")
                        }
                    if (snapshot != null) {
                        var selfStopped = false
                        bridgeMutex.withLock {
                            if (_status.value is ServerStatus.Running) {
                                if (snapshot.running) {
                                    _stats.value = snapshot
                                } else {
                                    _stats.value = StatsSnapshot()
                                    _mount.value = MountSnapshot()
                                    _status.value = ServerStatus.Idle
                                    selfStopped = true
                                }
                            }
                        }
                        if (selfStopped) emitError(messageText(RepoMessage.SELF_STOPPED))
                    }
                    val elapsed = (SystemClock.elapsedRealtime() - startedAt).milliseconds
                    delay((POLL_INTERVAL_MS - elapsed).coerceAtLeast(ZERO))
                }
            }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun onBridgeLog(
        level: LogLevel,
        message: String,
    ) {
        logBuffer.append(level, message)
        logSignal.trySend(Unit)
    }
}

private const val MAX_LOG_LINES = 200
private val LOG_FLUSH_MS = 250.milliseconds
private val PERSIST_DEBOUNCE_MS = 300.milliseconds
private val POLL_INTERVAL_MS = 1_000.milliseconds

private inline fun <T> guardCancellations(
    block: () -> T,
    onError: (Exception) -> Unit,
): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e)
        null
    }
