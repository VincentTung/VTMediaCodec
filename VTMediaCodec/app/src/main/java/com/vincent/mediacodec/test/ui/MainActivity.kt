package com.vincent.mediacodec.test.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.vincent.mediacodec.test.R
import com.vincent.mediacodec.test.logic.RecordService
import com.vincent.mediacodec.test.logic.VTDecoder


class MainActivity : BaseActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var mediaProjectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
        initMediaProjection()
    }

    private fun initView() {
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            Log.d(TAG, "stopRecord click")
            RecordService.stopRecord(this)
        }
        findViewById<Button>(R.id.btn_decode).setOnClickListener {
            PlayActivity.start(this@MainActivity, filePath =  "/data/user/0/com.vincent.mediacodec.test/files/1676103755796.mp4")
        }

    }





    private fun initMediaProjection() {
        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent: Intent = mediaProjectionManager.createScreenCaptureIntent()
        val startActivity =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { it ->
                when (it.resultCode) {
                    Activity.RESULT_OK -> {
                        val return_data = it.data?.getStringExtra("data_return")
                        Log.d("FirstActivity", "return data is $return_data")
                        RecordService.startRecord(this, it.resultCode, it.data)

                    }
                    else -> {
                    }
                }
            }
        startActivity.launch(captureIntent)
    }




}