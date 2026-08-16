package com.roleplayhub.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.content.pm.ServiceInfo
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {

    private val tasks = ConcurrentHashMap<Int, DownloadTask>()
    private val nm: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val uiHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "下载", NotificationManager.IMPORTANCE_DEFAULT)
            channel.setShowBadge(false)
            channel.setSound(null, null)
            channel.description = "角色卡与文件的下载进度"
            nm.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        when (action) {
            ACTION_PAUSE -> {
                tasks[intent.getIntExtra(EXTRA_ID, -1)]?.pause()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val name = intent.getStringExtra(EXTRA_NAME) ?: "download"
                val mime = intent.getStringExtra(EXTRA_MIME)
                val id = nextId.getAndIncrement()
                val task = DownloadTask(id, url, name, mime)
                task.start()
                tasks[id] = task
                refreshForeground()
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                tasks[intent.getIntExtra(EXTRA_ID, -1)]?.cancel()
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                tasks[intent.getIntExtra(EXTRA_ID, -1)]?.resume()
                return START_NOT_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refreshForeground() {
        val active = tasks.values.firstOrNull { it.isAlive }
        if (active == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            ServiceCompat.startForeground(
                this,
                active.id,
                active.buildNotification(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
        }
    }

    private fun contentIntent(): PendingIntent {
        val i = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionIntent(id: Int, action: String): PendingIntent {
        val i = Intent(this, DownloadService::class.java)
        i.action = action
        i.putExtra(EXTRA_ID, id)
        return PendingIntent.getService(this, (id * 10) + action.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    inner class DownloadTask(
        val id: Int,
        private val url: String,
        private val fileName: String,
        private val mimeType: String?
    ) {
        @Volatile private var state = STATE_DOWNLOADING
        @Volatile private var written = 0L
        @Volatile private var total = -1L
        @Volatile private var pauseRequested = false
        @Volatile private var cancelled = false
        @Volatile private var connection: HttpURLConnection? = null
        @Volatile private var generation = 0
        private var errorMessage: String? = null

        val isAlive: Boolean
            get() = state == STATE_DOWNLOADING || state == STATE_PAUSED

        fun start() {
            generation++
            val gen = generation
            Thread({ runLoop(gen) }, "rphub-dl-$id").start()
        }

        fun pause() {
            if (state != STATE_DOWNLOADING) return
            pauseRequested = true
            state = STATE_PAUSED
            connection?.disconnect()
            publish()
        }

        fun resume() {
            if (state != STATE_PAUSED) return
            pauseRequested = false
            state = STATE_DOWNLOADING
            generation++
            publish()
            start()
        }

        fun cancel() {
            cancelled = true
            pauseRequested = false
            state = STATE_CANCELLED
            generation++
            connection?.disconnect()
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName + ".part").delete()
            tasks.remove(id)
            nm.cancel(id)
            refreshForeground()
        }

        private fun runLoop(gen: Int) {
            try {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists() && !dir.mkdirs()) {
                    throw Exception("无法创建下载目录")
                }
                val finalFile = File(dir, fileName)
                val partFile = File(dir, fileName + ".part")
                written = if (partFile.exists()) partFile.length() else 0L
                if (cancelled || gen != generation) return

                val conn = openConnection()
                connection = conn
                try {
                    when (conn.responseCode) {
                        200 -> {
                            written = 0L
                            partFile.delete()
                        }
                        206 -> Unit
                        else -> throw Exception("服务器返回 ${conn.responseCode}")
                    }
                    val len = try {
                        conn.contentLength.toLong()
                    } catch (e: Exception) {
                        -1L
                    }
                    total = if (len >= 0) written + len else -1L
                    publish()

                    var lastPublishTime = 0L
                    var lastPublishBytes = 0L
                    FileOutputStream(partFile, true).use { out ->
                        conn.inputStream.use { input ->
                            val buf = ByteArray(8192)
                            while (!pauseRequested && !cancelled && gen == generation) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                written += n
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastPublishTime >= 300 || written - lastPublishBytes >= 262144) {
                                    lastPublishTime = now
                                    lastPublishBytes = written
                                    publish()
                                }
                            }
                        }
                    }

                    if (pauseRequested || cancelled || gen != generation) return
                    if (total >= 0 && written != total) throw Exception("下载不完整")
                    if (!partFile.renameTo(finalFile)) throw Exception("保存失败")
                    state = STATE_COMPLETED
                    publish()
                } catch (e: Exception) {
                    conn.disconnect()
                    if (pauseRequested) {
                        state = STATE_PAUSED
                        publish()
                        return
                    }
                    if (!cancelled && gen == generation) throw e
                    if (cancelled) partFile.delete()
                } finally {
                    if (gen == generation) connection = null
                }
            } catch (e: Exception) {
                if (cancelled || gen != generation) return
                state = STATE_FAILED
                errorMessage = e.message ?: "未知错误"
                publish()
            }
        }

        private fun openConnection(): HttpURLConnection {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 0
            conn.instanceFollowRedirects = true
            conn.useCaches = false
            if (written > 0) {
                conn.setRequestProperty("Range", "bytes=$written-")
            }
            return conn
        }

        fun buildNotification(): Notification {
            val percent = if (total > 0) ((written * 100) / total).toInt().coerceIn(0, 100) else -1
            val sizeText = if (total > 0) {
                formatBytes(written) + " / " + formatBytes(total)
            } else {
                formatBytes(written)
            }
            val builder = NotificationCompat.Builder(this@DownloadService, CHANNEL_ID)
                .setContentTitle(fileName)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOnlyAlertOnce(true)
                .setOngoing(state == STATE_DOWNLOADING || state == STATE_PAUSED)
                .setContentIntent(contentIntent())
            when (state) {
                STATE_DOWNLOADING -> {
                    builder.setContentText("$sizeText · 下载中")
                    if (percent >= 0) {
                        builder.setProgress(100, percent, false)
                    } else {
                        builder.setProgress(0, 0, true)
                    }
                    builder.addAction(android.R.drawable.ic_media_pause, "暂停", actionIntent(id, ACTION_PAUSE))
                        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", actionIntent(id, ACTION_CANCEL))
                }
                STATE_PAUSED -> {
                    builder.setContentText("$sizeText · 已暂停")
                    if (percent >= 0) {
                        builder.setProgress(100, percent, false)
                    } else {
                        builder.setProgress(0, 0, true)
                    }
                    builder.addAction(android.R.drawable.ic_media_play, "继续", actionIntent(id, ACTION_RESUME))
                        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", actionIntent(id, ACTION_CANCEL))
                }
                STATE_COMPLETED -> {
                    builder.setContentText("已保存到 Download/$fileName（${formatBytes(written)}）")
                        .setProgress(0, 0, false)
                        .setAutoCancel(true)
                }
                STATE_FAILED -> {
                    builder.setContentText("下载失败：${errorMessage ?: "未知错误"}")
                        .setProgress(0, 0, false)
                        .setAutoCancel(true)
                }
            }
            return builder.build()
        }

        private fun publish() {
            uiHandler.post { doPublish() }
        }

        private fun doPublish() {
            nm.notify(id, buildNotification())
            if (state == STATE_COMPLETED || state == STATE_FAILED) {
                tasks.remove(id)
            }
            val active = tasks.values.firstOrNull { it.isAlive }
            if (active == null) {
                ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                this@DownloadService.stopSelf()
            } else {
                ServiceCompat.startForeground(
                    this@DownloadService,
                    active.id,
                    active.buildNotification(),
                    if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
                )
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "rphub_download"
        private const val ACTION_START = "com.roleplayhub.app.DOWNLOAD_START"
        private const val ACTION_PAUSE = "com.roleplayhub.app.DOWNLOAD_PAUSE"
        private const val ACTION_RESUME = "com.roleplayhub.app.DOWNLOAD_RESUME"
        private const val ACTION_CANCEL = "com.roleplayhub.app.DOWNLOAD_CANCEL"
        private const val EXTRA_ID = "extra_id"
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_NAME = "extra_name"
        private const val EXTRA_MIME = "extra_mime"

        private const val STATE_DOWNLOADING = 1
        private const val STATE_PAUSED = 2
        private const val STATE_COMPLETED = 3
        private const val STATE_FAILED = 4
        private const val STATE_CANCELLED = 5

        private val nextId = AtomicInteger(1000)

        fun start(context: Context, url: String, fileName: String, mimeType: String?) {
            val i = Intent(context, DownloadService::class.java)
            i.action = ACTION_START
            i.putExtra(EXTRA_URL, url)
            i.putExtra(EXTRA_NAME, fileName)
            i.putExtra(EXTRA_MIME, mimeType)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        private fun formatBytes(bytes: Long): String {
            if (bytes < 0) return "?"
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return String.format("%.1f MB", mb)
            return String.format("%.2f GB", mb / 1024.0)
        }
    }
}
