package com.via.himalaya.presentation

import com.via.himalaya.data.models.SensorData

sealed class TrekScreenUIEvent {

    data class StartTrek(val name: String) : TrekScreenUIEvent()

    data object StopTrek : TrekScreenUIEvent()

    data object PauseTrek : TrekScreenUIEvent()

    data class LocationUpdate(val sensorData: SensorData) : TrekScreenUIEvent()

}