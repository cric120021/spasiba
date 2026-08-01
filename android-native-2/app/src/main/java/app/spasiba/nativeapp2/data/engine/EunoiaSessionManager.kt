package app.spasiba.nativeapp2.data.engine

import android.content.Context
import android.provider.Settings
import android.util.Log
import app.spasiba.nativeapp2.data.api.YouTubeMusicApi
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.player.NativePlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class EunoiaSessionStatus {
    DISCONNECTED,
    INVITE_SENT,
    INVITE_RECEIVED,
    REJECTED_BUSY,
    CONNECTED
}

data class FlyingReaction(
    val id: String,
    val emoji: String,
    val startXRatio: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class EunoiaChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val isPartner: Boolean,
    val messageText: String,
    val timestamp: String = "Just now"
)

class EunoiaSessionManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences("eunoia_prefs", Context.MODE_PRIVATE)

    private val _myDeviceId = MutableStateFlow<String>("")
    val myDeviceId: StateFlow<String> = _myDeviceId.asStateFlow()

    private val _sessionStatus = MutableStateFlow(EunoiaSessionStatus.DISCONNECTED)
    val sessionStatus: StateFlow<EunoiaSessionStatus> = _sessionStatus.asStateFlow()

    private val _partnerDeviceId = MutableStateFlow<String?>(null)
    val partnerDeviceId: StateFlow<String?> = _partnerDeviceId.asStateFlow()

    private val _partnerName = MutableStateFlow<String>("...")
    val partnerName: StateFlow<String> = _partnerName.asStateFlow()

    private val _pendingInviteSender = MutableStateFlow<String?>(null)
    val pendingInviteSender: StateFlow<String?> = _pendingInviteSender.asStateFlow()

    private val _flyingReactions = MutableStateFlow<List<FlyingReaction>>(emptyList())
    val flyingReactions: StateFlow<List<FlyingReaction>> = _flyingReactions.asStateFlow()

    private val _sessionMessages = MutableStateFlow<List<EunoiaChatMessage>>(listOf(
        EunoiaChatMessage(
            id = "1",
            senderId = "system",
            senderName = "...",
            isPartner = true,
            messageText = "Connected to Eunoia session 🎧",
            timestamp = "Just now"
        )
    ))
    val sessionMessages: StateFlow<List<EunoiaChatMessage>> = _sessionMessages.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var cloudWebSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    init {
        var id = prefs.getString("device_id", null)
        if (id.isNullOrBlank()) {
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (e: Exception) { null }

            id = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                "SPASIBA-${androidId.takeLast(6).uppercase()}"
            } else {
                "SPASIBA-${UUID.randomUUID().toString().take(6).uppercase()}"
            }
            prefs.edit().putString("device_id", id).apply()
        }
        _myDeviceId.value = id ?: "SPASIBA-000000"

        startSocketListener()
        connectCloudServer()
    }

    private fun connectCloudServer() {
        val cloudUrl = System.getProperty("spasiba.eunoia.url")
            ?: "wss://spasiba-fk0vtkc3o-cric120021-4555s-projects.vercel.app/ws"
        val request = Request.Builder()
            .url(cloudUrl)
            .build()

        cloudWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d("EunoiaCloud", "Connected to Cloudflare WebSocket Signaling Server!")
                val joinMsg = JSONObject().apply {
                    put("type", "join")
                    put("roomId", "EUNOIA_ROOM")
                    put("userId", _myDeviceId.value)
                    put("displayName", "Spasiba")
                }
                ws.send(joinMsg.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d("EunoiaCloud", "Cloud WebSocket payload: $text")
                runCatching {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    if (type == "signal") {
                        val from = json.optString("from")
                        if (from.isNotBlank() && from == _myDeviceId.value) return@runCatching
                        val payload = json.optJSONObject("payload") ?: return@runCatching
                        val msgType = payload.optString("type")

                        scope.launch(Dispatchers.Main) {
                            when (msgType) {
                                "INVITE" -> {
                                    _pendingInviteSender.value = from
                                    _sessionStatus.value = EunoiaSessionStatus.INVITE_RECEIVED
                                    showInviteNotification(from)
                                }
                                "ACCEPT" -> {
                                    _pendingInviteSender.value = null
                                    _partnerDeviceId.value = from
                                    _partnerName.value = "..."
                                    _sessionStatus.value = EunoiaSessionStatus.CONNECTED
                                }
                                "REJECT" -> {
                                    _pendingInviteSender.value = null
                                    _sessionStatus.value = EunoiaSessionStatus.REJECTED_BUSY
                                }
                                "CHAT_MSG" -> {
                                    val msgText = payload.optString("text")
                                    val newMsg = EunoiaChatMessage(
                                        id = UUID.randomUUID().toString(),
                                        senderId = from,
                                        senderName = "...",
                                        isPartner = true,
                                        messageText = msgText
                                    )
                                    _sessionMessages.value = _sessionMessages.value + newMsg
                                }
                                "REACTION" -> {
                                    val emoji = payload.optString("emoji")
                                    triggerFlyingReaction(emoji)
                                }
                            }
                        }
                    }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("EunoiaCloud", "Cloud WebSocket failure: ${t.message}")
                scope.launch {
                    delay(3000)
                    connectCloudServer()
                }
            }
        })
    }

    private fun sendCloudRelay(targetId: String, payload: JSONObject) {
        val relayMsg = JSONObject().apply {
            put("type", "relay")
            put("to", targetId)
            put("payload", payload)
        }
        cloudWebSocket?.send(relayMsg.toString())
    }

    private fun startSocketListener() {
        scope.launch(Dispatchers.IO) {
            val ports = listOf(8888, 8889, 9999)
            for (port in ports) {
                try {
                    serverSocket = ServerSocket(port)
                    Log.d("EunoiaSocket", "Eunoia Socket Server listening on port $port...")
                    while (true) {
                        val clientSocket = serverSocket?.accept() ?: break
                        handleIncomingSocketConnection(clientSocket)
                    }
                    break
                } catch (e: Exception) {
                    Log.e("EunoiaSocket", "Socket Server Exception on port $port: ${e.message}")
                }
            }
        }
    }

    private fun handleIncomingSocketConnection(socket: Socket) {
        scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val rawJson = reader.readLine() ?: return@launch
                Log.d("EunoiaSocket", "Received raw socket payload: $rawJson")

                val json = JSONObject(rawJson)
                val type = json.optString("type")
                val senderId = json.optString("senderId")

                if (senderId.isNotBlank() && senderId == _myDeviceId.value) {
                    socket.close()
                    return@launch
                }

                scope.launch(Dispatchers.Main) {
                    when (type) {
                        "INVITE" -> {
                            _pendingInviteSender.value = senderId
                            _sessionStatus.value = EunoiaSessionStatus.INVITE_RECEIVED
                        }
                        "ACCEPT" -> {
                            _pendingInviteSender.value = null
                            _partnerDeviceId.value = senderId
                            _partnerName.value = "..."
                            _sessionStatus.value = EunoiaSessionStatus.CONNECTED
                        }
                        "REJECT" -> {
                            _pendingInviteSender.value = null
                            _sessionStatus.value = EunoiaSessionStatus.REJECTED_BUSY
                        }
                        "CHAT_MSG" -> {
                            val text = json.optString("text")
                            val newMsg = EunoiaChatMessage(
                                id = UUID.randomUUID().toString(),
                                senderId = senderId,
                                senderName = "...",
                                isPartner = true,
                                messageText = text
                            )
                            _sessionMessages.value = _sessionMessages.value + newMsg
                        }
                        "REACTION" -> {
                            val emoji = json.optString("emoji")
                            triggerFlyingReaction(emoji)
                        }
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Log.e("EunoiaSocket", "Failed handling incoming socket: ${e.message}")
            }
        }
    }

    private fun sendSocketPayload(jsonPayload: String) {
        scope.launch(Dispatchers.IO) {
            val targetHosts = listOf("127.0.0.1", "10.0.2.2", "10.0.2.15")
            val targetPorts = listOf(8888, 8889, 9999)
            for (host in targetHosts) {
                for (port in targetPorts) {
                    runCatching {
                        val socket = Socket()
                        socket.connect(java.net.InetSocketAddress(host, port), 400)
                        val writer = PrintWriter(socket.getOutputStream(), true)
                        writer.println(jsonPayload)
                        socket.close()
                        Log.d("EunoiaSocket", "Payload successfully broadcast to $host:$port")
                    }
                }
            }
        }
    }

    fun sendInvite(targetDeviceId: String) {
        var trimmed = targetDeviceId.trim()
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1).trim()
        }
        if (!trimmed.startsWith("SPASIBA-", ignoreCase = true)) {
            trimmed = "SPASIBA-${trimmed}"
        }
        trimmed = trimmed.uppercase()
        if (trimmed.isBlank()) return

        _sessionStatus.value = EunoiaSessionStatus.INVITE_SENT
        _partnerDeviceId.value = trimmed

        val payload = JSONObject().apply {
            put("type", "INVITE")
            put("senderId", _myDeviceId.value)
            put("targetId", trimmed)
        }

        sendSocketPayload(payload.toString())
        sendCloudRelay(trimmed, payload)
    }

    fun receiveSimulatedInvite(fromDeviceId: String) {
        _pendingInviteSender.value = fromDeviceId
        _sessionStatus.value = EunoiaSessionStatus.INVITE_RECEIVED
    }

    fun acceptInvite(senderId: String, playerManager: NativePlayerManager) {
        _pendingInviteSender.value = null
        _partnerDeviceId.value = senderId
        _partnerName.value = "..."
        _sessionStatus.value = EunoiaSessionStatus.CONNECTED

        val payload = JSONObject().apply {
            put("type", "ACCEPT")
            put("senderId", _myDeviceId.value)
        }
        sendSocketPayload(payload.toString())
        sendCloudRelay(senderId, payload)

        mergeAndCapQueues(playerManager)
    }

    fun rejectInvite(senderId: String) {
        _pendingInviteSender.value = null
        _sessionStatus.value = EunoiaSessionStatus.REJECTED_BUSY

        val payload = JSONObject().apply {
            put("type", "REJECT")
            put("senderId", _myDeviceId.value)
        }
        sendSocketPayload(payload.toString())
        sendCloudRelay(senderId, payload)
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val myMsg = EunoiaChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = _myDeviceId.value,
            senderName = "You",
            isPartner = false,
            messageText = text
        )
        _sessionMessages.value = _sessionMessages.value + myMsg

        val payload = JSONObject().apply {
            put("type", "CHAT_MSG")
            put("senderId", _myDeviceId.value)
            put("text", text)
        }
        sendSocketPayload(payload.toString())
        _partnerDeviceId.value?.let { partner ->
            sendCloudRelay(partner, payload)
        }
    }

    fun triggerFlyingReaction(emoji: String) {
        val newReaction = FlyingReaction(
            id = UUID.randomUUID().toString(),
            emoji = emoji,
            startXRatio = (0.2f + Math.random().toFloat() * 0.6f)
        )
        _flyingReactions.value = _flyingReactions.value.takeLast(15) + newReaction

        val payload = JSONObject().apply {
            put("type", "REACTION")
            put("senderId", _myDeviceId.value)
            put("emoji", emoji)
        }
        sendSocketPayload(payload.toString())
        _partnerDeviceId.value?.let { partner ->
            sendCloudRelay(partner, payload)
        }
    }

    fun mergeAndCapQueues(playerManager: NativePlayerManager) {
        val currentQ: List<Song> = playerManager.queue.value
        val fallbackQ: List<Song> = YouTubeMusicApi.getFallbackTracks()

        val combined: List<Song> = (currentQ + fallbackQ).distinctBy { song -> song.id }.take(10)
        if (combined.isNotEmpty()) {
            playerManager.setQueue(combined, activeSongId = playerManager.currentSong.value?.id)
        }
    }

    fun checkAndCorrectPlaybackDrift(playerManager: NativePlayerManager, partnerPositionMs: Long) {
        if (_sessionStatus.value != EunoiaSessionStatus.CONNECTED) return

        val myPos = playerManager.positionMs.value
        val diff = Math.abs(myPos - partnerPositionMs)
        if (diff > 2000L) {
            Log.d("EunoiaSync", "Playback drift detected ($diff ms > 2000ms). Resyncing position to $partnerPositionMs")
            playerManager.seekTo(partnerPositionMs)
        }
    }

    private fun showInviteNotification(from: String) {
        runCatching {
            val channelId = "eunoia_invites"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Eunoia Session Invites",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for incoming Eunoia listening invites"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                999,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🎧 Eunoia Session Invite")
                .setContentText("$from wants to listen together with you!")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .build()

            notificationManager.notify(8888, notification)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: EunoiaSessionManager? = null

        fun getInstance(context: Context): EunoiaSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EunoiaSessionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
