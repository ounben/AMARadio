package net.ounben.AMARadio.recording

interface RecordableListener {
    fun onBytesAvailable(buffer: ByteArray, offset: Int, length: Int)
    fun onRecordingEnded()
}
