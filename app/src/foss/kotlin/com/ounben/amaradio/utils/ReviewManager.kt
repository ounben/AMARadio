package com.ounben.amaradio.utils

import android.app.Activity
import android.content.Context

/**
 * Dummy ReviewManager for FOSS flavor.
 * Does nothing as F-Droid doesn't allow Google Play Review API.
 */
class ReviewManager(context: Context) {
    fun isReviewCompleted(): Boolean = true
    fun incrementActionCount() {}
    fun maybeRequestReview(activity: Activity) {}
    fun launchReviewFlow(activity: Activity) {}
    fun markReviewCompleted() {}
}
