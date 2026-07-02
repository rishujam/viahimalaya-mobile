package com.via.himalaya.domain

interface Tracker {

    fun track(event: String, params: Map<String, String>)

}