package com.ounben.amaradio.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.ounben.amaradio.R
import com.ounben.amaradio.Utils

/**
 * ReviewManager for FOSS flavor.
 * Opens GitHub instead of Google Play.
 */
class ReviewManager(context: Context) {
    
    companion object {
        private const val TAG = "ReviewManager"
        private const val GITHUB_URL = "https://github.com/ounben/AMARadio"
    }

    fun getLabelRes(): Int = R.string.settings_rate_app_github
    fun getSummaryRes(): Int = R.string.settings_rate_app_github_summary

    fun isReviewCompleted(): Boolean = false
    
    fun incrementActionCount() {
        // We don't really track actions for FOSS, or we could if we want automatic prompts
    }
    
    fun maybeRequestReview(activity: Activity) {
        // We skip automatic prompts for GitHub stars in FOSS to remain non-intrusive
    }
    
    fun launchReviewFlow(activity: Activity) {
        openGitHub(activity)
    }
    
    fun markReviewCompleted() {
        // No-op or we could use SharedPreferences if we had automatic prompts
    }

    private fun openGitHub(activity: Activity) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open GitHub in browser", e)
            Utils.showModernToast(activity, R.string.error_no_browser)
        }
    }
}
