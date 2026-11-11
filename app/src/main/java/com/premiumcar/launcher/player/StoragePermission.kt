package com.premiumcar.launcher.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Permissão de leitura das músicas: armazenamento até o Android 12, áudio a partir do 13. */
object StoragePermission {

    val name: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun isGranted(context: Context): Boolean = context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
}
