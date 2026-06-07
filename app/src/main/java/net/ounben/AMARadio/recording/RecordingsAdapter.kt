package net.ounben.AMARadio.recording

import android.app.ProgressDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import net.ounben.AMARadio.BuildConfig
import net.ounben.AMARadio.R
import java.io.File

class RecordingsAdapter(private val context: Context) : RecyclerView.Adapter<RecordingsAdapter.RecordingItemViewHolder>() {
    inner class RecordingItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val viewRoot: ViewGroup = itemView as ViewGroup
        val textViewTitle: TextView = itemView.findViewById(R.id.textViewTitle)
        val textViewTime: TextView = itemView.findViewById(R.id.textViewTime)
    }

    private var recordings: List<DataRecording>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.list_item_recording, parent, false)
        return RecordingItemViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: RecordingItemViewHolder, position: Int) {
        val recording = recordings!![position]
        holder.textViewTitle.text = recording.Name
        holder.viewRoot.setOnClickListener { openRecording(recording) }
    }

    fun setRecordings(recordings: List<DataRecording>?) {
        if (this.recordings != null && recordings != null && recordings.size == this.recordings!!.size) {
            var same = true
            for (i in recordings.indices) {
                if (recordings[i] != this.recordings!![i]) {
                    same = false
                    break
                }
            }
            if (same) return
        }
        this.recordings = recordings
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = recordings?.size ?: 0

    private fun openRecording(theData: DataRecording) {
        @Suppress("DEPRECATION")
        val dialog = ProgressDialog.show(context, "Loading...", "Please wait...", true, false)
        val path = RecordingsManager.getRecordDir() + "/" + theData.Name
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "play: $path")
        }
        val i = Intent(android.content.Intent.ACTION_VIEW)
        val file = File(path)
        val fileUri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        i.setDataAndType(fileUri, "audio/*")
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            val clip = ClipData.newUri(context.contentResolver, "Record", fileUri)
            i.clipData = clip
        }
        context.startActivity(i)
        dialog.dismiss()
    }

    companion object {
        private const val TAG = "RecordingsAdapter"
    }
}
