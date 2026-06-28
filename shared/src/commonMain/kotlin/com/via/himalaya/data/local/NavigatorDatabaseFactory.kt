package com.via.himalaya.data.local

import androidx.room.RoomDatabase

expect class NavigatorDatabaseFactory {

    fun create(): RoomDatabase.Builder<NavigatorDatabase>

}