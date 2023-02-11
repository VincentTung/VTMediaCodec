package com.vincent.mediacodec.test.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.Toast
import com.vincent.mediacodec.test.R
import com.vincent.mediacodec.test.logic.VTDecoder

class PlayActivity : BaseActivity() , SurfaceHolder.Callback{

    companion object{

        @JvmStatic
        fun start(context: Context,filePath:String) {
            val starter = Intent(context, PlayActivity::class.java)
                .putExtra("filePath",filePath)
            context.startActivity(starter)
        }
    }

    private var filePath: String? = null
    private var decoder: VTDecoder? = null
    private lateinit var surfaceView:SurfaceView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filePath = intent.getStringExtra("filePath")
        if(filePath.isNullOrEmpty()){
            Toast.makeText(this,"路径不对",Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContentView(R.layout.activity_play)
        findViewById<Button>(R.id.btn_close).setOnClickListener{
            finish()
        }
        initSurfaceView()
    }

    private fun initSurfaceView() {
        surfaceView = findViewById(R.id.surface_view)
        surfaceView.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceView.holder.addCallback(this)
    }
    private fun startDecode(filePath: String,surface: Surface) {
        if (decoder == null) {
            filePath.let {
                decoder = VTDecoder(it, surface).apply { start() }
            }

        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {

    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        filePath?.let { startDecode(it,holder.surface) }
    }


    override fun surfaceDestroyed(holder: SurfaceHolder) {
        decoder?.stop()
    }

}