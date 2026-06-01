package com.via.himalaya.data.repository

import com.via.himalaya.data.models.Trek
import kotlinx.coroutines.delay

class TrekRepositoryImpl : TrekRepository {

    override suspend fun getTreks(): List<Trek> {
        delay(1500L)
        return listOf(
            Trek(
                id = "1",
                name = "Triund Trek",
                location = "Dharamshala, HP",
                distance = "9 km",
                elevation = "850 m"
            ),
            Trek(
                id = "2",
                name = "Kheerganga",
                location = "Parvati Valley, HP",
                distance = "12 km",
                elevation = "1,420 m"
            ),
            Trek(
                id = "3",
                name = "Hampta Pass",
                location = "Manali, HP",
                distance = "26 km",
                elevation = "2,460 m"
            )
        )
    }
}