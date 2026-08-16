package com.roleplayhub.app

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

object WebAssetLoader {
    const val DOMAIN = "appassets.androidplatform.net"
    const val START_URL = "https://appassets.androidplatform.net/index.html"

    private const val PREFS = "rphub_asset_meta"
    private const val KEY_BUILTIN = "builtin_www_version"

    private var wwwRoot: File? = null

    fun initialize(context: Context) {
        wwwRoot = File(context.filesDir, "www")
        val builtinVersion = currentVersionCode(context)
        val needsSync = !(wwwRoot!!.isDirectory &&
                File(wwwRoot!!, "index.html").exists() &&
                context.getSharedPreferences(PREFS, 0).getInt(KEY_BUILTIN, 0) == builtinVersion)
        if (needsSync) {
            syncAssetsToFiles(context)
            context.getSharedPreferences(PREFS, 0).edit().putInt(KEY_BUILTIN, builtinVersion).apply()
        }
    }

    fun getWwwRoot(): File {
        return wwwRoot ?: throw IllegalStateException("wwwRoot 未初始化")
    }

    private fun currentVersionCode(context: Context): Int {
        return try {
            val info: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
        } catch (e: Exception) {
            0
        }
    }

    private fun syncAssetsToFiles(context: Context) {
        try {
            if (wwwRoot!!.exists()) {
                wwwRoot!!.deleteRecursively()
            }
            wwwRoot!!.mkdirs()
            val assets = context.assets.list("www") ?: return
            for (entry in assets) {
                copyAssetRecursively(context, "www/$entry", wwwRoot!!)
            }
        } catch (e: Exception) {
            // 忽略同步失败，运行时回退到 assets
        }
    }

    private fun copyAssetRecursively(context: Context, assetPath: String, destDir: File) {
        val assets = context.assets
        val children = assets.list(assetPath)
        if (children != null && children.isNotEmpty()) {
            File(destDir, assetPath.substringAfter("www/")).mkdirs()
            for (entry in children) {
                copyAssetRecursively(context, "$assetPath/$entry", destDir)
            }
            return
        }
        val relative = assetPath.substringAfter("www/")
        val target = File(destDir, relative)
        target.parentFile?.mkdirs()
        assets.open(assetPath).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }

    fun buildLoader(context: Context): WebViewAssetLoader {
        return WebViewAssetLoader.Builder()
            .setDomain(DOMAIN)
            .addPathHandler("/") { path ->
                val root = wwwRoot!!.canonicalFile
                val file = File(root, path).canonicalFile
                if (file.path != root.path && !file.path.startsWith(root.path + File.separator)) {
                    fallbackToAssets(context, path)
                } else if (file.isFile) {
                    WebResourceResponse(MimeTypes.forPath(file.name), "utf-8", FileInputStream(file))
                } else {
                    fallbackToAssets(context, path)
                }
            }
            .build()
    }

    private fun fallbackToAssets(context: Context, path: String): WebResourceResponse? {
        return try {
            WebViewAssetLoader.AssetsPathHandler(context).handle(path)
        } catch (e: Exception) {
            null
        }
    }

    fun fileForPath(path: String): File = File(wwwRoot, path)
}

object MimeTypes {
    private val map = mapOf(
        "html" to "text/html",
        "htm" to "text/html",
        "js" to "application/javascript",
        "mjs" to "application/javascript",
        "css" to "text/css",
        "json" to "application/json",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "svg" to "image/svg+xml",
        "webp" to "image/webp",
        "ico" to "image/x-icon",
        "woff" to "font/woff",
        "woff2" to "font/woff2",
        "ttf" to "font/ttf",
        "otf" to "font/otf",
        "mp3" to "audio/mpeg",
        "mp4" to "video/mp4",
        "webm" to "video/webm",
        "txt" to "text/plain",
        "xml" to "text/xml",
        "pdf" to "application/pdf",
        "zip" to "application/zip"
    )

    fun forPath(name: String): String {
        val ext = name.substringAfterLast('.', "").toLowerCase(Locale.ROOT)
        return map[ext] ?: "application/octet-stream"
    }
}

fun WebView.loadRoleplayContent() {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.allowFileAccess = true
    settings.allowContentAccess = true
    settings.setSupportMultipleWindows(false)
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    settings.mediaPlaybackRequiresUserGesture = false
    if (isDebugBuild(context)) {
        WebView.setWebContentsDebuggingEnabled(true)
    }
    webViewClient = object : WebViewClientCompat() {
        override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
            val url = request.url.toString()
            return !url.startsWith("https://appassets.androidplatform.net")
        }
    }
}

private fun isDebugBuild(context: Context): Boolean =
    (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
