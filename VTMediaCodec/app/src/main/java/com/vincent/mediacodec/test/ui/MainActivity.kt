package com.vincent.mediacodec.test.ui

import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
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


class MainActivity : BaseActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private val fileList = ArrayList<RecordFile>()
    private lateinit var recyclerView: RecyclerView
    private val startActivity =
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
        initMediaProjection()

    }

    override fun onResume() {
        super.onResume()
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
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            val captureIntent: Intent = mediaProjectionManager.createScreenCaptureIntent()
            startActivity.launch(captureIntent)
        }

    }


    private fun initMediaProjection() {
        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    }


    private fun detectFileList() {
        fileList.clear()
        lifecycleScope.launch(Dispatchers.IO) {

            val directory: File = this@MainActivity.filesDir
            if (directory.exists()) {
                val files: Array<out File>? = directory.listFiles()

                files?.forEach {
                    fileList.add(RecordFile(it.path, getDuration(it.path), it.lastModified()))
                }
                withContext(Dispatchers.Main) {
                    val adapter: RecordFileListAdapter = RecordFileListAdapter(fileList)
                    adapter.setOnItemClickListener(object :
                        RecordFileListAdapter.OnItemClickListener {
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


    fun getLocalVideoDuration(filePath: String?): Long {
        var duration = 0L
        duration = try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(filePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!
                .toLong()  //除以 1000 返回是秒
            //时长(毫秒)
            //            String duration = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            //            //宽
            //            String width = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            //            //高
            //            String height = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        } catch (e: Exception) {
            e.printStackTrace()
            return duration
        }
        return duration
    }

    private fun getDuration(filePath: String): Long{
        val player = MediaPlayer()
        try {
            player.setDataSource(filePath) //filePath为文件的路径
            player.prepare()
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "getDuration: $e")
        }
        val duration = player.duration //获取媒体文件时长
        Log.d(TAG, "getDuration: $duration")
        player.release() //记得释放资源
        return (duration / 1L)
    }

}