package com.udpfs.app.core

private const val ALL_FILES_ACCESS_SDK = 30

fun volumeRootPath(externalFilesDirPath: String, packageName: String): String? {
    val suffix = "/Android/data/$packageName/files"
    return externalFilesDirPath.takeIf { it.endsWith(suffix) }?.removeSuffix(suffix)
}

fun isRemovableVolumePath(path: String): Boolean =
    path.startsWith("/storage/") && !path.startsWith("/storage/emulated")

private fun String.isWithinOwnAppDir(packageName: String): Boolean {
    val normalized = trimEnd('/') + "/"
    return normalized.contains("/Android/data/$packageName/") ||
        normalized.contains("/Android/media/$packageName/")
}

fun forcesReadOnly(sdkInt: Int, path: String, packageName: String): Boolean {
    if (sdkInt >= ALL_FILES_ACCESS_SDK) return false
    if (!isRemovableVolumePath(path)) return false
    return !path.isWithinOwnAppDir(packageName)
}
