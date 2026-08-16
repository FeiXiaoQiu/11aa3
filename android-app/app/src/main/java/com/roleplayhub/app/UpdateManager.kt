package com.roleplayhub.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object UpdateManager {

    private const val TAG = "UpdateManager"

    data class UpdateResult(val success: Boolean, val message: String)

    fun installPackage(context: Context, zipPath: String): UpdateResult {
        val wwwRoot = WebAssetLoader.getWwwRoot()
        if (!wwwRoot.isDirectory) {
            wwwRoot.mkdirs()
        }
        val tmpDir = File(context.cacheDir, "update_tmp_" + System.currentTimeMillis())
        try {
            tmpDir.mkdirs()
            extractZip(zipPath, tmpDir)
            if (!File(tmpDir, "index.html").exists()) {
                deleteRecursiveQuietly(tmpDir)
                return UpdateResult(false, "压缩包不是有效的仓库包：缺少 index.html")
            }
            if (!File(tmpDir, "assets").isDirectory) {
                deleteRecursiveQuietly(tmpDir)
                return UpdateResult(false, "压缩包不是有效的仓库包：缺少 assets 目录")
            }
            replaceDirectory(wwwRoot, tmpDir)
            deleteRecursiveQuietly(tmpDir)
            return UpdateResult(true, "更新成功")
        } catch (e: Exception) {
            Log.e(TAG, "installPackage failed", e)
            deleteRecursiveQuietly(tmpDir)
            return UpdateResult(false, "更新失败：" + e.message)
        }
    }

    private fun extractZip(zipPath: String, dest: File) {
        ZipInputStream(FileInputStream(zipPath)).use { zis ->
            val root = dest.canonicalFile
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val target = File(root, entry.name).canonicalFile
                    if (target.path != root.path && !target.path.startsWith(root.path + File.separator)) {
                        throw SecurityException("更新包包含非法路径")
                    }
                    val parent = target.parentFile
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs()
                    }
                    FileOutputStream(target).use { out -> zis.copyTo(out) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun replaceDirectory(target: File, source: File) {
        val backup = File(target.parentFile, "www_backup")
        if (backup.exists()) {
            deleteRecursiveQuietly(backup)
        }
        if (target.exists()) {
            target.renameTo(backup)
        }
        try {
            source.copyRecursively(target, overwrite = false)
            deleteRecursiveQuietly(backup)
        } catch (e: Exception) {
            if (!target.exists() && backup.exists()) {
                backup.renameTo(target)
            }
            throw e
        }
    }

    private fun deleteRecursiveQuietly(file: File) {
        if (file.exists()) {
            file.deleteRecursively()
        }
    }
}
