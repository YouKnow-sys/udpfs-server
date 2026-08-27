package com.udpfs.app.core

import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.*
import com.udpfs.udpfsbridge.Udpfsbridge
import com.udpfs.udpfsbridge.Logger
import android.os.SystemClock
import android.os.Build
import android.content.Context

sealed interface ServerStatus {
    data object Idle : ServerStatus
    data object Starting : ServerStatus
    data object Running : ServerStatus
    data object Stopping : ServerStatus
}

object ServerRepository {

    private val controller = Udpfsbridge.newServer()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    @Volatile
    private var startJob: Job? = null

    @Volatile
    private var settings: Settings? = null

    @Volatile
    private var appContext: Context? = null

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

    val errors = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val logBuffer = ArrayDeque<LogLine>()
    private val logSeq = java.util.concurrent.atomic.AtomicLong()

    private val logSignal = Channel<Unit>(capacity = Channel.CONFLATED)

    @Synchronized
    fun init(context: Context) {
        if (settings != null) return
        val appCtx = context.applicationContext
        val s = Settings(appCtx)
        appContext = appCtx
        settings = s
        controller.setLogger { level, message ->
            onBridgeLog(level, message)
        }
        scope.launch {
            try {
                s.config.collect { _config.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.emit("Failed to load settings: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        scope.launch {
            combine(status, _config) { st, cfg -> st to cfg.showStats }
                .collect { (st, show) ->
                    if (st is ServerStatus.Running && show) startPolling() else stopPolling()
                }
        }
        scope.launch {
            while (true) {
                logSignal.receive()
                delay(LOG_FLUSH_MS)
                synchronized(logBuffer) {
                    if (logBuffer.isNotEmpty()) _logs.value = logBuffer.toList()
                }
            }
        }
    }

    fun start() {
        if (!_status.compareAndSet(ServerStatus.Idle, ServerStatus.Starting)) return
        startJob = scope.launch {
            val cfg = awaitConfig()
            val issues = cfg.validate()
            if (issues.isNotEmpty()) {
                _status.value = ServerStatus.Idle
                errors.emit(issues.joinToString("\n") { issue ->
                    appContext?.getString(issue.reason.resId) ?: issue.reason.name
                })
                return@launch
            }
            val effective = if (forcesReadOnly(Build.VERSION.SDK_INT, cfg.activeStoragePath, appContext?.packageName.orEmpty())) {
                cfg.copy(readOnly = true)
            } else {
                cfg
            }
            try {
                withContext(Dispatchers.IO) { controller.start(effective.toBridgeConfig()) }
                if (!_status.compareAndSet(ServerStatus.Starting, ServerStatus.Running)) return@launch
                _stats.value = StatsSnapshot(running = true)
                _mount.value = withContext(Dispatchers.IO) {
                    controller.mountInfo().toSnapshot(controller.compressionFormats().toFormatList())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = ServerStatus.Idle
                errors.emit(e.message ?: "Failed to start server")
            }
        }
    }

    fun stop() {
        if (_status.value !in listOf(ServerStatus.Running, ServerStatus.Starting)) return
        _status.value = ServerStatus.Stopping
        scope.launch {
            try {
                startJob?.join()
                withContext(Dispatchers.IO) { controller.stop() }
            } catch (e: Exception) {
                errors.emit(e.message ?: "Failed to stop server")
            } finally {
                _stats.value = StatsSnapshot()
                _mount.value = MountSnapshot()
                _status.value = ServerStatus.Idle
            }
        }
    }

    fun localIP(): String = Udpfsbridge.getLocalIP()

    fun activeConfig(): ServerConfig = _config.value

    suspend fun awaitConfig(): ServerConfig = settings?.config?.first() ?: ServerConfig()

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    _stats.value = withContext(Dispatchers.IO) {
                        val s = controller.stats()
                        val peers = buildList {
                            repeat(s.peerCount.toInt()) { i ->
                                controller.peer(i.toLong())?.let { add(it.toSnapshot()) }
                            }
                        }
                        s.toSnapshot(peers)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errors.emit("Stats update failed: ${e.message ?: e.javaClass.simpleName}")
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

    private fun onBridgeLog(level: String, message: String) {
        synchronized(logBuffer) {
            logBuffer.addLast(LogLine(logSeq.incrementAndGet(), System.currentTimeMillis(), level, message))
            while (logBuffer.size > MAX_LOG_LINES) logBuffer.removeFirst()
        }
        logSignal.trySend(Unit)
    }
}

private const val MAX_LOG_LINES = 200
private val LOG_FLUSH_MS = 250.milliseconds
private val POLL_INTERVAL_MS = 1_000.milliseconds

private fun String.toFormatList(): List<String> = split(',').filter { it.isNotBlank() }
