package com.vincent.mediacodec.test.logic

import android.media.MediaCodec
import android.media.MediaCodec.CodecException
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.io.IOException
import java.lang.Thread.sleep
import java.nio.ByteBuffer


/**
 * 进行解码视频播放
 */
class VTDecoder(private val filePath: String, private val outputSurface: Surface) {
    companion object {
        private const val TAG = "VTDecoder"
    }

    private var startMs: Long = -1
    //提取文件的MediaFormat等信息
    private lateinit var mediaExtractor: MediaExtractor
    private lateinit var mediaCodec: MediaCodec

    /**
     * 开始解码
     */
    fun start() {
        play(outputSurface)
    }

    /**
     *停止解码

     */
    fun stop() {
        mediaCodec.stop()
        mediaCodec.release()
        mediaExtractor.release()
    }

    private fun play(surface: Surface) {
        mediaExtractor = MediaExtractor()
        try {
            mediaExtractor.setDataSource(filePath)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val trackCount: Int = mediaExtractor.trackCount
        //从媒体提取器中拿到了 MIME 以及MediaFormat   通过MIME 创建的硬件解码器   通过MediaFormat配置的硬件解码器
        for (i in 0 until trackCount) {
            val trackFormat: MediaFormat = mediaExtractor.getTrackFormat(i)
            Log.d(TAG, "trackFormat is $trackFormat")
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            Log.d(TAG, "mime is $mime")
            if (mime!!.startsWith("video/")) {
                mediaExtractor.selectTrack(i)
                try {
                    mediaCodec = MediaCodec.createDecoderByType(mime)
                    //配置解码之后的数据输出到surface上 
                    mediaCodec.configure(trackFormat, surface, null, 0)
                    mediaCodec.setCallback(object : MediaCodec.Callback() {
                        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                            val inputBuffer: ByteBuffer = codec.getInputBuffer(index) ?: return
                            val sampleSize: Int = mediaExtractor.readSampleData(inputBuffer, 0)
                            if (sampleSize > 0) {
                                codec.queueInputBuffer(index,
                                    0,
                                    sampleSize,
                                    mediaExtractor.sampleTime,
                                    0)
                                //下一帧数据
                                mediaExtractor.advance() 
                            } else {
                                codec.queueInputBuffer(index,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                        }

                        override fun onOutputBufferAvailable(
                            codec: MediaCodec,
                            index: Int,
                            info: MediaCodec.BufferInfo,
                        ) {
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                Log.d(TAG, "outputBuffer BUFFER_FLAG_END_OF_STREAM")
                                codec.stop()
                                codec.release() // 释放组件
                                mediaExtractor.release()
                                return
                            }
                            if (index > 0) {
                                if (startMs === -1L) {
                                    startMs = System.currentTimeMillis()
                                }
                                sleepRender(info, startMs)
                            }
                            codec.releaseOutputBuffer(index, true) //释放缓冲区，并交给Surface 进行播放
                        }

                        override fun onError(codec: MediaCodec, e: CodecException) {}
                        override fun onOutputFormatChanged(
                            codec: MediaCodec,
                            format: MediaFormat,
                        ) {
                        }
                    })
                    //调用Start 如果没有异常信息，表示成功构建组件
                    mediaCodec.start()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    /*
    *  数据的时间戳对齐
    */
    private fun sleepRender(audioBufferInfo: MediaCodec.BufferInfo, startMs: Long) {
        // 这里的时间是 毫秒  presentationTimeUs 的时间是累加的 以微秒进行一帧一帧的累加
        val timeDifference =
            audioBufferInfo.presentationTimeUs / 1000 - (System.currentTimeMillis() - startMs)
        if (timeDifference > 0) {
            try {
                sleep(timeDifference)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

    }

}