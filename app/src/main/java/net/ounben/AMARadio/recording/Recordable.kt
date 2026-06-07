package net.ounben.AMARadio.recording

interface Recordable {
    fun canRecord(): Boolean
    fun startRecording(recordableListener: RecordableListener)
    fun stopRecording()
    fun isRecording(): Boolean
    fun getRecordNameFormattingArgs(): Map<String, String>?
    fun getExtension(): String
}
