package com.udpfs.app.core

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

enum class WriteAccess {
    WRITABLE,

    READ_ONLY,

    INACCESSIBLE,
}

const val WRITE_PROBE_TIMEOUT_MS = 2_500L

private const val PROBE_PREFIX = ".udpfs-write-probe-"

fun probeWriteAccess(target: File): WriteAccess =
    try {
        probeObservedTarget(target)
    } catch (e: Exception) {
        WriteAccess.READ_ONLY
    }

private fun probeObservedTarget(target: File): WriteAccess {
    val attributes =
        try {
            Files.readAttributes(target.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (e: NoSuchFileException) {
            return WriteAccess.INACCESSIBLE
        } catch (e: FileSystemException) {
            return WriteAccess.INACCESSIBLE
        }
    return if (attributes.isDirectory) probeDirectory(target) else probeFile(target)
}

private fun probeDirectory(directory: File): WriteAccess {
    val probe = File(directory, PROBE_PREFIX + System.nanoTime()).toPath()
    try {
        Files.createFile(probe)
    } catch (e: NoSuchFileException) {
        return WriteAccess.INACCESSIBLE
    } catch (e: AccessDeniedException) {
        return WriteAccess.READ_ONLY
    } catch (e: ReadOnlyFileSystemException) {
        return WriteAccess.READ_ONLY
    }
    val landedAtProbePath =
        try {
            Files.readAttributes(probe, BasicFileAttributes::class.java)
            true
        } catch (e: NoSuchFileException) {
            false
        }
    runCatching { Files.deleteIfExists(probe) }
    return if (landedAtProbePath) WriteAccess.WRITABLE else WriteAccess.READ_ONLY
}

private fun probeFile(file: File): WriteAccess =
    try {
        FileChannel.open(file.toPath(), StandardOpenOption.APPEND).use { WriteAccess.WRITABLE }
    } catch (e: NoSuchFileException) {
        WriteAccess.INACCESSIBLE
    } catch (e: AccessDeniedException) {
        WriteAccess.READ_ONLY
    } catch (e: ReadOnlyFileSystemException) {
        WriteAccess.READ_ONLY
    }
