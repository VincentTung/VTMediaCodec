package com.vincent.mediacodec.test
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.vincent.mediacodec.RecordService


class MainActivity : AppCompatActivity() {
    companion object {
        private const val  TAG = "MainActivity"
    }

    private lateinit var mMediaProjectionManager: MediaProjectionManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
        initMediaProjection()
    }

    private fun initView() {
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            Log.d(TAG,"stopRecord click")
            RecordService.stopRecord(this)
        }

    }

    private fun initMediaProjection() {
        mMediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent: Intent = mMediaProjectionManager.createScreenCaptureIntent()
       val  startActivity =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { it ->
                when (it.resultCode) {
                    Activity.RESULT_OK -> {
                        val return_data = it.data?.getStringExtra("data_return")
                        Log.d("FirstActivity", "return data is $return_data")
                        RecordService.startRecord(this,it.resultCode,it.data)

                    }
                    else -> {
                    }
                }
            }
        startActivity.launch(captureIntent)
    }


}