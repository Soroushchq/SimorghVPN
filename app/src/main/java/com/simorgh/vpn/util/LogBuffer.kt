package com.simorgh.vpn.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-memory ring buffer for engine + app logs, observable by the UI. */
object LogBuffer {
    private const val MAX = 600
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    @Synchronized
    private fun add(level: String, tag: String, msg: String) {
        val line = "${fmt.format(Date())} $level/$tag: $msg"
        val cur = _lines.value
        _lines.value = if (cur.size >= MAX) cur.takeLast(MAX - 1) + line else cur + line
    }

    fun d(tag: String, msg: String) = add("D", tag, msg)
    fun i(tag: String, msg: String) = add("I", tag, msg)
    fun w(tag: String, msg: String) = add("W", tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) =
        add("E", tag, if (t != null) "$msg (${t.message})" else msg)

    fun clear() {
        _lines.value = emptyList()
    }

    fun dump(): String = _lines.value.joinToString("\n")
}
