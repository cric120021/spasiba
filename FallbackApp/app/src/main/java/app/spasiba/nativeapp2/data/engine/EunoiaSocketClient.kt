package app.spasiba.nativeapp2.data.engine

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class EunoiaSocketEvent(
    val eventId: String,
    val sessionId: String,
    val senderDeviceId: String,
    val eventType: String,
    val sequence: Int,
    val clientSentAt: Long,
    val serverTime: Long,
    val payload: JSONObject
)

interface EunoiaSocketEventListener {
    fun onEventReceived(event: EunoiaSocketEvent)
    fun onConnectionStatusChanged(isConnected: Boolean)
}

class EunoiaSocketClient(
    private var socketUrl: String = DEFAULT_SOCKET_URL
) {
    companion object {
        const val DEFAULT_SOCKET_URL = "" // Render dependency removed - Vercel HTTP Signals active
        const val API_BASE_URL = "https://spasiba.vercel.app"
    }

    private val TAG = "EunoiaSocketClient"
    private var webSocket: WebSocket? = null
    private val sequenceCounter = AtomicInteger(0)
    private var currentSessionId: String? = null
    private var myDeviceId: String? = null
    private var listener: EunoiaSocketEventListener? = null
    private var isConnected = false

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    fun updateSocketUrl(newUrl: String) {
        if (newUrl.isNotBlank()) {
            this.socketUrl = newUrl
        }
    }

    fun setEventListener(listener: EunoiaSocketEventListener) {
        this.listener = listener
    }

    fun connect(sessionId: String, deviceId: String) {
        this.currentSessionId = sessionId
        this.myDeviceId = deviceId

        if (socketUrl.isBlank()) {
            Log.d(TAG, "No socket URL configured (Render removed). Using Vercel HTTP signals.")
            return
        }

        if (isConnected) {
            Log.d(TAG, "Already connected to socket for session $sessionId")
            return
        }

        val request = Request.Builder()
            .url(socketUrl)
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully to $socketUrl")
                isConnected = true
                listener?.onConnectionStatusChanged(true)

                // Join room upon connection
                sendEvent("joinSession", JSONObject().apply {
                    put("sessionId", sessionId)
                    put("deviceId", deviceId)
                })
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val event = EunoiaSocketEvent(
                        eventId = json.optString("eventId", UUID.randomUUID().toString()),
                        sessionId = json.optString("sessionId", sessionId),
                        senderDeviceId = json.optString("senderDeviceId", ""),
                        eventType = json.optString("eventType", "unknown"),
                        sequence = json.optInt("sequence", 0),
                        clientSentAt = json.optLong("clientSentAt", System.currentTimeMillis()),
                        serverTime = json.optLong("serverTime", System.currentTimeMillis()),
                        payload = json.optJSONObject("payload") ?: JSONObject()
                    )

                    // Ignore self messages
                    if (event.senderDeviceId != myDeviceId) {
                        listener?.onEventReceived(event)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed parsing WebSocket message: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                isConnected = false
                listener?.onConnectionStatusChanged(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}. Falling back to HTTP signal relay.")
                isConnected = false
                listener?.onConnectionStatusChanged(false)
            }
        })
    }

    fun sendEvent(eventType: String, payload: JSONObject) {
        val session = currentSessionId ?: return
        val device = myDeviceId ?: return

        val payloadObj = JSONObject(payload.toString()).apply {
            put("type", eventType)
            put("sessionId", session)
        }

        val eventJson = JSONObject().apply {
            put("eventId", UUID.randomUUID().toString())
            put("sessionId", session)
            put("senderDeviceId", device)
            put("eventType", eventType)
            put("sequence", sequenceCounter.incrementAndGet())
            put("clientSentAt", System.currentTimeMillis())
            put("payload", payloadObj)
        }.toString()

        if (isConnected && webSocket != null) {
            webSocket?.send(eventJson)
            Log.d(TAG, "Sent socket event: $eventType seq=${sequenceCounter.get()}")
        } else {
            // Relay via HTTP signal fallback so sync never breaks!
            relayEventViaHttpSignal(device, payloadObj)
        }
    }

    private fun relayEventViaHttpSignal(fromDeviceId: String, payload: JSONObject) {
        val session = currentSessionId ?: return
        Thread {
            try {
                val jsonPayload = JSONObject().apply {
                    put("fromId", fromDeviceId)
                    put("sessionId", session)
                    put("payload", payload)
                }.toString()

                val body = jsonPayload.toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("$API_BASE_URL/api/eunoia-signal")
                    .post(body)
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    Log.d(TAG, "HTTP signal fallback relay result code: ${resp.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "HTTP signal relay failed: ${e.message}")
            }
        }.start()
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected session")
        webSocket = null
        isConnected = false
        listener?.onConnectionStatusChanged(false)
    }
}
