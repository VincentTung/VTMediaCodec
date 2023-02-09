package com.vincent.mediacodec.test

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.annotation.RequiresApi
import java.io.File
import java.nio.ByteBuffer

class VTRecorder(
    private val context: Context,
    private val resultCode: Int,
    private val resultData: Intent?
) {

    companion object {

        private const val DEFAULT_I_FRAME_INTERVAL = 10 // seconds

        private const val REPEAT_FRAME_DELAY_US = 100000 // repeat after 100ms

        private const val KEY_MAX_FPS_TO_ENCODER = "max-fps-to-encoder"

        // Keep the values in descending order
        private val MAX_SIZE_FALLBACK = intArrayOf(2560, 1920, 1600, 1280, 1024, 800)

        private const val PACKET_FLAG_CONFIG = 1L shl 63
        private const val PACKET_FLAG_KEY_FRAME = 1L shl 62

        private const val TAG = "VTRecorder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
    }


    private var hasMuxerStarted: Boolean = false
    private var encodeVideoTrackIndex: Int = 0
    private lateinit var mMediaMuxer: MediaMuxer
    private lateinit var saveFile: File
    private lateinit var mediaProjection: MediaProjection
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var mediaCodec: MediaCodec

    init {
        initFile()
        initMediaProjection()
    }

    private fun initFile() {
        saveFile = File(
            this.context.filesDir.path + "/${System.currentTimeMillis()}.mp4"
        )
        if (saveFile.exists()) {
            saveFile.delete()
            saveFile.createNewFile()
        }
        Log.d(TAG, "path:${saveFile.path}")
    }

    private fun initMediaProjection() {
        mediaProjectionManager =
            context.getSystemService(Service.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection =
            mediaProjectionManager.getMediaProjection(
                resultCode,
                resultData as Intent
            )
    }

    private fun createMediaFormat(
        bitRate: Int,
        maxFps: Int
    ): MediaFormat {
        val format = MediaFormat()
        format.setString(MediaFormat.KEY_MIME, MIME_TYPE)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        // must be present to configure the encoder, but does not impact the actual frame rate, which is variable
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 60)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(
            MediaFormat.KEY_I_FRAME_INTERVAL,
            DEFAULT_I_FRAME_INTERVAL
        )
        // display the very first frame, and recover from bad quality when no new frames
        format.setLong(
            MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,
            REPEAT_FRAME_DELAY_US.toLong()
        ) // µs
        if (maxFps > 0) {
            // The key existed privately before Android 10:
            // <https://android.googlesource.com/platform/frameworks/base/+/625f0aad9f7a259b6881006ad8710adce57d1384%5E%21/>
            // <https://github.com/Genymobile/scrcpy/issues/488#issuecomment-567321437>
            format.setFloat(
                KEY_MAX_FPS_TO_ENCODER,
                maxFps.toFloat()
            )
        }

        return format
    }


    @RequiresApi(Build.VERSION_CODES.R)
    private fun startRecording() {

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics: WindowMetrics = windowManager.currentWindowMetrics

        val screenWidth = metrics.bounds.width()
        val screenHeight = metrics.bounds.height()
        prepareMediaCodec(screenWidth, screenHeight, context.resources.configuration.densityDpi)
    }

    private fun prepareMediaCodec(screenWidth: Int, screenHeight: Int, screenDensity: Int) {
        Log.d(
            TAG,
            "prepareMediaCodec: width:${screenWidth}___height:${screenHeight}___density:${screenDensity}"
        )
        //初始化MediaFormat
        val mediaFormat = createMediaFormat(6000000, 0)
        //设置宽高
        mediaFormat.setInteger(MediaFormat.KEY_WIDTH, screenWidth)
        mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, screenHeight)

        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
        mediaCodec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            }
            override fun onOutputBufferAvailable(
                p0: MediaCodec,
                outputBufferId: Int,
                info: MediaCodec.BufferInfo
            ) {
                if (!hasMuxerStarted) {
                    startMuxer()
                    return
                }
                val encodedData = getEncodedData(outputBufferId,info)
                muxerWriteData(encodedData, info)
                mediaCodec.releaseOutputBuffer(outputBufferId, false)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            }

        })

        //设置为编码模式
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = mediaCodec.createInputSurface()
        initMuxer()
        mediaCodec.start()
        mediaProjection.createVirtualDisplay(
            "Record", screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface, null, null
        )
    }

    private fun getEncodedData(outputBufferId:Int,info:BufferInfo): ByteBuffer {
        val encodedData: ByteBuffer = mediaCodec.getOutputBuffer(outputBufferId)!!
        encodedData.position(info.offset)
        encodedData.limit(info.offset + info.size)
        val data =  ByteArray(encodedData.remaining())
        return encodedData.get(data)
    }

    private fun startMuxer() {
        // 要在获取第一帧数据后，设置Muxer的track格式，并启动muxer，否则会报错:mediacodec-missing-codec-specific-data
        //https://stackoverflow.com/questions/66529879/mediacodec-missing-codec-specific-data
        encodeVideoTrackIndex = mMediaMuxer.addTrack(
            mediaCodec.outputFormat
        )
        mMediaMuxer.start()
        hasMuxerStarted = true
    }

    private fun muxerWriteData(encodedData: ByteBuffer, info: MediaCodec.BufferInfo) {
        Log.d(TAG, "writing...")
        mMediaMuxer.writeSampleData(encodeVideoTrackIndex, encodedData, info)
    }

    private fun initMuxer() {
        mMediaMuxer = MediaMuxer(saveFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun start() {
        Log.d(TAG, "start")
        startRecording()
    }

    fun stop() {
        Log.d(TAG, "stop")
        hasMuxerStarted = false
        mediaCodec.stop()
        mediaCodec.release()

        mMediaMuxer.stop()
        mMediaMuxer.release()
    }
}