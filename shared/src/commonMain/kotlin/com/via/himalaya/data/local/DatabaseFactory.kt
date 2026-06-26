package com.via.himalaya.data.local

import androidx.room.RoomDatabase

expect class DatabaseFactory {

    fun create(): RoomDatabase.Builder<TrekDatabase>

}