package com.roleplayhub.app

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    private const val TAG = "BackupManager"

    fun exportData(context: Context, outUri: Uri): Boolean {
        return try {
            val dataDir = File(context.applicationContext.dataDir, "app_webview")
            if (!dataDir.isDirectory) {
                dataDir.mkdirs()
            }
            val resolver = context.contentResolver
            val output = resolver.openOutputStream(outUri) ?: throw IllegalStateException("无法打开备份文件")
            output.use { os ->
                ZipOutputStream(os).use { zos ->
                    dataDir.walkTopDown().forEach { file ->
                        val rel = file.relativeTo(dataDir).path
                        if (file.isDirectory) {
                            zos.putNextEntry(ZipEntry(rel + "/"))
                            zos.closeEntry()
                        } else {
                            zos.putNextEntry(ZipEntry(rel))
                            FileInputStream(file).use { input -> input.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "exportData failed", e)
            false
        }
    }

    fun restoreData(context: Context, zipUri: Uri): Boolean {
        return try {
            val dataDir = File(context.applicationContext.dataDir, "app_webview")
            dataDir.mkdirs()
            val resolver = context.contentResolver
            val input = resolver.openInputStream(zipUri) ?: throw IllegalStateException("无法打开备份文件")
            input.use { inputStream ->
                dataDir.copyRecursively(File(context.cacheDir, "backup_old"), overwrite = true)
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val root = dataDir.canonicalFile
                        val target = File(root, entry.name).canonicalFile
                        if (target.path != root.path && !target.path.startsWith(root.path + File.separator)) {
                            throw SecurityException("备份包含非法路径")
                        }
                        if (!entry.isDirectory) {
                            target.parentFile?.mkdirs()
                            FileOutputStream(target).use { out -> zis.copyTo(out) }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "restoreData failed", e)
            false
        }
    }

    fun dataSize(context: Context): Long {
        val dataDir = File(context.applicationContext.dataDir, "app_webview")
        if (!dataDir.isDirectory) return 0L
        return dataDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
