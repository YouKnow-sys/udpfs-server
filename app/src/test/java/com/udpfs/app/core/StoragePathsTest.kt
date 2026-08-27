package com.udpfs.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePathsTest {
    private val pkg = "com.udpfs.app"

    @Test
    fun `volumeRootPath strips the own-dir suffix from a primary emulated path`() {
        assertEquals(
            "/storage/emulated/0",
            volumeRootPath("/storage/emulated/0/Android/data/$pkg/files", pkg),
        )
    }

    @Test
    fun `volumeRootPath handles removable volumes without leaving an Android tail`() {
        assertEquals(
            "/storage/1A2B-3C4D",
            volumeRootPath("/storage/1A2B-3C4D/Android/data/$pkg/files", pkg),
        )
    }

    @Test
    fun `volumeRootPath returns null when the suffix doesn't match`() {
        assertNull(volumeRootPath("/data/user/0/$pkg/files", pkg))
        assertNull(volumeRootPath("/storage/emulated/0/Download", pkg))
        assertNull(volumeRootPath("", pkg))
    }

    @Test
    fun `removable volumes are distinguished from emulated primary storage`() {
        assertTrue(isRemovableVolumePath("/storage/1A2B-3C4D"))
        assertTrue(isRemovableVolumePath("/storage/1A2B-3C4D/Movies/film.iso"))
        assertFalse(isRemovableVolumePath("/storage/emulated/0"))
        assertFalse(isRemovableVolumePath("/storage/emulated/0/Movies"))
        assertFalse(isRemovableVolumePath("/data/local/tmp"))
    }

    @Test
    fun `pre-R removable paths outside the app dirs force read-only`() {
        assertTrue(forcesReadOnly(29, "/storage/1A2B-3C4D", pkg))
        assertTrue(forcesReadOnly(28, "/storage/1A2B-3C4D/Movies", pkg))
        assertTrue(forcesReadOnly(29, "/storage/1A2B-3C4D/game.iso", pkg))
    }

    @Test
    fun `pre-R removable paths inside the app dirs stay writable`() {
        assertFalse(forcesReadOnly(29, "/storage/1A2B-3C4D/Android/data/$pkg/files", pkg))
        assertFalse(forcesReadOnly(29, "/storage/1A2B-3C4D/Android/data/$pkg/files/share", pkg))
        assertFalse(forcesReadOnly(29, "/storage/1A2B-3C4D/Android/media/$pkg", pkg))
        assertTrue(forcesReadOnly(29, "/storage/1A2B-3C4D/Android/data/com.udpfs.app.evil", pkg))
    }

    @Test
    fun `pre-R primary storage and Android 11 plus never force read-only`() {
        assertFalse(forcesReadOnly(29, "/storage/emulated/0/Movies", pkg))
        assertFalse(forcesReadOnly(28, "/storage/emulated/0", pkg))
        assertFalse(forcesReadOnly(30, "/storage/1A2B-3C4D/Movies", pkg))
        assertFalse(forcesReadOnly(34, "/storage/1A2B-3C4D", pkg))
    }

    @Test
    fun `empty path never forces anything`() {
        assertFalse(forcesReadOnly(29, "", pkg))
    }
}
