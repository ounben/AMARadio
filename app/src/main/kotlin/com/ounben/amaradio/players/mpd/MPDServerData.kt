package com.ounben.amaradio.players.mpd

import java.util.HashMap

class MPDServerData {
    enum class Status {
        Idle,
        Paused,
        Playing,
    }

    // Persistent data
    var id: Int = -1
    var name: String = ""
    var hostname: String = ""
    var port: Int = 0
    var password: String? = null

    // Runtime status
    var isReachable: Boolean = false
    var status: Status = Status.Idle
    var volume: Int = 0
    var connected: Boolean = false

    constructor(name: String, hostname: String, port: Int, password: String?) {
        this.name = name
        this.hostname = hostname
        this.port = port
        this.password = password
    }

    constructor(other: MPDServerData) {
        this.id = other.id
        this.name = other.name
        this.hostname = other.hostname
        this.password = other.password
        this.port = other.port
        this.isReachable = other.isReachable
        this.status = other.status
        this.volume = other.volume
        this.connected = other.connected
    }

    fun updateStatus(str: String) {
        val statusMap: MutableMap<String, String> = HashMap()
        val lines = str.split("\\R".toRegex()).toTypedArray()
        for (line in lines) {
            val keyAndValue = line.split(": ".toRegex(), limit = 2).toTypedArray()
            if (keyAndValue.size == 2) {
                statusMap[keyAndValue[0]] = keyAndValue[1]
            }
        }

        volume = statusMap["volume"]?.toIntOrNull() ?: 0

        statusMap["state"]?.let { stateStr ->
            status = when (stateStr) {
                "stop" -> Status.Idle
                "pause" -> Status.Paused
                "play" -> Status.Playing
                else -> status
            }
        }

        connected = true
    }

    fun contentEquals(o: MPDServerData?): Boolean {
        if (o == null) return false
        if (id != o.id) return false
        if (port != o.port) return false
        if (isReachable != o.isReachable) return false
        if (volume != o.volume) return false
        if (connected != o.connected) return false
        if (password != o.password) return false
        if (name != o.name) return false
        if (hostname != o.hostname) return false
        return status == o.status
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as MPDServerData
        return id == that.id
    }

    override fun hashCode(): Int {
        return id
    }
}
