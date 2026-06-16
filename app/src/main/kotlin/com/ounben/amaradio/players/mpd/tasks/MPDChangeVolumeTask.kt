package com.ounben.amaradio.players.mpd.tasks

import android.annotation.SuppressLint
import com.ounben.amaradio.players.mpd.MPDAsyncTask
import com.ounben.amaradio.players.mpd.MPDServerData
import java.io.BufferedWriter

class MPDChangeVolumeTask(deltaVolume: Int, failureCallback: FailureCallback?, server: MPDServerData) : MPDAsyncTask() {
    init {
        setStages(
            arrayOf(
                okReadStage(),
                statusReadStage(true),
                object : ReadStage {
                    override fun onRead(task: MPDAsyncTask, result: String): Boolean {
                        task.getMpdServerData()?.updateStatus(result)
                        task.notifyServerUpdated()
                        return false
                    }
                }
            ),
            arrayOf(
                statusWriteStage(),
                object : WriteStage {
                    @SuppressLint("DefaultLocale")
                    override fun onWrite(task: MPDAsyncTask, bufferedWriter: BufferedWriter): Boolean {
                        val currentVolume = task.getMpdServerData()?.volume ?: 0
                        val newVolume = (currentVolume + deltaVolume).coerceIn(0, 100)
                        bufferedWriter.write(String.format("command_list_begin\nsetvol %d\nstatus\ncommand_list_end\n", newVolume))
                        return true
                    }
                }
            ),
            failureCallback
        )
    }
}
