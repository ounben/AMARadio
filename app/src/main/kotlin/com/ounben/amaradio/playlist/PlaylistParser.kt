package com.ounben.amaradio.playlist

import java.net.URL
import java.util.Locale

object PlaylistParser {
    fun isPlaylist(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT).split("?")[0]
        return lower.endsWith(".m3u") || lower.endsWith(".pls")
    }

    fun parse(url: String, content: String): String? {
        val lower = url.lowercase(Locale.ROOT).split("?")[0]
        return when {
            lower.endsWith(".m3u") -> {
                try {
                    val playlist = PlaylistM3U(URL(url), content)
                    playlist.getEntries().firstOrNull { it.content?.startsWith("http") == true }?.content
                } catch (e: Exception) {
                    null
                }
            }
            lower.endsWith(".pls") -> {
                parsePls(content)
            }
            else -> url
        }
    }

    private fun parsePls(content: String): String? {
        // Simple PLS parsing: find File1=...
        val regex = Regex("File\\d+=(.*)", RegexOption.IGNORE_CASE)
        val match = regex.find(content)
        return match?.groupValues?.get(1)?.trim()
    }
}
