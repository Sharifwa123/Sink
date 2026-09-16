package com.sharif.sink.logging

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * Structured logger for the whole app. Deliberately has no overload that
 * accepts a "message body" or "plaintext" parameter — callers pass
 * identifiers and enum states, never message content, private keys,
 * tokens, or SMS bodies. Debug-level logs compile out of release builds
 * (see [BuildConfig.DEBUG_LOGGING]).
 */
interface SinkLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

@Singleton
class AndroidSinkLogger @Inject constructor() : SinkLogger {
    override fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG_LOGGING) Log.d(prefixed(tag), message)
    }

    override fun i(tag: String, message: String) {
        Log.i(prefixed(tag), message)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        Log.w(prefixed(tag), message, throwable)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(prefixed(tag), message, throwable)
    }

    private fun prefixed(tag: String) = "Sink/$tag"
}
