package com.danis.linuxdistroapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class VncClientState(
    val status: String = "Disconnected",
    val bitmap: Bitmap? = null,
    val error: String? = null
)

class VncClient(
    private val password: String,
    private val distroName: String = "Linux"
) {
    companion object {
        private const val PORT = 5901
        private const val MAX_FRAME_PIXELS = 32 * 1024 * 1024
        private const val FRAME_UPDATE_INTERVAL_MILLIS = 100L
        private const val ENCODING_RAW = 0
        private const val ENCODING_DESKTOP_SIZE = -223
    }

    private val mutableState = MutableStateFlow(VncClientState())
    val state = mutableState.asStateFlow()
    private val writeLock = Any()
    private val writeExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "andistro-vnc-input").apply { isDaemon = true }
    }

    @Volatile private var socket: Socket? = null
    @Volatile private var output: DataOutputStream? = null
    @Volatile private var connectionThread: Thread? = null
    @Volatile private var closed = false

    @Synchronized
    fun connect() {
        if (connectionThread?.isAlive == true) return
        closed = false
        mutableState.value = VncClientState(status = "Connecting to $distroName desktop…")
        connectionThread = Thread(::runConnection, "andistro-vnc-client").apply {
            isDaemon = true
            start()
        }
    }

    fun close() {
        closed = true
        runCatching { socket?.close() }
        socket = null
        synchronized(writeLock) { output = null }
        writeExecutor.shutdownNow()
    }

    fun sendPointer(x: Int, y: Int, buttons: Int) {
        sendMessage {
            writeByte(5)
            writeByte(buttons)
            writeShort(x.coerceIn(0, 0xffff))
            writeShort(y.coerceIn(0, 0xffff))
        }
    }

    fun sendKey(keySym: Int) {
        sendKeyCombination(keySym, emptyList())
    }

    fun sendKeyCombination(keySym: Int, modifiers: List<Int>) {
        val heldModifiers = modifiers.distinct()
        sendMessage {
            heldModifiers.forEach { writeKeyEvent(it, true) }
            writeKeyEvent(keySym, true)
            writeKeyEvent(keySym, false)
            heldModifiers.asReversed().forEach { writeKeyEvent(it, false) }
        }
    }

    fun sendText(text: String) {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            index += Character.charCount(codePoint)
            val keySym = when (codePoint) {
                '\n'.code, '\r'.code -> 0xff0d
                '\t'.code -> 0xff09
                '\b'.code -> 0xff08
                in 0x20..0xff -> codePoint
                in 0x100..0x10ffff -> codePoint or 0x01000000
                else -> continue
            }
            sendKey(keySym)
        }
    }

    private fun DataOutputStream.writeKeyEvent(keySym: Int, down: Boolean) {
        writeByte(4)
        writeByte(if (down) 1 else 0)
        writeShort(0)
        writeInt(keySym)
    }

    private fun sendMessage(message: DataOutputStream.() -> Unit) {
        synchronized(writeLock) {
            if (closed) return
            try {
                writeExecutor.execute {
                    try {
                        synchronized(writeLock) {
                            if (closed) return@execute
                            val stream = output ?: return@execute
                            stream.message()
                            stream.flush()
                        }
                    } catch (error: IOException) {
                        if (!closed) {
                            mutableState.value = mutableState.value.copy(
                                status = "Connection lost",
                                error = error.message ?: "Unable to send input to the desktop."
                            )
                            runCatching { socket?.close() }
                        }
                    }
                }
            } catch (error: RejectedExecutionException) {
                if (!closed) {
                    mutableState.value = mutableState.value.copy(
                        status = "Connection lost",
                        error = error.message ?: "Unable to send input to the desktop."
                    )
                }
            }
        }
    }

    private fun runConnection() {
        var connection: Socket? = null
        try {
            mutableState.value = VncClientState(status = "Waiting for the local VNC server…")
            connection = connectToLocalServer()
            socket = connection
            val input = DataInputStream(BufferedInputStream(connection.getInputStream()))
            val outputStream = DataOutputStream(BufferedOutputStream(connection.getOutputStream()))
            synchronized(writeLock) { output = outputStream }

            performHandshake(input, outputStream)
            mutableState.value = VncClientState(status = "Connected")
            readFramebuffer(input, outputStream)
        } catch (error: Exception) {
            if (!closed) {
                mutableState.value = mutableState.value.copy(
                    status = "Disconnected",
                    error = error.message ?: error.javaClass.simpleName
                )
            }
        } finally {
            synchronized(writeLock) { output = null }
            runCatching { connection?.close() }
            if (socket === connection) socket = null
            if (!closed && mutableState.value.error == null) {
                mutableState.value = mutableState.value.copy(status = "Disconnected")
            }
            synchronized(this) {
                if (connectionThread === Thread.currentThread()) connectionThread = null
            }
        }
    }

    private fun connectToLocalServer(): Socket {
        var lastError: IOException? = null
        repeat(30) {
            if (closed) throw IOException("Connection cancelled.")
            val candidate = Socket()
            try {
                candidate.connect(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 1_000)
                candidate.tcpNoDelay = true
                return candidate
            } catch (error: IOException) {
                lastError = error
                runCatching { candidate.close() }
                Thread.sleep(500)
            }
        }
        throw IOException(
            "Could not connect to 127.0.0.1:$PORT. Check that the XFCE desktop is running.",
            lastError
        )
    }

    private fun performHandshake(input: DataInputStream, output: DataOutputStream) {
        val version = ByteArray(12)
        input.readFully(version)
        output.write("RFB 003.008\n".toByteArray(Charsets.US_ASCII))
        output.flush()

        val securityCount = input.readUnsignedByte()
        if (securityCount == 0) throw IOException("The VNC server refused the connection.")
        val securityTypes = ByteArray(securityCount)
        input.readFully(securityTypes)
        if (securityTypes.none { (it.toInt() and 0xff) == 2 }) {
            throw IOException("The VNC server does not offer password authentication.")
        }
        val selectedType = 2
        output.writeByte(selectedType)
        output.flush()

        if (selectedType == 2) {
            val challenge = ByteArray(16)
            input.readFully(challenge)
            output.write(encryptVncChallenge(challenge, password))
            output.flush()
        }
        if (input.readInt() != 0) throw IOException("VNC authentication failed. Restart the desktop and retry.")

        output.writeByte(1)
        output.flush()
        val width = input.readUnsignedShort()
        val height = input.readUnsignedShort()
        input.skipFully(16L)
        val nameLength = input.readInt()
        if (nameLength !in 0..65_536) throw IOException("The VNC server sent an invalid desktop name.")
        input.skipFully(nameLength.toLong())
        validateFrameSize(width, height)

        output.writeByte(0)
        output.write(byteArrayOf(0, 0, 0))
        output.writeByte(32)
        output.writeByte(24)
        output.writeByte(0)
        output.writeByte(1)
        output.writeShort(255)
        output.writeShort(255)
        output.writeShort(255)
        output.writeByte(16)
        output.writeByte(8)
        output.writeByte(0)
        output.write(byteArrayOf(0, 0, 0))

        output.writeByte(2)
        output.writeByte(0)
        output.writeShort(2)
        output.writeInt(ENCODING_RAW)
        output.writeInt(ENCODING_DESKTOP_SIZE)
        requestFramebuffer(output, width, height, incremental = false)
        output.flush()
        currentWidth = width
        currentHeight = height
        framebufferPixels = IntArray(width * height)
    }

    @Volatile private var currentWidth = 0
    @Volatile private var currentHeight = 0
    private var framebufferPixels = IntArray(0)
    private var lastFramePublishedAt = 0L

    private fun readFramebuffer(input: DataInputStream, output: DataOutputStream) {
        while (!closed) {
            when (input.readUnsignedByte()) {
                0 -> {
                    input.readUnsignedByte()
                    val rectangleCount = input.readUnsignedShort()
                    if (rectangleCount > 4096) throw IOException("The VNC update contains too many rectangles.")
                    repeat(rectangleCount) {
                        val x = input.readUnsignedShort()
                        val y = input.readUnsignedShort()
                        val width = input.readUnsignedShort()
                        val height = input.readUnsignedShort()
                        when (input.readInt()) {
                            ENCODING_RAW -> readRawRectangle(input, x, y, width, height)
                            ENCODING_DESKTOP_SIZE -> resizeFramebuffer(width, height)
                            else -> throw IOException("The VNC server sent an unsupported screen update.")
                        }
                    }
                    publishFramebuffer()
                    Thread.sleep(FRAME_UPDATE_INTERVAL_MILLIS)
                    synchronized(writeLock) {
                        requestFramebuffer(output, currentWidth, currentHeight, incremental = true)
                        output.flush()
                    }
                }

                2 -> Unit
                3 -> {
                    input.skipFully(3)
                    val length = input.readInt()
                    if (length !in 0..1_048_576) throw IOException("The VNC server sent invalid clipboard data.")
                    input.skipFully(length.toLong())
                }

                else -> throw IOException("The VNC server sent an unsupported message.")
            }
        }
    }

    private fun readRawRectangle(
        input: DataInputStream,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        if (width == 0 || height == 0) return
        if (x + width > currentWidth || y + height > currentHeight) {
            throw IOException("The VNC server sent a screen rectangle outside the framebuffer.")
        }
        val row = ByteArray(width * 4)
        repeat(height) { rowIndex ->
            input.readFully(row)
            val offset = (y + rowIndex) * currentWidth + x
            var byteIndex = 0
            repeat(width) { column ->
                val blue = row[byteIndex++].toInt() and 0xff
                val green = row[byteIndex++].toInt() and 0xff
                val red = row[byteIndex++].toInt() and 0xff
                byteIndex++
                framebufferPixels[offset + column] =
                    Color.rgb(red, green, blue)
            }
        }
    }

    private fun resizeFramebuffer(width: Int, height: Int) {
        validateFrameSize(width, height)
        currentWidth = width
        currentHeight = height
        framebufferPixels = IntArray(width * height)
    }

    private fun publishFramebuffer() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFramePublishedAt < FRAME_UPDATE_INTERVAL_MILLIS) return
        lastFramePublishedAt = now
        val frame = Bitmap.createBitmap(currentWidth, currentHeight, Bitmap.Config.ARGB_8888)
        frame.setPixels(framebufferPixels, 0, currentWidth, 0, 0, currentWidth, currentHeight)
        mutableState.value = VncClientState(status = "Connected", bitmap = frame)
    }

    private fun validateFrameSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_FRAME_PIXELS) {
            throw IOException("The VNC server reported an unsupported screen size.")
        }
    }

    private fun requestFramebuffer(
        output: DataOutputStream,
        width: Int,
        height: Int,
        incremental: Boolean
    ) {
        output.writeByte(3)
        output.writeByte(if (incremental) 1 else 0)
        output.writeShort(0)
        output.writeShort(0)
        output.writeShort(width)
        output.writeShort(height)
    }

    private fun encryptVncChallenge(challenge: ByteArray, password: String): ByteArray {
        val key = ByteArray(8)
        password.take(8).forEachIndexed { index, character ->
            key[index] = reverseBits(character.code.toByte())
        }
        val cipher = Cipher.getInstance("DES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "DES"))
        return cipher.doFinal(challenge)
    }

    private fun reverseBits(value: Byte): Byte {
        var input = value.toInt() and 0xff
        var reversed = 0
        repeat(8) {
            reversed = (reversed shl 1) or (input and 1)
            input = input shr 1
        }
        return reversed.toByte()
    }
}

class VncFramebufferView(context: Context) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    private var framebuffer: Bitmap? = null
    private var pointerListener: ((Int, Int, Int) -> Unit)? = null
    private var pressed = false

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = true
    }

    fun setFramebuffer(bitmap: Bitmap?) {
        framebuffer = bitmap
        invalidate()
    }

    fun setPointerListener(listener: (Int, Int, Int) -> Unit) {
        pointerListener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = framebuffer ?: return
        val scale = minOf(width.toFloat() / image.width, height.toFloat() / image.height)
        val drawnWidth = image.width * scale
        val drawnHeight = image.height * scale
        val left = (width - drawnWidth) / 2f
        val top = (height - drawnHeight) / 2f
        destination.set(left, top, left + drawnWidth, top + drawnHeight)
        canvas.drawBitmap(image, null, destination, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val image = framebuffer ?: return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            pressed = true
            requestFocus()
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            pressed = false
        }
        if (event.actionMasked in setOf(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL
            )
        ) {
            val scale = minOf(width.toFloat() / image.width, height.toFloat() / image.height)
            val left = (width - image.width * scale) / 2f
            val top = (height - image.height * scale) / 2f
            val x = ((event.x - left) / scale).toInt().coerceIn(0, image.width - 1)
            val y = ((event.y - top) / scale).toInt().coerceIn(0, image.height - 1)
            val buttons = if (pressed && event.actionMasked != MotionEvent.ACTION_CANCEL) 1 else 0
            pointerListener?.invoke(x, y, buttons)
            return true
        }
        return true
    }
}

private fun DataInputStream.skipFully(byteCount: Long) {
    var remaining = byteCount
    while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped > 0) {
            remaining -= skipped
        } else {
            if (read() < 0) throw IOException("The VNC server closed the connection unexpectedly.")
            remaining--
        }
    }
}
