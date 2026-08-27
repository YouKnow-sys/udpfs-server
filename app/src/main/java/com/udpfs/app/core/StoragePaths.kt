package com.udpfs.app.core

fun volumeRootPath(externalFilesDirPath: String, packageName: String): String? {
    val suffix = "/Android/data/$packageName/files"
    if (!externalFilesDirPath.endsWith(suffix)) return null
    return externalFilesDirPath.removeSuffix(suffix)
}

fun isRemovableVolumePath(path: String): Boolean =
    path.startsWith("/storage/") && !path.startsWith("/storage/emulated")

fun forcesReadOnly(sdkInt: Int, path: String, packageName: String): Boolean {
    if (sdkInt >= 30) return false
    if (!isRemovableVolumePath(path)) return false
    val normalized = path.trimEnd('/') + "/"
    val ownDataDir = "/Android/data/$packageName/"
    val ownMediaDir = "/Android/media/$packageName/"
    return !normalized.contains(ownDataDir) && !normalized.contains(ownMediaDir)
}
