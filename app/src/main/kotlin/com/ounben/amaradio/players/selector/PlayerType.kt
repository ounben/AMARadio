package com.ounben.amaradio.players.selector

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class PlayerType(val value: Int) : Parcelable {
    MPD_SERVER(0),
    AMARadio(1),
    EXTERNAL(2),
    CAST(3)
}
