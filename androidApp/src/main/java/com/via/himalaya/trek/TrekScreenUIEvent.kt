package com.via.himalaya.trek

sealed class TrekScreenUIEvent {

    data object StartTrek : TrekScreenUIEvent()

    data object StopTrek : TrekScreenUIEvent()

}