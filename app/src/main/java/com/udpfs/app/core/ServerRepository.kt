package com.udpfs.app.core

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.flow.combine
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

class ServerRepository(
    private val controller: BridgeController,
    private val configSource: Flow<ServerConfig>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val issueText: (ConfigIssueReason) -> String = { it.name },
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

    private val configLoaded = CompletableDeferred<Unit>()

    private val _stats = MutableStateFlow(StatsSnapshot())
    val stats: StateFlow<StatsSnapshot> = _stats.asStateFlow()

    private val _mount = MutableStateFlow(MountSnapshot())
    val mount: StateFlow<MountSnapshot> = _mount.asStateFlow()

    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    val logs: StateFlow<List<LogLine>> = _logs.asStateFlow()

    val errors = Channel<String>(Channel.BUFFERED)

    private val logBuffer = LogRingBuffer(MAX_LOG_LINES)
    private var flushJob: Job? = null

    private val logSignal = Channel<Unit>(capacity = Channel.CONFLATED)

    init {
        controller.setLogger { level, message -> onBridgeLog(level, message) }
        scope.launch {
            guardCancellations({
                configSource.collect {
                    synchronized(configLock) {
                        if (pendingWrites.get() == 0) _config.value = it
                    }
                    configLoaded.complete(Unit)
                }
            }) { e ->
                emitError("Failed to load settings: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        scope.launch {
            combine(status, _config, _stats.subscriptionCount) { st, cfg, collectors ->
                st is ServerStatus.Running && cfg.showStats && collectors > 0
            }.distinctUntilChanged().collect { shouldPoll ->
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
    }

    private fun emitError(message: String) {
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
        scope.launch {
            writeMutex.withLock {
                try {
                    persistConfig(_config.value)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    emitError("Failed to save settings: ${e.message ?: e.javaClass.simpleName}")
                } finally {
                    pendingWrites.decrementAndGet()
                }
                if (pendingWrites.get() == 0) {
                    val disk =
                        guardCancellations({ configSource.first() }) { e ->
                            emitError("Failed to load settings: ${e.message ?: e.javaClass.simpleName}")
                        } ?: return@withLock
                    synchronized(configLock) {
                        if (pendingWrites.get() == 0) _config.value = disk
                    }
                }
            }
        }
    }

    fun start() {
        if (!_status.compareAndSet(ServerStatus.Idle, ServerStatus.Starting)) return
        val job =
            scope.launch {
                writeMutex.withLock { }

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

                bridgeMutex.withLock {
                    guardCancellations({ withContext(Dispatchers.IO) { controller.start(effective) } }) { e ->
                        _status.compareAndSet(ServerStatus.Starting, ServerStatus.Idle)
                        emitError(e.message ?: "Failed to start server")
                    } ?: return@launch

                    if (!_status.compareAndSet(ServerStatus.Starting, ServerStatus.Running)) return@launch

                    _stats.value = StatsSnapshot(running = true)

                    _mount.value = guardCancellations({ withContext(Dispatchers.IO) { controller.mount() } }) { e ->
                        emitError(e.message ?: "Failed to read mount info")
                        if (_status.compareAndSet(ServerStatus.Running, ServerStatus.Idle)) {
                            withContext(Dispatchers.IO) { controller.stop() }
                            _stats.value = StatsSnapshot()
                            _mount.value = MountSnapshot()
                        }
                    } ?: return@launch
                }
            }
        startJob.set(job)
        job.invokeOnCompletion { startJob.compareAndSet(job, null) }
    }

    fun stop() {
        val wasStarting = _status.compareAndSet(ServerStatus.Starting, ServerStatus.Stopping)
        if (!wasStarting && !_status.compareAndSet(ServerStatus.Running, ServerStatus.Stopping)) return
        scope.launch {
            try {
                if (wasStarting) startJob.get()?.join()
                bridgeMutex.withLock {
                    withContext(Dispatchers.IO) { controller.stop() }
                }
            } catch (e: Exception) {
                emitError(e.message ?: "Failed to stop server")
            } finally {
                bridgeMutex.withLock {
                    _stats.value = StatsSnapshot()
                    _mount.value = MountSnapshot()
                    _status.value = ServerStatus.Idle
                }
            }
        }
    }

    fun localIP(): String = controller.localIP()

    fun clearLogs() {
        logBuffer.clear()
        _logs.value = emptyList()
    }

    fun activeConfig(): ServerConfig = _config.value

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
                        bridgeMutex.withLock {
                            if (_status.value is ServerStatus.Running) _stats.value = snapshot
                        }
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
