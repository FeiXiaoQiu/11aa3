package com.roleplayhub.app

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object PlainBackupManager {

    private const val TAG = "PlainBackupManager"

    data class PlainFile(val path: String, val bytes: ByteArray)

    fun writeZip(files: List<PlainFile>, targetFile: java.io.File, onProgress: ((done: Int, total: Int) -> Unit)? = null): Boolean {
        return try {
            val output = java.io.FileOutputStream(targetFile)
            output.use { os ->
                ZipOutputStream(os).use { zos ->
                    val seen = HashSet<String>()
                    val total = files.size
                    var done = 0
                    files.forEach { file ->
                        val path = sanitizePath(file.path)
                        if (!seen.add(path)) return@forEach
                        zos.putNextEntry(ZipEntry(path))
                        zos.write(file.bytes)
                        zos.closeEntry()
                        done += 1
                        onProgress?.invoke(done, total)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "writeZip failed", e)
            false
        }
    }

    fun readZip(context: Context, uri: Uri): List<PlainFile>? {
        return try {
            val resolver = context.contentResolver
            val input = resolver.openInputStream(uri) ?: return null
            val files = mutableListOf<PlainFile>()
            input.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val baos = ByteArrayOutputStream()
                            copyTo(zis, baos)
                            files.add(PlainFile(entry.name, baos.toByteArray()))
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            files
        } catch (e: Exception) {
            Log.e(TAG, "readZip failed", e)
            null
        }
    }

    fun isPlainBackupZip(files: List<PlainFile>): Boolean {
        val manifest = files.firstOrNull { it.path.trimEnd('/') == "manifest.json" } ?: return false
        val text = try {
            String(manifest.bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return false
        }
        return text.contains("rphub-plain-backup")
    }

    fun readUriBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            val resolver = context.contentResolver
            val input = resolver.openInputStream(uri) ?: return null
            val baos = ByteArrayOutputStream()
            input.use { copyTo(it, baos) }
            baos.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "readUriBytes failed", e)
            null
        }
    }

    fun encodeBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun copyTo(input: InputStream, output: ByteArrayOutputStream) {
        val buffer = ByteArray(8192)
        var read = input.read(buffer)
        while (read > 0) {
            output.write(buffer, 0, read)
            read = input.read(buffer)
        }
    }

    private fun sanitizePath(path: String): String {
        val cleaned = path.replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")
        return cleaned.ifBlank { "file" }
    }
}
