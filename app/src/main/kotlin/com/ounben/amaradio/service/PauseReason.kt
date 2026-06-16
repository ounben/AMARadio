package com.ounben.amaradio.service

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class PauseReason : Parcelable {
    NONE,
    BECAME_NOISY,
    FOCUS_LOSS,
    FOCUS_LOSS_TRANSIENT,
    METERED_CONNECTION,
    USER
}
