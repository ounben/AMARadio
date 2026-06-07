package net.ounben.AMARadio.players

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class PlayState : Parcelable {
    Idle,
    PrePlaying,
    Playing,
    Paused
}
