package net.ounben.AMARadio.players.mpd.tasks

import net.ounben.AMARadio.players.mpd.MPDAsyncTask

class MPDPlayTask(url: String, failureCallback: FailureCallback?) : MPDAsyncTask() {
    private var songId = -1

    init {
        setStages(
            arrayOf(
                okReadStage(),
                object : ReadStage {
                    override fun onRead(task: MPDAsyncTask, result: String): Boolean {
                        if (result.startsWith("Id:")) {
                            val idStr = result.substring(3, result.indexOf("\n")).trim()
                            songId = idStr.toIntOrNull() ?: -1
                            return true
                        }
                        return true
                    }
                },
                statusReadStage(false)
            ),
            arrayOf(
                object : WriteStage {
                    override fun onWrite(task: MPDAsyncTask, bufferedWriter: java.io.BufferedWriter): Boolean {
                        bufferedWriter.write("addid $url\n")
                        return true
                    }
                },
                object : WriteStage {
                    override fun onWrite(task: MPDAsyncTask, bufferedWriter: java.io.BufferedWriter): Boolean {
                        bufferedWriter.write("command_list_begin\nplayid $songId\nstatus\ncommand_list_end\n")
                        return true
                    }
                }
            ),
            failureCallback
        )
    }
}
