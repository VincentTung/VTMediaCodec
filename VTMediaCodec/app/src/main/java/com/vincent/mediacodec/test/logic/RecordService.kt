package com.vincent.mediacodec.test.logic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.vincent.mediacodec.test.R

/**
 *
 *   负责录屏后台Service
 *
 */

class RecordService : Service() {
    companion object {
        /**
         *  开始录屏
         */
        fun startRecord(context: Context, resultCode: Int, data: Intent?) {
            startService(context, CMD_START_RECORD, resultCode, data)
        }

        /**
         * 停止录屏
         */
        fun stopRecord(context: Context) {
            startService(context, CMD_STOP_RECORD, null, null)
        }

        @JvmStatic
        fun startService(context: Context, cmd: Int?, resultCode: Int?, data: Intent?) {
            val starter = Intent(context, RecordService::class.java)
                .putExtra("cmd", cmd)
                .putExtra("resultCode", resultCode)
                .putExtra("data", data)
            context.startService(starter)
        }

        const val NOTIFICATION_CHANNEL_ID = "222"
        const val NOTIFICATION_CHANNEL_DESC = "des_record"
        const val NOTIFICATION_CHANNEL_NAME = "recording"
        const val NOTIFICATION_TICKER = "录屏中..."
        const val NOTIFICATION_ID = 123

        const val CMD_START_RECORD = 1
        const val CMD_STOP_RECORD = 2
        const val CMD_UNKNOWN = -1

        private const val TAG = "RecordService"
    }

    private lateinit var recorder: VTRecorder
    private lateinit var notificationManager: NotificationManager
    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        val cmd: Int = intent.getIntExtra("cmd", CMD_UNKNOWN)
        val resultCode: Int = intent.getIntExtra("resultCode", -1)
        val resultData: Intent? = intent.getParcelableExtra<Parcelable>("data") as Intent?
        Log.d(TAG, "onStartCommand:  cmd:${cmd}___resultCode:${resultCode}")

        when (cmd) {
            CMD_UNKNOWN -> {}
            CMD_START_RECORD -> {
                showRecordingNotification()
                recorder = VTRecorder(this, resultCode, resultData)
                recorder.start()
            }
            CMD_STOP_RECORD -> {
                hideRecordingNotification()
                recorder.stop( )
            }
            else -> {
            }

        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun hideRecordingNotification() {
        stopSelf()
    }


    private fun showRecordingNotification() {
        Log.i(TAG, "notification: " + Build.VERSION.SDK_INT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //Call Start foreground with notification
            val notificationIntent = Intent(this, RecordService::class.java)
            val pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            val notificationBuilder: NotificationCompat.Builder =
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setLargeIcon(
                        BitmapFactory.decodeResource(
                            resources,
                            R.drawable.ic_launcher_foreground
                        )
                    )
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Starting Service")
                    .setContentText("Starting monitoring service")
                    .setTicker(NOTIFICATION_TICKER)
                    .setContentIntent(pendingIntent)
            val notification = notificationBuilder.build()
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = NOTIFICATION_CHANNEL_DESC

            notificationManager.createNotificationChannel(channel)
//            notificationManager.notify(NOTIFICATION_ID, notification);
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

}