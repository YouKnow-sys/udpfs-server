package com.udpfs.app.core

fun volumeRootPath(
    externalFilesDirPath: String,
    packageName: String,
): String? {
    val suffix = "/Android/data/$packageName/files"
    return externalFilesDirPath.takeIf { it.endsWith(suffix) }?.removeSuffix(suffix)
}
