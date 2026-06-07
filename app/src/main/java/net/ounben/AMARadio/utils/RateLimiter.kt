package net.ounben.AMARadio.utils

import kotlin.math.abs

class RateLimiter(private val limit: Int, private val fullReplenishTime: Long) {
    private var available: Double = limit.toDouble()
    private var lastTime: Long = System.currentTimeMillis()

    fun allowed(): Boolean {
        val now = System.currentTimeMillis()
        available += abs(now - lastTime).toDouble() * (1.0 / fullReplenishTime) * limit
        if (available > limit) {
            available = limit.toDouble()
        }

        return if (available < 1.0) {
            false
        } else {
            available--
            lastTime = now
            true
        }
    }
}
