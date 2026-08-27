package com.udpfs.app.core

import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import com.udpfs.udpfsbridge.Udpfsbridge
import com.udpfs.udpfsbridge.Logger
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
    private var startJob: Job? = null
    private var settings: Settings? = null

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

    fun init(context: Context) {
        if (settings != null) return
        val s = Settings(context.applicationContext)
        settings = s
        controller.setLogger(object : Logger {
            override fun onLog(level: String, message: String) = onBridgeLog(level, message)
        })
        // TODO: dont blindly overwrite local edits here
        scope.launch { s.config.collect { _config.value = it } }
        scope.launch {
            combine(status, _config) { st, cfg -> st to cfg.showStats }
                .collect { (st, show) ->
                    if (st is ServerStatus.Running && show) startPolling() else stopPolling()
                }
        }
    }

    fun start(): Boolean {
        if (_status.value != ServerStatus.Idle) return true
        val cfg = _config.value
        val issues = cfg.validate()
        if (issues.isNotEmpty()) {
            scope.launch { errors.emit(issues.joinToString("\n") { it.message }) }
            return false
        }
        _status.value = ServerStatus.Starting
        startJob = scope.launch {
            try {
                withContext(Dispatchers.IO) { controller.start(cfg.toBridgeConfig()) }
                if (!_status.compareAndSet(ServerStatus.Starting, ServerStatus.Running)) return@launch
                _stats.value = StatsSnapshot(running = true)
                _mount.value = withContext(Dispatchers.IO) {
                    controller.mountInfo().toSnapshot(controller.compressionFormats().toFormatList())
                }
            } catch (e: Exception) {
                _status.value = ServerStatus.Idle
                errors.emit(e.message ?: "Failed to start server")
            }
        }
        return true
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
                delay(1000)
                _stats.value = withContext(Dispatchers.IO) { controller.stats().let { s -> s.toSnapshot(buildList { repeat(s.peerCount.toInt()) { i -> controller.peer(i.toLong())?.let { add(it.toSnapshot()) } } }) } }
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
            while (logBuffer.size > 200) logBuffer.removeFirst()
            _logs.value = logBuffer.toList()
        }
    }
}

private fun String.toFormatList(): List<String> = split(',').filter { it.isNotBlank() }
