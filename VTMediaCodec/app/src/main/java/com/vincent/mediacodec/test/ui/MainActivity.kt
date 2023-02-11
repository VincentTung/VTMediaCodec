package com.vincent.mediacodec.test.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vincent.mediacodec.test.R
import com.vincent.mediacodec.test.adapter.RecordFileListAdapter
import com.vincent.mediacodec.test.data.RecordFile
import com.vincent.mediacodec.test.logic.RecordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : BaseActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private val fileList = ArrayList<RecordFile>()
    private lateinit var recyclerView: RecyclerView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
        initMediaProjection()
        detectFileList()
    }

    private fun initView() {
        recyclerView = findViewById(R.id.recycler_view)

        val mLayoutManger: LinearLayoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = mLayoutManger
        recyclerView.setHasFixedSize(true)


        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            Log.d(TAG, "stopRecord click")
            RecordService.stopRecord(this)
        }
        findViewById<Button>(R.id.btn_decode).setOnClickListener {
            PlayActivity.start(this@MainActivity,
                filePath = "/data/user/0/com.vincent.mediacodec.test/files/1676103755796.mp4")
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


    private fun detectFileList() {
        fileList.clear()
        lifecycleScope.launch(Dispatchers.IO) {

            val directory: File = this@MainActivity.filesDir
            if (directory.exists()) {
                val files: Array<out File>? = directory.listFiles()

                files?.forEach {
                    fileList.add(RecordFile(it.path,it.length(),it.lastModified()))
                }
                withContext(Dispatchers.Main){
                    val adapter: RecordFileListAdapter = RecordFileListAdapter(fileList)
                    adapter.setOnItemClickListener(object :RecordFileListAdapter.OnItemClickListener{
                        override fun onItemClick(position: Int) {
                            PlayActivity.start(this@MainActivity,
                                filePath = fileList[position].filePath)
                        }
                    })
                    recyclerView.adapter = adapter
                }
            }

        }


    }


}