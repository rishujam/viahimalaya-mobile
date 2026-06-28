package com.via.himalaya.domain

import com.via.himalaya.domain.model.SensorData

interface SensorListener {

    fun getSensorData(): SensorData

    fun startListening()

    fun stopListening()

}