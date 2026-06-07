package net.ounben.AMARadio.recording

import java.io.FileOutputStream

class RunningRecordingInfo {
    var recordable: Recordable? = null
    var title: String? = null
    var fileName: String? = null
    var outputStream: FileOutputStream? = null
    var bytesWritten: Long = 0
}
