package net.ounben.AMARadio.recording

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.preference.PreferenceManager
import net.ounben.AMARadio.BuildConfig
import net.ounben.AMARadio.R
import net.ounben.AMARadio.Utils
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class RecordingsManager {
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormatter = SimpleDateFormat("HH-mm", Locale.US)

    inner class RecordingsObservable : Observable() {
        override fun hasChanged(): Boolean {
            return true
        }
    }

    val savedRecordingsObservable = RecordingsObservable()

    private inner class RunningRecordableListener(private val runningRecordingInfo: RunningRecordingInfo) : RecordableListener {
        private var ended = false

        override fun onBytesAvailable(buffer: ByteArray, offset: Int, length: Int) {
            try {
                runningRecordingInfo.outputStream?.write(buffer, offset, length)
                runningRecordingInfo.bytesWritten += length.toLong()
            } catch (e: IOException) {
                e.printStackTrace()
                runningRecordingInfo.recordable?.stopRecording()
            }
        }

        override fun onRecordingEnded() {
            if (ended) {
                return
            }
            ended = true
            try {
                runningRecordingInfo.outputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            stopRecording(runningRecordingInfo.recordable!!)
        }
    }

    private val runningRecordings = HashMap<Recordable, RunningRecordingInfo>()
    private val savedRecordings = ArrayList<DataRecording>()

    fun record(context: Context, recordable: Recordable) {
        if (!recordable.canRecord()) {
            return
        }
        if (!runningRecordings.containsKey(recordable)) {
            val info = RunningRecordingInfo()
            info.recordable = recordable
            val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            val fileNameFormat = prefs.getString("record_name_formatting", context.getString(R.string.settings_record_name_formatting_default))
            val formattingArgs = HashMap(recordable.getRecordNameFormattingArgs())
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = System.currentTimeMillis()
            val currentTime = calendar.time
            val dateStr = dateFormatter.format(currentTime)
            val timeStr = timeFormatter.format(currentTime)
            formattingArgs["date"] = dateStr
            formattingArgs["time"] = timeStr
            val recordNum = prefs.getInt("record_num", 1)
            formattingArgs["index"] = recordNum.toString()
            val recordTitle = Utils.formatStringWithNamedArgs(fileNameFormat!!, formattingArgs)
            info.title = recordTitle
            info.fileName = String.format("%s.%s", recordTitle, recordable.getExtension())
            val filePath = getRecordDir() + "/" + info.fileName
            try {
                info.outputStream = FileOutputStream(filePath)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                return
            }
            recordable.startRecording(RunningRecordableListener(info))
            runningRecordings[recordable] = info
            prefs.edit().putInt("record_num", recordNum + 1).apply()
        }
    }

    fun stopRecording(recordable: Recordable) {
        recordable.stopRecording()
        runningRecordings.remove(recordable)
        updateRecordingsList()
    }

    fun getRecordingInfo(recordable: Recordable): RunningRecordingInfo? {
        return runningRecordings[recordable]
    }

    fun getRunningRecordings(): Map<Recordable, RunningRecordingInfo> {
        return Collections.unmodifiableMap(runningRecordings)
    }

    fun getSavedRecordings(): List<DataRecording> {
        return ArrayList(savedRecordings)
    }

    fun updateRecordingsList() {
        val path = getRecordDir()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Updating recordings from $path")
        }
        savedRecordings.clear()
        val folder = File(path)
        val files = folder.listFiles()
        if (files != null) {
            for (f in files) {
                val dr = DataRecording()
                dr.Name = f.name
                dr.Time = Date(f.lastModified())
                savedRecordings.add(dr)
            }
            savedRecordings.sortWith { o1, o2 -> o2.Time.compareTo(o1.Time) }
        } else {
            Log.e(TAG, "Could not enumerate files in recordings directory")
        }
        savedRecordingsObservable.notifyObservers()
    }

    companion object {
        private const val TAG = "Recordings"

        @JvmStatic
        fun getRecordDir(): String {
            val pathRecordings = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).toString() + "/Recordings"
            val folder = File(pathRecordings)
            if (!folder.exists()) {
                if (!folder.mkdirs()) {
                    Log.e(TAG, "could not create dir:$pathRecordings")
                }
            }
            return pathRecordings
        }
    }
}
