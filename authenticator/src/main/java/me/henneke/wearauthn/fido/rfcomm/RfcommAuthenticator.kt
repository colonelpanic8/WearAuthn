package me.henneke.wearauthn.fido.rfcomm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import androidx.annotation.GuardedBy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.henneke.wearauthn.Logging
import me.henneke.wearauthn.d
import me.henneke.wearauthn.e
import me.henneke.wearauthn.fido.ApduException
import me.henneke.wearauthn.fido.CommandApdu
import me.henneke.wearauthn.fido.ResponseApdu
import me.henneke.wearauthn.fido.StatusWord
import me.henneke.wearauthn.fido.context.AuthenticatorContext
import me.henneke.wearauthn.fido.ctap2.Authenticator
import me.henneke.wearauthn.i
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Serves CTAP2 over an RFCOMM socket so that paired phones can use the watch without an NFC tap.
 *
 * Each frame in either direction is a 4-byte big-endian length followed by an ISO 7816 APDU,
 * with the same command set as the NFC transport (SELECT, NFCCTAP_MSG, GET RESPONSE).
 */
@ExperimentalUnsignedTypes
class RfcommAuthenticator(
    private val adapter: BluetoothAdapter,
    private val authenticatorContext: AuthenticatorContext,
    private val scope: CoroutineScope,
) {
    private val requestLock = Mutex()

    private val lock = Any()

    @GuardedBy("lock")
    private var acceptJob: Job? = null

    @GuardedBy("lock")
    private var serverSocket: BluetoothServerSocket? = null

    @GuardedBy("lock")
    private val clients = mutableSetOf<BluetoothSocket>()

    @SuppressLint("MissingPermission")
    fun start() {
        synchronized(lock) {
            if (acceptJob != null)
                return
            acceptJob = scope.launch(Dispatchers.IO) {
                val job = coroutineContext.job
                try {
                    val server =
                        adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                    try {
                        synchronized(lock) {
                            if (!job.isActive)
                                return@launch
                            serverSocket = server
                        }
                        i { "Listening for RFCOMM connections" }
                        while (true) {
                            val socket = runInterruptible { server.accept() }
                            i { "RFCOMM connection from ${socket.remoteDevice.address}" }
                            launch { serve(socket) }
                        }
                    } finally {
                        server.closeQuietly()
                    }
                } catch (e: IOException) {
                    if (job.isActive)
                        e(e) { "RFCOMM listener failed" }
                } finally {
                    synchronized(lock) {
                        if (acceptJob === job) {
                            acceptJob = null
                            serverSocket = null
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            acceptJob?.cancel()
            acceptJob = null
            // Neither accept() nor a blocked read notices cancellation; closing the sockets
            // is what ends them.
            serverSocket?.closeQuietly()
            serverSocket = null
            clients.forEach { it.closeQuietly() }
            clients.clear()
        }
    }

    private suspend fun serve(socket: BluetoothSocket) {
        val job = currentCoroutineContext().job
        synchronized(lock) {
            if (!job.isActive) {
                socket.closeQuietly()
                return
            }
            clients += socket
        }
        try {
            coroutineScope {
                val requests = Channel<ByteArray>()
                val worker = launch {
                    try {
                        answer(socket, requests)
                    } finally {
                        socket.closeQuietly()
                    }
                }
                // Reading on while a request is in flight is how a peer that went away gets
                // noticed, which cancels the request and dismisses its prompt.
                try {
                    read(socket, requests)
                } finally {
                    worker.cancel()
                }
            }
        } catch (e: IOException) {
            d { "RFCOMM connection closed: ${e.message}" }
        } finally {
            socket.closeQuietly()
            synchronized(lock) { clients -= socket }
        }
    }

    private suspend fun read(socket: BluetoothSocket, requests: SendChannel<ByteArray>) {
        val input = DataInputStream(socket.inputStream)
        while (true) {
            val length = input.readInt()
            if (length < 4 || length > MAX_FRAME_SIZE)
                throw IOException("Invalid frame length $length")
            val request = ByteArray(length)
            input.readFully(request)
            requests.send(request)
        }
    }

    private suspend fun answer(socket: BluetoothSocket, requests: ReceiveChannel<ByteArray>) {
        val session = ApduSession()
        val output = DataOutputStream(socket.outputStream)
        for (request in requests) {
            val response = requestLock.withLock { session.handle(request) }
            output.writeInt(response.size)
            output.write(response)
            output.flush()
        }
    }

    private fun Closeable.closeQuietly() {
        try {
            close()
        } catch (_: IOException) {
        }
    }

    private inner class ApduSession {
        private var isSelected = false
        private var lastResponse: ResponseApdu? = null
        private var chainedRequest = ubyteArrayOf()

        suspend fun handle(rawApdu: ByteArray): ByteArray = try {
            handleApdu(rawApdu.asUByteArray())
        } catch (e: ApduException) {
            lastResponse = null
            chainedRequest = ubyteArrayOf()
            e.statusWord.value
        }.asByteArray()

        private suspend fun handleApdu(raw: UByteArray): UByteArray {
            if (SELECT_FIDO_APDUS.any { raw.contentEquals(it) }) {
                isSelected = true
                lastResponse = null
                chainedRequest = ubyteArrayOf()
                return VERSION_U2F + StatusWord.NO_ERROR.value
            }
            if (!isSelected)
                throw ApduException(StatusWord.CONDITIONS_NOT_SATISFIED)
            val apdu = CommandApdu(raw)
            if (apdu.headerEquals(DESELECT_FIDO_APDU_HEADER)) {
                isSelected = false
                return StatusWord.NO_ERROR.value
            }
            if (GET_RESPONSE_APDU_HEADERS.any { apdu.headerEquals(it) }) {
                val response = lastResponse?.takeIf { it.hasNext() }
                    ?: throw ApduException(StatusWord.CONDITIONS_NOT_SATISFIED)
                return response.next(apdu.le)
            }
            if (chainedRequest.size + apdu.data.size > MAX_FRAME_SIZE)
                throw ApduException(StatusWord.WRONG_LENGTH)
            return when {
                apdu.headerEquals(NFCCTAP_MSG_CHAINING_HEADER) -> {
                    chainedRequest += apdu.data
                    StatusWord.NO_ERROR.value
                }
                NFCCTAP_MSG_HEADERS.any { apdu.headerEquals(it) } -> {
                    val request = (chainedRequest + apdu.data).asByteArray()
                    chainedRequest = ubyteArrayOf()
                    val response = Authenticator.handle(authenticatorContext, request)
                    ResponseApdu(response.asUByteArray(), StatusWord.NO_ERROR).also {
                        lastResponse = it
                    }.next(if (apdu.le == 0) MAX_RESPONSE_CHUNK else apdu.le)
                }
                else -> throw ApduException(StatusWord.INS_NOT_SUPPORTED)
            }
        }
    }

    companion object : Logging {
        override val TAG = "RfcommAuthenticator"

        const val SERVICE_NAME = "WearAuthn CTAP"
        val SERVICE_UUID: UUID = UUID.fromString("5c1b1d3a-62a4-4f4e-9f0e-8a7c2d1e6b3f")

        private const val MAX_FRAME_SIZE = 65_536
        private const val MAX_RESPONSE_CHUNK = 65_536

        private val SELECT_FIDO_APDUS = setOf(
            ubyteArrayOf(
                0x00u, 0xA4u, 0x04u, 0x00u, 0x08u, 0xA0u, 0x00u, 0x00u, 0x06u, 0x47u, 0x2Fu,
                0x00u, 0x01u, 0x00u
            ),
            ubyteArrayOf(
                0x00u, 0xA4u, 0x04u, 0x00u, 0x08u, 0xA0u, 0x00u, 0x00u, 0x06u, 0x47u, 0x2Fu,
                0x00u, 0x01u
            )
        )
        private val DESELECT_FIDO_APDU_HEADER = ubyteArrayOf(0x80u, 0x12u, 0x01u, 0x00u)
        private val GET_RESPONSE_APDU_HEADERS = setOf(
            ubyteArrayOf(0x00u, 0xC0u, 0x00u, 0x00u),
            ubyteArrayOf(0x80u, 0xC0u, 0x00u, 0x00u)
        )
        private val NFCCTAP_MSG_CHAINING_HEADER = ubyteArrayOf(0x90u, 0x10u, 0x00u, 0x00u)
        private val NFCCTAP_MSG_HEADERS = setOf(
            ubyteArrayOf(0x80u, 0x10u, 0x00u, 0x00u),
            ubyteArrayOf(0x80u, 0x10u, 0x80u, 0x00u)
        )
        private val VERSION_U2F = "U2F_V2".toByteArray().asUByteArray()
    }
}
