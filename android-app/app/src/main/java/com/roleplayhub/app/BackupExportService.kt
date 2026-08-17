package com.roleplayhub.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * 明文备份导出的前台服务。
 *
 * 职责：导出期间保持进程存活、在通知栏展示导出进度（收集数据 / 打包 / 写入 zip），
 * 并在后台线程执行 zip 写入，避免 UI 线程阻塞。
 */
class BackupExportService : Service() {

    private val nm: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val uiHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "备份导出", NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            channel.setSound(null, null)
            channel.description = "数据备份导出的进度"
            nm.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        when (action) {
            ACTION_START_EXPORT -> {
                enterForeground("正在导出数据…", "正在收集数据", true)
            }
            ACTION_UPDATE_PROGRESS -> {
                val done = intent.getIntExtra(EXTRA_DONE, 0)
                val total = intent.getIntExtra(EXTRA_TOTAL, 0)
                val stage = intent.getStringExtra(EXTRA_STAGE) ?: "collect"
                val text = when (stage) {
                    "transfer" -> "正在打包备份 $done/$total"
                    else -> "正在收集数据 $done/$total"
                }
                if (total > 0 && done > 0) {
                    publish("正在导出数据…", text, ((done * 100) / total).coerceIn(0, 100), true)
                } else {
                    publish("正在导出数据…", text, -1, true)
                }
            }
            ACTION_WRITE_ZIP -> {
                val uri = intent.getParcelableExtra<Uri>(EXTRA_URI)
                val files = pendingFiles
                pendingFiles = null
                if (uri == null || files.isNullOrEmpty()) {
                    publish("导出失败", "备份数据为空", -1, false)
                    finish()
                    return START_NOT_STICKY
                }
                writeZipAsync(files, uri)
            }
            else -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun enterForeground(title: String, text: String, indeterminate: Boolean) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(title, text, -1, indeterminate),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
    }

    private fun publish(title: String, text: String, percent: Int, indeterminate: Boolean) {
        uiHandler.post {
            nm.notify(NOTIFICATION_ID, buildNotification(title, text, percent, indeterminate))
        }
    }

    private fun buildNotification(title: String, text: String, percent: Int, indeterminate: Boolean): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(indeterminate)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
        if (percent >= 0) {
            builder.setProgress(100, percent, false)
        } else if (indeterminate) {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun contentIntent(): PendingIntent {
        val i = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun writeZipAsync(files: List<PlainBackupManager.PlainFile>, uri: Uri) {
        enterForeground("正在写入备份文件…", "正在写入备份文件", true)
        Thread({
            val ok = PlainBackupManager.writeZip(files, uri, this) { done, total ->
                publish("正在写入备份文件…", "正在写入备份文件 $done/$total", (done * 100 / total).coerceIn(0, 100), false)
            }
            uiHandler.post {
                if (ok) {
                    nm.notify(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                        .setContentTitle("备份导出完成")
                        .setContentText("明文备份已保存到所选位置")
                        .setAutoCancel(true)
                        .setContentIntent(contentIntent())
                        .build())
                } else {
                    nm.notify(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_notify_error)
                        .setContentTitle("备份导出失败")
                        .setContentText("写入备份文件失败，请重试")
                        .setAutoCancel(true)
                        .setContentIntent(contentIntent())
                        .build())
                }
                finish()
            }
        }, "rphub-backup-write").start()
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "rphub_backup"
        private const val NOTIFICATION_ID = 2000
        private const val ACTION_START_EXPORT = "com.roleplayhub.app.BACKUP_START_EXPORT"
        private const val ACTION_UPDATE_PROGRESS = "com.roleplayhub.app.BACKUP_UPDATE_PROGRESS"
        private const val ACTION_WRITE_ZIP = "com.roleplayhub.app.BACKUP_WRITE_ZIP"
        private const val EXTRA_URI = "extra_uri"
        private const val EXTRA_DONE = "extra_done"
        private const val EXTRA_TOTAL = "extra_total"
        private const val EXTRA_STAGE = "extra_stage"

        @Volatile
        private var pendingFiles: List<PlainBackupManager.PlainFile>? = null

        fun startExport(context: Context) {
            val i = Intent(context, BackupExportService::class.java).setAction(ACTION_START_EXPORT)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun updateProgress(context: Context, done: Int, total: Int, stage: String) {
            val i = Intent(context, BackupExportService::class.java)
                .setAction(ACTION_UPDATE_PROGRESS)
                .putExtra(EXTRA_DONE, done)
                .putExtra(EXTRA_TOTAL, total)
                .putExtra(EXTRA_STAGE, stage)
            context.startService(i)
        }

        fun writeZip(context: Context, files: List<PlainBackupManager.PlainFile>, uri: Uri) {
            pendingFiles = files
            val i = Intent(context, BackupExportService::class.java)
                .setAction(ACTION_WRITE_ZIP)
                .putExtra(EXTRA_URI, uri)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
