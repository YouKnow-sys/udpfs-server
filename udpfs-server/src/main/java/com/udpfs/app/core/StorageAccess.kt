package com.udpfs.app.core

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.Executors

enum class WriteAccess {
    Writable,
    ReadOnly,
    Inaccessible,
}

const val WRITE_PROBE_TIMEOUT_MS = 2_500L

private const val PROBE_PREFIX = ".udpfs-write-probe-"

fun probeWriteAccess(target: File): WriteAccess =
    try {
        probeObservedTarget(target)
    } catch (_: Exception) {
        WriteAccess.Inaccessible
    }

private fun probeObservedTarget(target: File): WriteAccess {
    val attributes =
        try {
            Os.lstat(target.absolutePath)
        } catch (_: ErrnoException) {
            return WriteAccess.Inaccessible
        }
    return if (OsConstants.S_ISDIR(attributes.st_mode)) probeDirectory(target) else probeFile(target)
}

private fun classifyWriteErrno(errno: Int): WriteAccess =
    when (errno) {
        OsConstants.EACCES, OsConstants.EPERM, OsConstants.EROFS -> WriteAccess.ReadOnly
        else -> WriteAccess.Inaccessible
    }

private fun probeDirectory(directory: File): WriteAccess {
    val probe = File(directory, PROBE_PREFIX + System.nanoTime())
    val fd =
        try {
            Os.open(
                probe.absolutePath,
                OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_WRONLY,
                OsConstants.S_IRUSR or OsConstants.S_IWUSR,
            )
        } catch (e: ErrnoException) {
            return classifyWriteErrno(e.errno)
        }

    runCatching { Os.close(fd) }
    runCatching { Os.remove(probe.absolutePath) }
    return WriteAccess.Writable
}

private fun probeFile(file: File): WriteAccess =
    try {
        val fd = Os.open(file.absolutePath, OsConstants.O_WRONLY or OsConstants.O_APPEND, 0)
        runCatching { Os.close(fd) }
        WriteAccess.Writable
    } catch (e: ErrnoException) {
        classifyWriteErrno(e.errno)
    }

private val probeDispatcher =
    Executors
        .newFixedThreadPool(2) { r -> Thread(r, "udpfs-write-probe").apply { isDaemon = true } }
        .asCoroutineDispatcher()

suspend fun probeWriteAccessCapped(target: File): WriteAccess {
    val access =
        withTimeoutOrNull(WRITE_PROBE_TIMEOUT_MS) {
            withContext(probeDispatcher) { probeWriteAccess(target) }
        }
    return access ?: WriteAccess.Inaccessible
}

suspend fun forcesReadOnly(path: String): Boolean = path.isNotBlank() && probeWriteAccessCapped(File(path)) == WriteAccess.ReadOnly
