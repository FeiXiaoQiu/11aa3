package com.roleplayhub.app

import android.webkit.JavascriptInterface

class RoleplayHubNativeBridge(private val activity: MainActivity) {

    @JavascriptInterface
    fun saveImage(url: String) {
        if (url.startsWith("https://") || url.startsWith("http://")) {
            activity.requestSaveImage(url)
        }
    }

    @JavascriptInterface
    fun saveFile(base64: String, fileName: String, mimeType: String) {
        activity.requestSaveFile(base64, fileName, mimeType)
    }

    @JavascriptInterface
    fun setFullscreen(enabled: Boolean) {
        activity.setFullscreen(enabled)
    }

    @JavascriptInterface
    fun beginPlainBackup(fileCount: Int) {
        activity.beginPlainBackup(fileCount)
    }

    @JavascriptInterface
    fun beginPlainBackupFile(path: String, size: Int) {
        activity.beginPlainBackupFile(path, size)
    }

    @JavascriptInterface
    fun addPlainBackupChunk(base64: String) {
        activity.addPlainBackupChunk(base64)
    }

    @JavascriptInterface
    fun endPlainBackupFile() {
        activity.endPlainBackupFile()
    }

    @JavascriptInterface
    fun finishPlainBackup() {
        activity.finishPlainBackup()
    }

    @JavascriptInterface
    fun onBackupProgress(done: Int, total: Int, stage: String) {
        activity.onBackupProgress(done, total, stage)
    }
}
