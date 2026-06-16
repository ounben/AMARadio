package com.ounben.amaradio.players.mpd.tasks

import com.ounben.amaradio.players.mpd.MPDAsyncTask
import java.io.BufferedWriter

class MPDStopTask(failureCallback: FailureCallback?) : MPDAsyncTask() {
    init {
        setStages(
            arrayOf(
                okReadStage(),
                statusReadStage(false)
            ),
            arrayOf(
                object : WriteStage {
                    override fun onWrite(task: MPDAsyncTask, bufferedWriter: BufferedWriter): Boolean {
                        bufferedWriter.write("command_list_begin\nstop\nstatus\ncommand_list_end\n")
                        return true
                    }
                }
            ),
            failureCallback
        )
    }
}
