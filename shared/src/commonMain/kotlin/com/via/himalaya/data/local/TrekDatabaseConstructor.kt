package com.via.himalaya.data.local

import androidx.room.RoomDatabaseConstructor

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object TrekDatabaseConstructor : RoomDatabaseConstructor<TrekDatabase> {

    override fun initialize(): TrekDatabase

}