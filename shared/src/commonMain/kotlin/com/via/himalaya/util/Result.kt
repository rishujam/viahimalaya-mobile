package com.via.himalaya.util

sealed class Result <T>(val data: T? = null, val message: String? = null, val code: Int? = null) {
    class Success<T>(data: T) : Result<T>(data = data)
    class Loading<T> : Result<T>()
    class Error<T>(message: String, errorCode: Int): Result<T>(message = message, code = errorCode)
}