package com.udpfs.app.core

import android.os.Build
import android.os.SystemClock
import com.udpfs.udpfsbridge.Udpfsbridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val packageName: String = "",
    private val issueText: (ConfigIssueReason) -> String = { it.name },
) {
    private var pollJob: Job? = null

    private val bridgeMutex = Mutex()

    private val startJob = AtomicReference<Job?>(null)

    private val _status = MutableStateFlow<ServerStatus>(ServerStatus.Idle)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _config = MutableStateFlow(ServerConfig())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    private val _stats = MutableStateFlow(StatsSnapshot())
    val stats: StateFlow<StatsSnapshot> = _stats.asStateFlow()

    private val _mount = MutableStateFlow(MountSnapshot())
    val mount: StateFlow<MountSnapshot> = _mount.asStateFlow()

    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    val logs: StateFlow<List<LogLine>> = _logs.asStateFlow()

    val errors = Channel<String>(Channel.UNLIMITED)

    private val logBuffer = LogRingBuffer(MAX_LOG_LINES)

    private val logSignal = Channel<Unit>(capacity = Channel.CONFLATED)

    init {
        controller.setLogger { level, message -> onBridgeLog(level, message) }
        scope.launch {
            guardCancellations({ configSource.collect { _config.value = it } }) { e ->
                errors.trySend("Failed to load settings: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        scope.launch {
            combine(status, _config) { st, cfg -> st to cfg.showStats }
                .distinctUntilChanged()
                .collect { (st, show) ->
                    if (st is ServerStatus.Running && show) startPolling() else stopPolling()
                }
        }
        scope.launch {
            while (true) {
                logSignal.receive()
                delay(LOG_FLUSH_MS)
                logBuffer.snapshot().takeIf { it.isNotEmpty() }?.let { _logs.value = it }
            }
        }
    }

    fun start() {
        if (!_status.compareAndSet(ServerStatus.Idle, ServerStatus.Starting)) return
        val job =
            scope.launch {
                val cfg =
                    guardCancellations({ awaitConfig() }) { e ->
                        _status.compareAndSet(ServerStatus.Starting, ServerStatus.Idle)
                        errors.trySend("Failed to load settings: ${e.message ?: e.javaClass.simpleName}")
                    } ?: return@launch

                val issues = cfg.validate()
                if (issues.isNotEmpty()) {
                    _status.compareAndSet(ServerStatus.Starting, ServerStatus.Idle)
                    errors.trySend(issues.joinToString("\n", transform = issueText))
                    return@launch
                }

                val effective =
                    if (forcesReadOnly(sdkInt, cfg.activeStoragePath, packageName)) {
                        cfg.copy(readOnly = true)
                    } else {
                        cfg
                    }

                bridgeMutex.withLock {
                    guardCancellations({ withContext(Dispatchers.IO) { controller.start(effective) } }) { e ->
                        _status.compareAndSet(ServerStatus.Starting, ServerStatus.Idle)
                        errors.trySend(e.message ?: "Failed to start server")
                    } ?: return@launch

                    if (!_status.compareAndSet(ServerStatus.Starting, ServerStatus.Running)) return@launch

                    _stats.value = StatsSnapshot(running = true)

                    _mount.value = guardCancellations({ withContext(Dispatchers.IO) { controller.mount() } }) { e ->
                        errors.trySend(e.message ?: "Failed to read mount info")
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
                errors.trySend(e.message ?: "Failed to stop server")
            } finally {
                bridgeMutex.withLock {
                    _stats.value = StatsSnapshot()
                    _mount.value = MountSnapshot()
                    _status.value = ServerStatus.Idle
                }
            }
        }
    }

    fun localIP(): String = Udpfsbridge.getLocalIP()

    fun activeConfig(): ServerConfig = _config.value

    suspend fun awaitConfig(): ServerConfig = configSource.first()

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob =
            scope.launch {
                while (isActive) {
                    val startedAt = SystemClock.elapsedRealtime()
                    val snapshot =
                        guardCancellations({ withContext(Dispatchers.IO) { controller.stats() } }) { e ->
                            errors.trySend("Stats update failed: ${e.message ?: e.javaClass.simpleName}")
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
        level: String,
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
