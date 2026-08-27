package com.udpfs.app.core

import androidx.core.content.ContextCompat
import android.provider.Settings
import android.os.Environment
import android.os.Build
import android.net.Uri
import android.content.pm.PackageManager
import android.content.Intent
import android.content.Context
import android.Manifest

object Permissions {

    private const val GRANTED = PackageManager.PERMISSION_GRANTED

    fun hasStorageAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == GRANTED
        }

    fun storageSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    fun legacyStoragePermissions(): Array<String>? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        } else {
            null
        }

    fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != GRANTED
}
