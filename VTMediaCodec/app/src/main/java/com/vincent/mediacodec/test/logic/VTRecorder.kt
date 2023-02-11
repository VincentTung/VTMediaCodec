package com.vincent.mediacodec.test.logic

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
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import android.view.WindowMetrics
import com.vincent.mediacodec.test.util.VTCodecUtil.getEncodedData
import java.io.File
import java.nio.ByteBuffer

/**
 * 录屏类
 * 录制屏幕+编码+存储mp4
 */
class VTRecorder(
    private val context: Context,
    private val resultCode: Int,
    private val resultData: Intent?,
) {

    companion object {
        private const val TAG = "VTRecorder"
        private const val DEFAULT_I_FRAME_INTERVAL = 10 // seconds

        private const val REPEAT_FRAME_DELAY_US = 100000 // repeat after 100ms

        // Keep the values in descending order
        private val MAX_SIZE_FALLBACK = intArrayOf(2560, 1920, 1600, 1280, 1024, 800)

        private const val PACKET_FLAG_CONFIG = 1L shl 63
        private const val PACKET_FLAG_KEY_FRAME = 1L shl 62

        private const val BIT_RATE = 6000000
        private const val FRAME_RATE = 60
        private const val MAX_FPS = 0
        //编码格式
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC

        //保存文件格式为mp4格式
        private const val POSTFIX = ".mp4"
    }
    private var windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var hasMuxerStarted: Boolean = false

    //muxer负责生成mp4
    private lateinit var mediaMuxer: MediaMuxer

    //录制存储文件 格式:/data/user/0/com.vincent.mediacodec.test/files/1676103755796.mp4
    private lateinit var saveFile: File

    //录屏系统类
    private lateinit var mediaProjection: MediaProjection
    private lateinit var mediaProjectionManager: MediaProjectionManager

    //编码器
    private lateinit var mediaCodec: MediaCodec
    private var encodeVideoTrackIndex: Int = -1


    init {
        initFile()
        initMediaProjection()
        initMuxer()

    }

    private fun initFile() {
        saveFile = File("${this.context.filesDir.path}/${generateFileName()}${POSTFIX}")
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
        maxFps: Int,
    ): MediaFormat {
        val format = MediaFormat()
        format.setString(MediaFormat.KEY_MIME, MIME_TYPE)
        //比特率
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        //帧率
        // must be present to configure the encoder, but does not impact the actual frame rate, which is variable
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        //颜色空间
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        //I帧间隔时间
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
                MediaFormat.KEY_MAX_FPS_TO_ENCODER,
                maxFps.toFloat()
            )
        }

        return format
    }

    private fun initMediaCodec(screenWidth: Int, screenHeight: Int, screenDensity: Int) {
        Log.d(
            TAG,
            "initMediaCodec: width:${screenWidth}___height:${screenHeight}___density:${screenDensity}"
        )
        //初始化MediaFormat
        val mediaFormat = createMediaFormat(BIT_RATE, MAX_FPS)
        //设置MediaFormat的宽高
        mediaFormat.setInteger(MediaFormat.KEY_WIDTH, screenWidth)
        mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, screenHeight)
        //创建codec
        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
        //设置codec的回调
        mediaCodec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                outputBufferId: Int,
                info: MediaCodec.BufferInfo,
            ) {
                Log.d(TAG, "onOutputBufferAvailable: $outputBufferId ____ $info")
                if (!hasMuxerStarted) {
                    startMuxer()
                    return
                }
                val encodedData = getEncodedData(mediaCodec,outputBufferId, info)
                muxerWriteData(encodedData, info)
                mediaCodec.releaseOutputBuffer(outputBufferId, false)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.d(TAG, "onError: ${e.errorCode}___${e.diagnosticInfo}")
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                Log.d(TAG, "onOutputFormatChanged: $format")
            }

        })

        //设置codec的模式为编码模式
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        //提前创建InputSurface
        val inputSurface: Surface =  mediaCodec.createInputSurface()
        //启动编码器
        mediaCodec.start()
        //开始录屏
        mediaProjection.createVirtualDisplay(
            "Record", screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface, null, null
        )
    }



    /**
     * 初始化muxer
     */
    private fun initMuxer() {
        mediaMuxer = MediaMuxer(saveFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    /**
     * 启动muxer
     */
    private fun startMuxer() {
        // 要在获取第一帧数据后，设置Muxer的track格式，并启动muxer，否则会报错:mediacodec-missing-codec-specific-data
        //https://stackoverflow.com/questions/66529879/mediacodec-missing-codec-specific-data
        encodeVideoTrackIndex = mediaMuxer.addTrack(
            mediaCodec.outputFormat
        )
        mediaMuxer.start()
        hasMuxerStarted = true
    }

    /**
     * muxer向文件写入数据
     */
    private fun muxerWriteData(encodedData: ByteBuffer, info: MediaCodec.BufferInfo) {
        Log.d(TAG, "muxerWriteData...")
        mediaMuxer.writeSampleData(encodeVideoTrackIndex, encodedData, info)
    }

    /**
     *  启动录制
     */
    fun start() {
        Log.d(TAG, "start")
        val screenWidth: Int
        val screenHeight: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics: WindowMetrics =
                windowManager.currentWindowMetrics
            screenWidth = metrics.bounds.width()
            screenHeight = metrics.bounds.height()
        } else {
            val display: Display = windowManager.defaultDisplay
            screenWidth = display.width
            screenHeight = display.height

        }
        initMediaCodec(screenWidth, screenHeight, context.resources.configuration.densityDpi)
    }

    /**
     * 停止录制
     */
    fun stop() {
        Log.d(TAG, "stop")
        hasMuxerStarted = false
        mediaCodec.stop()
        mediaCodec.release()

        mediaMuxer.stop()
        mediaMuxer.release()
    }

    /**
     * 生成文件名
     */
    private fun generateFileName(): String {
        return System.currentTimeMillis().toString()
    }

}