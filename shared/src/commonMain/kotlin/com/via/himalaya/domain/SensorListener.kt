package com.via.himalaya.domain

import com.via.himalaya.data.models.SensorData

interface SensorListener {

    fun getSensorData(): SensorData

    fun startListening()

    fun stopListening()

}