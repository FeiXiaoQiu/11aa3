package com.roleplayhub.app

import android.os.Environment
import android.util.Base64
import java.io.File
import java.net.URLDecoder
import java.util.Locale

object FileDownloader {

    fun saveBase64(base64: String, fileName: String, mimeType: String?, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists() && !dir.mkdirs()) {
                    throw Exception("无法创建下载目录")
                }
                val rawName = if (fileName.isBlank()) {
                    "download_" + System.currentTimeMillis() + "." + extensionForMime(mimeType)
                } else {
                    fileName
                }
                val name = sanitize(rawName)
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                File(dir, name).writeBytes(bytes)
                onResult(true, "已保存到 Download/$name")
            } catch (e: Exception) {
                onResult(false, "下载失败：" + (e.message ?: "未知错误"))
            }
        }.start()
    }

    fun resolveFileName(url: String, contentDisposition: String?, mimeType: String?): String {
        val fromCd = contentDisposition?.let { parseFileName(it) }
        if (!fromCd.isNullOrBlank()) {
            return sanitize(fromCd)
        }
        val fromUrl = url.substringAfterLast('/', "").substringBefore('?')
        if (fromUrl.isNotBlank()) {
            val decoded = try {
                URLDecoder.decode(fromUrl, "UTF-8")
            } catch (e: Exception) {
                fromUrl
            }
            return sanitize(decoded)
        }
        return "download_" + System.currentTimeMillis() + "." + extensionForMime(mimeType)
    }

    private fun parseFileName(cd: String): String? {
        val star = Regex("filename\\*\\s*=\\s*UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(cd)
        if (star != null) {
            val raw = star.groupValues[1].trim().trim('"')
            return try {
                URLDecoder.decode(raw, "UTF-8")
            } catch (e: Exception) {
                raw
            }
        }
        val plain = Regex("filename\\s*=\\s*\"?([^\";]+)", RegexOption.IGNORE_CASE).find(cd)
        if (plain != null) {
            return plain.groupValues[1].trim()
        }
        return null
    }

    private fun sanitize(name: String): String {
        val cleaned = Regex("[\\\\/:*?\"<>|\\r\\n\\t]").replace(name, "_").trim()
        return if (cleaned.isBlank() || cleaned == "." || cleaned == "..") "download" else cleaned
    }

    private fun extensionForMime(mimeType: String?): String {
        return when (mimeType?.toLowerCase(Locale.ROOT)) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            "application/zip" -> "zip"
            "image/gif" -> "gif"
            "image/png" -> "png"
            "application/json" -> "json"
            "text/plain" -> "txt"
            else -> "bin"
        }
    }
}
