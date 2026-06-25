package com.ounben.amaradio.players

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class PlayState : Parcelable {
    Idle,
    PrePlaying,
    Playing,
    Paused,
    Error
}
