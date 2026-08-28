package com.udpfs.app.core

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.ReadOnlyFileSystemException
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
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
    } catch (e: Exception) {
        WriteAccess.ReadOnly
    }

private fun probeObservedTarget(target: File): WriteAccess {
    val attributes =
        try {
            Files.readAttributes(target.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (e: NoSuchFileException) {
            return WriteAccess.Inaccessible
        } catch (e: FileSystemException) {
            return WriteAccess.Inaccessible
        }
    return if (attributes.isDirectory) probeDirectory(target) else probeFile(target)
}

private fun probeDirectory(directory: File): WriteAccess {
    val probe = File(directory, PROBE_PREFIX + System.nanoTime()).toPath()
    try {
        Files.createFile(probe)
    } catch (e: NoSuchFileException) {
        return WriteAccess.Inaccessible
    } catch (e: AccessDeniedException) {
        return WriteAccess.ReadOnly
    } catch (e: ReadOnlyFileSystemException) {
        return WriteAccess.ReadOnly
    }
    val landedAtProbePath =
        try {
            Files.readAttributes(probe, BasicFileAttributes::class.java)
            true
        } catch (e: NoSuchFileException) {
            false
        }
    runCatching { Files.deleteIfExists(probe) }
    return if (landedAtProbePath) WriteAccess.Writable else WriteAccess.ReadOnly
}

private fun probeFile(file: File): WriteAccess =
    try {
        FileChannel.open(file.toPath(), StandardOpenOption.APPEND).use { WriteAccess.Writable }
    } catch (e: NoSuchFileException) {
        WriteAccess.Inaccessible
    } catch (e: AccessDeniedException) {
        WriteAccess.ReadOnly
    } catch (e: ReadOnlyFileSystemException) {
        WriteAccess.ReadOnly
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
    return access ?: WriteAccess.ReadOnly
}

suspend fun forcesReadOnly(path: String): Boolean = path.isNotBlank() && probeWriteAccessCapped(File(path)) == WriteAccess.ReadOnly
