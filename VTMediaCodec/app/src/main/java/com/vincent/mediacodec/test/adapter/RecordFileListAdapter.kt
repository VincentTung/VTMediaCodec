package com.vincent.mediacodec.test.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vincent.mediacodec.test.R
import com.vincent.mediacodec.test.data.RecordFile
import java.io.File
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.*

class RecordFileListAdapter(private val fileList: List<RecordFile>) :
    RecyclerView.Adapter<RecordFileListAdapter.ItemViewHolder>() {

    private val dateFormat:SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private var onItemClickListener:OnItemClickListener? = null
    fun setOnItemClickListener(listener: OnItemClickListener){
        this.onItemClickListener = listener
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val item = layoutInflater.inflate(R.layout.item_view_recod_file, parent, false)

        return ItemViewHolder(item)
    }


    override fun getItemViewType(position: Int): Int {

        return super.getItemViewType(position)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.itemView.setOnClickListener{
            onItemClickListener?.onItemClick(position)
        }
        val recordFile = fileList[position]
        holder.run {
            name.text = File(recordFile.filePath).name
            duration.text = getDurationString(recordFile.duration)
            createTime.text = getCreateTimeString(recordFile.createTime)
        }
    }

    private fun getCreateTimeString(createTime: Long): CharSequence? {
        return dateFormat.format(Date(createTime))
    }

    private fun getDurationString(duration: Long): CharSequence? {
        return duration.toString()
    }

    override fun getItemCount(): Int {
        return fileList.size
    }

    class ItemViewHolder(val itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name = itemView.findViewById<TextView>(R.id.tv_name)
        val duration = itemView.findViewById<TextView>(R.id.tv_duration)
        val createTime = itemView.findViewById<TextView>(R.id.tv_create_time)
        init {

        }
    }

    public interface OnItemClickListener {
        fun  onItemClick(position: Int)

    }
}