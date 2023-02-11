package com.vincent.mediacodec.test.util

import android.media.MediaCodec
import java.nio.ByteBuffer

object VTCodecUtil {
    /**
     * 获取编码后的视频数据buffer
     */
    fun getEncodedData(
        mediaCodec: MediaCodec,
        outputBufferId: Int,
        info: MediaCodec.BufferInfo,
    ): ByteBuffer {
        val encodedData: ByteBuffer = mediaCodec.getOutputBuffer(outputBufferId)!!
        encodedData.position(info.offset)
        encodedData.limit(info.offset + info.size)
        val data = ByteArray(encodedData.remaining())
        return encodedData.get(data)
    }

}