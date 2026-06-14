package net.ounben.AMARadio.players.mpd.tasks

import net.ounben.AMARadio.players.mpd.MPDAsyncTask
import java.io.BufferedWriter

class MPDResumeTask(failureCallback: FailureCallback?) : MPDAsyncTask() {
    init {
        setStages(
            arrayOf(
                okReadStage(),
                statusReadStage(false)
            ),
            arrayOf(
                object : WriteStage {
                    override fun onWrite(task: MPDAsyncTask, bufferedWriter: BufferedWriter): Boolean {
                        bufferedWriter.write("command_list_begin\npause 0\nstatus\ncommand_list_end\n")
                        return true
                    }
                }
            ),
            failureCallback
        )
    }
}
