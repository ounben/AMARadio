package com.ounben.amaradio.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils

class ReviewManager(private val context: Context) {

    private val sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val manager = ReviewManagerFactory.create(context)

    companion object {
        private const val PREF_ACTION_COUNT = "review_action_count"
        private const val PREF_LAST_REVIEW_TIME = "review_last_time"
        private const val PREF_REVIEW_COMPLETED = "review_completed"
        private const val THRESHOLD = 100 // Request review after 100 significant actions
        private const val TAG = "ReviewManager"
    }

    /**
     * Returns true if the user has completed the review flow at least once.
     */
    fun isReviewCompleted(): Boolean {
        return sharedPref.getBoolean(PREF_REVIEW_COMPLETED, false)
    }

    /**
     * Increment the action count. Call this when the user performs a positive action,
     * like successfully playing a station or adding a favourite.
     */
    fun incrementActionCount() {
        if (isReviewCompleted()) return
        val currentCount = sharedPref.getInt(PREF_ACTION_COUNT, 0)
        sharedPref.edit().putInt(PREF_ACTION_COUNT, currentCount + 1).apply()
        Log.d(TAG, "Action count incremented to ${currentCount + 1}")
    }

    /**
     * Potentially shows the review dialog if criteria are met.
     */
    fun maybeRequestReview(activity: Activity) {
        if (isReviewCompleted()) return
        val count = sharedPref.getInt(PREF_ACTION_COUNT, 0)
        val lastTime = sharedPref.getLong(PREF_LAST_REVIEW_TIME, 0L)
        val currentTime = System.currentTimeMillis()

        // Only ask if threshold reached and at least 7 days since last ask attempt
        if (count >= THRESHOLD && (currentTime - lastTime > 7 * 24 * 60 * 60 * 1000)) {
            launchReviewFlow(activity)
        }
    }

    fun launchReviewFlow(activity: Activity) {
        Log.d(TAG, "Requesting review flow...")
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener { _ ->
                    Log.d(TAG, "Review flow finished")
                    sharedPref.edit()
                        .putLong(PREF_LAST_REVIEW_TIME, System.currentTimeMillis())
                        .putInt(PREF_ACTION_COUNT, 0)
                        .apply()
                }
            } else {
                Log.e(TAG, "Review request failed, falling back to Play Store link", task.exception)
                openPlayStore(activity)
            }
        }
    }

    /**
     * Mark review as completed manually. Call this for example if the user
     * explicitly clicks a "Don't ask again" button or we are sure they reviewed.
     * Note: The Google API doesn't tell us if they actually reviewed.
     */
    fun markReviewCompleted() {
        sharedPref.edit().putBoolean(PREF_REVIEW_COMPLETED, true).apply()
    }

    private fun openPlayStore(activity: Activity) {
        val packageName = activity.packageName
        try {
            activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, 
                android.net.Uri.parse("market://details?id=$packageName")))
        } catch (e: Exception) {
            try {
                activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, 
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open Play Store or Browser", e2)
                Utils.showModernToast(activity, R.string.error_no_browser)
            }
        }
    }
}
