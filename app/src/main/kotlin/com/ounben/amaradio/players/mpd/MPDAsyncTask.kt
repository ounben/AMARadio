package com.ounben.amaradio.players.mpd

import android.text.TextUtils
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.*

open class MPDAsyncTask : Runnable {
    interface ReadStage {
        fun onRead(task: MPDAsyncTask, result: String): Boolean
    }

    interface WriteStage {
        @Throws(IOException::class)
        fun onWrite(task: MPDAsyncTask, bufferedWriter: BufferedWriter): Boolean
    }

    interface FailureCallback {
        fun onFailure(task: MPDAsyncTask)
    }

    private var readStages: LinkedList<ReadStage>? = null
    private var writeStages: LinkedList<WriteStage>? = null
    private var failureCallback: FailureCallback? = null
    private var timeoutMs: Long = 0
    private var mpdServerData: MPDServerData? = null
    private var mpdClient: MPDClient? = null

    internal fun setStages(readStages: Array<ReadStage>, writeStages: Array<WriteStage>, failureCallback: FailureCallback?) {
        this.readStages = LinkedList(listOf(*readStages))
        this.writeStages = LinkedList(listOf(*writeStages))
        this.failureCallback = failureCallback
    }

    fun setTimeout(timeoutMs: Long) {
        this.timeoutMs = timeoutMs
    }

    protected fun fail() {
        failureCallback?.onFailure(this)
    }

    override fun run() {
        try {
            val serverData = mpdServerData ?: return
            if (!TextUtils.isEmpty(serverData.password)) {
                readStages?.addFirst(okReadStage())
                writeStages?.addFirst(loginWriteStage(serverData.password!!))
            }

            val s = Socket()
            s.connect(InetSocketAddress(serverData.hostname, serverData.port), timeoutMs.toInt())
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charset.forName("UTF-8")))
            val writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charset.forName("UTF-8")))

            onConnected(reader, writer)

            reader.close()
            writer.close()
            s.close()
        } catch (ex: IOException) {
            fail()
        }
    }

    @Throws(IOException::class)
    private fun onConnected(reader: BufferedReader, writer: BufferedWriter) {
        val readBuffer = CharBuffer.allocate(1024)
        var c = true
        while (c) {
            readBuffer.clear()
            val readStage = readStages?.poll()
            if (readStage != null) {
                val read = reader.read(readBuffer)
                readBuffer.position(0)
                Log.d(TAG, readBuffer.toString())
                c = readStage.onRead(this, readBuffer.toString())
            } else {
                c = false
            }

            if (c) {
                val writeStage = writeStages?.poll()
                if (writeStage != null) {
                    c = writeStage.onWrite(this, writer)
                    writer.flush()
                } else {
                    c = false
                }
            }
        }
    }

    fun setParams(mpdClient: MPDClient, mpdServerData: MPDServerData) {
        this.mpdClient = mpdClient
        this.mpdServerData = MPDServerData(mpdServerData)
    }

    fun getMpdServerData(): MPDServerData? {
        return mpdServerData
    }

    fun notifyServerUpdated() {
        mpdServerData?.let { mpdClient?.notifyServerUpdate(it) }
    }

    companion object {
        private const val TAG = "MPDAsyncTask"

        @JvmStatic
        internal fun okReadStage(): ReadStage {
            return object : ReadStage {
                override fun onRead(task: MPDAsyncTask, result: String): Boolean {
                    val ok = result.startsWith("OK")
                    if (!ok) {
                        task.fail()
                    }
                    return ok
                }
            }
        }

        @JvmStatic
        internal fun statusWriteStage(): WriteStage {
            return object : WriteStage {
                override fun onWrite(task: MPDAsyncTask, bufferedWriter: BufferedWriter): Boolean {
                    bufferedWriter.write("status\n")
                    return true
                }
            }
        }

        @JvmStatic
        internal fun loginWriteStage(password: String): WriteStage {
            return object : WriteStage {
                override fun onWrite(task: MPDAsyncTask, bufferedWriter: BufferedWriter): Boolean {
                    bufferedWriter.write("password $password\n")
                    return true
                }
            }
        }

        @JvmStatic
        internal fun statusReadStage(c: Boolean): ReadStage {
            return object : ReadStage {
                override fun onRead(task: MPDAsyncTask, result: String): Boolean {
                    task.getMpdServerData()?.updateStatus(result)
                    task.notifyServerUpdated()
                    return c
                }
            }
        }
    }
}
