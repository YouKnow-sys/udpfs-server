package com.udpfs.app.core

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

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

    fun requestStorageAccess(
        context: Context,
        launcher: ActivityResultLauncher<Array<String>>,
    ) {
        val legacy = legacyStoragePermissions()
        if (legacy != null) {
            launcher.launch(legacy)
            return
        }
        try {
            context.startActivity(storageSettingsIntent(context))
        } catch (e: ActivityNotFoundException) {
            openAppDetails(context)
        } catch (e: SecurityException) {
            openAppDetails(context)
        }
    }

    private fun openAppDetails(context: Context) {
        try {
            context.startActivity(appDetailsIntent(context))
        } catch (e: ActivityNotFoundException) {
        }
    }

    private fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
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
