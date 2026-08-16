package com.roleplayhub.app

import android.os.Environment
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object ImageSaver {

    private const val DIR_NAME = "RPHub"
    private const val NOMEDIA_CONTENT = "/storage/emulated/0/Download/RPHub/"

    fun saveImageFromUrl(url: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val parsedUrl = URL(url)
                if (parsedUrl.protocol != "http" && parsedUrl.protocol != "https") {
                    throw IllegalArgumentException("图片地址无效")
                }
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME)
                val created = !dir.exists() && dir.mkdirs()
                if (!dir.exists()) {
                    throw Exception("无法创建下载目录")
                }
                if (created) {
                    try {
                        File(dir, ".nomedia").writeText(NOMEDIA_CONTENT)
                    } catch (e: Exception) {
                        // 忽略 .nomedia 写入失败
                    }
                }
                val (bytes, mime) = download(url)
                val name = "roleplayhub_" + System.currentTimeMillis() + "." + extensionForMime(mime)
                File(dir, name).writeBytes(bytes)
                onResult(true, "图片已保存到 Download/RPHub")
            } catch (e: Exception) {
                onResult(false, "保存失败：" + (e.message ?: "未知错误"))
            }
        }.start()
    }

    private fun download(url: String): Pair<ByteArray, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 60000
        connection.instanceFollowRedirects = true
        connection.useCaches = false
        try {
            val contentType = connection.contentType
            val mime = contentType?.substringBefore(';')?.trim()?.takeIf { it.startsWith("image/") } ?: "image/png"
            connection.inputStream.use { input ->
                return Pair(input.readBytes(), mime)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extensionForMime(mime: String): String {
        return when (mime.toLowerCase(Locale.ROOT)) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> "png"
        }
    }
}
