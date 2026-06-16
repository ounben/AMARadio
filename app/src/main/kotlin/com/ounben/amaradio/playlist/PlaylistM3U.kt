package com.ounben.amaradio.playlist

import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.net.MalformedURLException
import java.net.URL
import java.util.*

class PlaylistM3U(private val path: URL, private val fullText: String) {
    var extended = false
    private val entries = ArrayList<PlaylistM3UEntry>()
    private var header: String? = null

    init {
        decode()
    }

    private fun decode() {
        val lines = getLines()
        for (line in lines) {
            try {
                decodeLine(line)
            } catch (e: MalformedURLException) {
                // Ignore
            }
        }
    }

    @Throws(MalformedURLException::class)
    private fun resolveToBase(file: String): URL {
        val oldPath = path.path
        val filePath = getBasePath(oldPath) + "/" + file
        return URL(path.protocol, path.host, path.port, filePath)
    }

    @Throws(MalformedURLException::class)
    private fun decodeLine(line: String) {
        if (line.startsWith(EXTENDED)) {
            extended = true
        } else if (line.startsWith(COMMENTMARKER)) {
            if (extended) {
                header = line
            }
        } else {
            val lineLower = line.lowercase(Locale.ROOT)
            if (lineLower.startsWith("http://") || lineLower.startsWith("https://")) {
                entries.add(PlaylistM3UEntry(header, line))
            } else {
                entries.add(PlaylistM3UEntry(header, resolveToBase(line).toString()))
            }
            header = null
        }
    }

    private fun getBasePath(fullPath: String): String {
        val sep = fullPath.lastIndexOf('/')
        return if (sep != -1) fullPath.substring(0, sep) else ""
    }

    private fun getLines(): Array<String> {
        val r = StringReader(fullText)
        val br = BufferedReader(r)
        val list = ArrayList<String>()
        try {
            var line: String?
            while (br.readLine().also { line = it } != null) {
                list.add(line!!)
            }
        } catch (e: IOException) {
            // Ignore
        }
        return list.toTypedArray()
    }

    fun getEntries(): Array<PlaylistM3UEntry> {
        return entries.toTypedArray()
    }

    companion object {
        const val COMMENTMARKER = "#"
        const val EXTENDED = "#EXTM3U"
    }
}
