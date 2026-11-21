package com.wakeupver.pijar.gamepad

import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

enum class GamepadStickSide(val xAxis: String, val yAxis: String) {
    LEFT("ABS_X", "ABS_Y"),
    RIGHT("ABS_RX", "ABS_RY"),
}

interface GamepadBackend {
    val isRunning: Boolean
    val lastError: String?
    fun start(): Boolean
    fun setButton(control: GamepadControl, pressed: Boolean)
    fun setStick(side: GamepadStickSide, x: Float, y: Float)
    fun releaseAll()
    fun stop()
}

object RootAccess {
    fun available(): Boolean = try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor(2, TimeUnit.SECONDS)
        output.contains("uid=0")
    } catch (_: Exception) {
        false
    }
}

class UinputGamepadBackend : GamepadBackend {
    private val responses = LinkedBlockingQueue<String>()
    private val errors = LinkedBlockingQueue<String>()
    private val buttonStates = LinkedHashMap<String, Int>()
    private val axisStates = LinkedHashMap<String, Int>()
    private val dpadPressed = HashSet<String>()
    private var process: Process? = null
    private var input: BufferedWriter? = null

    @Volatile
    override var isRunning: Boolean = false
        private set

    @Volatile
    override var lastError: String? = null
        private set

    override fun start(): Boolean {
        if (isRunning) return true
        return try {
            val binary = if (File("/system/bin/uinput").exists()) "/system/bin/uinput" else "uinput"
            val started = Runtime.getRuntime().exec(arrayOf("su", "-c", "$binary -"))
            process = started
            input = BufferedWriter(OutputStreamWriter(started.outputStream, Charsets.UTF_8))
            responses.clear()
            errors.clear()
            lastError = null
            startReader(started.inputStream)
            startReader(started.errorStream, errorStream = true)
            write(registerCommand())
            write(syncCommand())
            if (!waitForSync(3_000L)) {
                stop()
                return false
            }
            releaseAll()
            isRunning = true
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    override fun setButton(control: GamepadControl, pressed: Boolean) {
        if (!isRunning) return
        when (control.id) {
            "dpad_up", "dpad_down", "dpad_left", "dpad_right" -> updateDpad(control.id, pressed)
            "l2" -> updateStates(axes = mapOf("ABS_Z" to if (pressed) 255 else 0))
            "r2" -> updateStates(axes = mapOf("ABS_RZ" to if (pressed) 255 else 0))
            else -> {
                val key = keyLabels[control.id] ?: return
                updateStates(keys = mapOf(key to if (pressed) 1 else 0))
            }
        }
    }

    override fun setStick(side: GamepadStickSide, x: Float, y: Float) {
        if (!isRunning) return
        val nx = x.coerceIn(-1f, 1f)
        val ny = y.coerceIn(-1f, 1f)
        updateStates(
            axes = mapOf(
                side.xAxis to (nx * MAX_STICK).roundToInt(),
                side.yAxis to (ny * MAX_STICK).roundToInt(),
            ),
        )
    }

    override fun releaseAll() {
        if (!isRunning && process == null) return
        dpadPressed.clear()
        val keys = keyLabels.values.distinct().associateWith { 0 }
        val axes = LinkedHashMap<String, Int>()
        axisNames.associateWith { 0 }.let { axes.putAll(it) }
        axes["ABS_HAT0X"] = 0
        axes["ABS_HAT0Y"] = 0
        axes["ABS_Z"] = 0
        axes["ABS_RZ"] = 0
        updateStates(keys = keys, axes = axes)
    }

    override fun stop() {
        isRunning = false
        runCatching { input?.close() }
        runCatching { process?.destroy() }
        input = null
        process = null
        buttonStates.clear()
        axisStates.clear()
        dpadPressed.clear()
        responses.clear()
        errors.clear()
    }

    private fun startReader(stream: java.io.InputStream, errorStream: Boolean = false) {
        Thread {
            val pending = StringBuilder()
            var depth = 0
            try {
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(1024)
                    while (true) {
                        val read = reader.read(buffer)
                        if (read == -1) break
                        for (index in 0 until read) {
                            val char = buffer[index]
                            if (char == '{') depth++
                            pending.append(char)
                            when {
                                char == '}' && depth > 0 -> {
                                    depth--
                                    if (depth == 0) {
                                        deliver(pending.toString(), errorStream)
                                        pending.setLength(0)
                                    }
                                }
                                char == '\n' && depth == 0 -> {
                                    deliver(pending.toString(), errorStream)
                                    pending.setLength(0)
                                }
                            }
                        }
                    }
                    if (pending.isNotBlank()) deliver(pending.toString(), errorStream)
                }
            } catch (_: Exception) {
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun deliver(raw: String, errorStream: Boolean) {
        val text = raw.trim()
        if (text.isEmpty()) return
        if (errorStream) errors.offer(text) else responses.offer(text)
    }

    private fun updateDpad(id: String, pressed: Boolean) {
        if (pressed) dpadPressed += id else dpadPressed -= id
        val x = when {
            "dpad_right" in dpadPressed -> 1
            "dpad_left" in dpadPressed -> -1
            else -> 0
        }
        val y = when {
            "dpad_down" in dpadPressed -> 1
            "dpad_up" in dpadPressed -> -1
            else -> 0
        }
        updateStates(axes = mapOf("ABS_HAT0X" to x, "ABS_HAT0Y" to y))
    }

    private fun updateStates(keys: Map<String, Int> = emptyMap(), axes: Map<String, Int> = emptyMap()) {
        val events = ArrayList<String>()
        for ((key, value) in keys) {
            if (buttonStates.put(key, value) == value) continue
            events += "EV_KEY"
            events += key
            events += value.toString()
        }
        for ((axis, value) in axes) {
            if (axisStates.put(axis, value) == value) continue
            events += "EV_ABS"
            events += axis
            events += value.toString()
        }
        if (events.isEmpty()) return
        events += "EV_SYN"
        events += "SYN_REPORT"
        events += "0"
        write("""{"id":1,"command":"updateTimeBase"}""")
        write("""{"id":1,"command":"inject","events":[${events.joinToString(",") { "\"$it\"" }}]}""")
    }

    private fun write(command: String) {
        val writer = input ?: return
        writer.write(command)
        writer.newLine()
        writer.flush()
    }

    private fun waitForSync(timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (true) {
            errors.poll()?.let { lastError = it.take(160) }
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) break
            val response = responses.poll(remaining, TimeUnit.NANOSECONDS) ?: continue
            if (response.contains("syncToken") && response.contains(SYNC_TOKEN)) {
                lastError = null
                return true
            }
            lastError = response.take(160)
        }
        if (lastError.isNullOrBlank()) lastError = "no sync response from uinput"
        return false
    }

    private fun registerCommand(): String = """
        {"id":1,"command":"register","name":"Microsoft X-Box 360 pad","vid":1118,"pid":654,"bus":"usb",
        "configuration":[
          {"type":"UI_SET_EVBIT","data":["EV_KEY","EV_ABS"]},
          {"type":"UI_SET_KEYBIT","data":[304,305,307,308,310,311,314,315,317,318]},
          {"type":"UI_SET_ABSBIT","data":[0,1,2,3,4,5,16,17]}
        ],
        "abs_info":[
          {"code":0,"info":{"value":0,"minimum":-32768,"maximum":32767,"fuzz":0,"flat":0,"resolution":1}},
          {"code":1,"info":{"value":0,"minimum":-32768,"maximum":32767,"fuzz":0,"flat":0,"resolution":1}},
          {"code":2,"info":{"value":0,"minimum":0,"maximum":255,"fuzz":0,"flat":0,"resolution":1}},
          {"code":3,"info":{"value":0,"minimum":-32768,"maximum":32767,"fuzz":0,"flat":0,"resolution":1}},
          {"code":4,"info":{"value":0,"minimum":-32768,"maximum":32767,"fuzz":0,"flat":0,"resolution":1}},
          {"code":5,"info":{"value":0,"minimum":0,"maximum":255,"fuzz":0,"flat":0,"resolution":1}},
          {"code":16,"info":{"value":0,"minimum":-1,"maximum":1,"fuzz":0,"flat":0,"resolution":1}},
          {"code":17,"info":{"value":0,"minimum":-1,"maximum":1,"fuzz":0,"flat":0,"resolution":1}}
        ]}
    """.trimIndent().replace("\n", "")

    private fun syncCommand(): String = """{"id":1,"command":"sync","syncToken":"$SYNC_TOKEN"}"""

    private companion object {
        const val SYNC_TOKEN = "pijar-gamepad"
        const val MAX_STICK = 32_767
        val keyLabels = mapOf(
            "cross" to 304,
            "circle" to 305,
            "square" to 308,
            "triangle" to 307,
            "l1" to 310,
            "r1" to 311,
            "select" to 314,
            "start" to 315,
            "l3" to 317,
            "r3" to 318,
        ).mapValues { it.value.toString() }
        val axisNames = listOf("ABS_X", "ABS_Y", "ABS_Z", "ABS_RX", "ABS_RY", "ABS_RZ")
    }
}
