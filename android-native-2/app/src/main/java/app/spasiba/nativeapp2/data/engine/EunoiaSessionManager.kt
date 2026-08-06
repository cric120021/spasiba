package app.spasiba.nativeapp2.data.engine

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import app.spasiba.nativeapp2.player.NativePlayerManager
import app.spasiba.nativeapp2.service.SpasibaFcmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

enum class EunoiaSessionStatus {
    IDLE,
    INVITE_SENT,
    INVITE_RECEIVED,
    CONNECTED,
    REJECTED_BUSY
}

data class EunoiaChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val isPartner: Boolean,
    val messageText: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class FlyingReaction(
    val id: String,
    val emoji: String,
    val startXRatio: Float,
    val isPartner: Boolean = false,
    val durationMs: Long = 2500L
)

class EunoiaSessionManager private constructor(private val context: Context) : EunoiaSocketEventListener {

    companion object {
        const val API_BASE_URL = "https://spasiba.vercel.app"

        @Volatile
        private var INSTANCE: EunoiaSessionManager? = null

        fun getInstance(context: Context): EunoiaSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EunoiaSessionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient()
    private val fcmManager = FcmTokenManager.getInstance(context)
    val socketClient = EunoiaSocketClient()
    val webrtcManager = EunoiaWebRtcManager(context)

    private val _sessionStatus = MutableStateFlow(EunoiaSessionStatus.IDLE)
    val sessionStatus: StateFlow<EunoiaSessionStatus> = _sessionStatus.asStateFlow()

    private val _myDeviceId = MutableStateFlow("SPASIBA-000000")
    val myDeviceId: StateFlow<String> = _myDeviceId.asStateFlow()

    val activeScreen = MutableStateFlow<Int>(0)
    val activeChatFriendId = MutableStateFlow<String?>(null)

    private val _partnerDeviceId = MutableStateFlow<String?>(null)
    val partnerDeviceId: StateFlow<String?> = _partnerDeviceId.asStateFlow()

    private val _partnerName = MutableStateFlow<String?>(null)
    val partnerName: StateFlow<String?> = _partnerName.asStateFlow()

    private val _partnerPhotoUri = MutableStateFlow<String?>(null)
    val partnerPhotoUri: StateFlow<String?> = _partnerPhotoUri.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    private val _pendingInviteSender = MutableStateFlow<String?>(null)
    val pendingInviteSender: StateFlow<String?> = _pendingInviteSender.asStateFlow()

    private val _sessionMessages = MutableStateFlow<List<EunoiaChatMessage>>(emptyList())
    val sessionMessages: StateFlow<List<EunoiaChatMessage>> = _sessionMessages.asStateFlow()

    private val _flyingReactions = MutableStateFlow<List<FlyingReaction>>(emptyList())
    val flyingReactions: StateFlow<List<FlyingReaction>> = _flyingReactions.asStateFlow()

    private val _isLeader = MutableStateFlow(true)
    val isLeader: StateFlow<Boolean> = _isLeader.asStateFlow()

    private val _leaderDeviceId = MutableStateFlow<String?>(null)
    val leaderDeviceId: StateFlow<String?> = _leaderDeviceId.asStateFlow()

    private var syncController: EunoiaSyncController? = null

    init {
        scope.launch {
            fcmManager.deviceId.collect { id ->
                _myDeviceId.value = id
            }
        }

        socketClient.setEventListener(this)
        setupWebRtcListener()
        startHeartbeatTicker()
        startSignalPolling()
    }

    private fun dismissInviteNotification() {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(SpasibaFcmService.NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e("EunoiaSessionManager", "Failed dismissing notification: ${e.message}")
        }
    }

    fun claimLeadership() {
        setLeadership(true)
    }

    fun endSession() {
        val partnerId = _partnerDeviceId.value
        if (!partnerId.isNullOrBlank()) {
            val payload = JSONObject().apply {
                put("type", "END_SESSION")
                put("senderId", _myDeviceId.value)
            }
            val p2pOk = webrtcManager.sendP2pMessage(payload.toString())
            if (!p2pOk) {
                sendHttpSignal(partnerId, "END_SESSION", payload)
            }
        }
        webrtcManager.close()
        _sessionStatus.value = EunoiaSessionStatus.IDLE
        _partnerDeviceId.value = null
        _partnerName.value = null
        _pendingInviteSender.value = null
        _sessionMessages.value = emptyList()
        _flyingReactions.value = emptyList()
        dismissInviteNotification()
        Log.d("EunoiaSessionManager", "Session ended & chat cleared")
    }

    fun setLeadership(leader: Boolean) {
        _isLeader.value = leader
        val partnerId = _partnerDeviceId.value
        if (leader) {
            _leaderDeviceId.value = _myDeviceId.value
        } else if (!partnerId.isNullOrBlank()) {
            _leaderDeviceId.value = partnerId
        }

        if (!partnerId.isNullOrBlank()) {
            val payload = JSONObject().apply {
                put("type", "LEADERSHIP_TRANSFER")
                put("senderId", if (leader) _myDeviceId.value else partnerId)
                put("isLeader", leader)
            }

            val p2pOk = webrtcManager.sendP2pMessage(payload.toString())
            if (!p2pOk) {
                sendHttpSignal(partnerId, "LEADERSHIP_TRANSFER", payload)
            }
        }
    }

    private fun setupWebRtcListener() {
        webrtcManager.setListener(object : EunoiaWebRtcListener {
            override fun onDataChannelMessage(message: String) {
                scope.launch(Dispatchers.Main) {
                    try {
                        val json = JSONObject(message)
                        val type = json.optString("type")
                        val sender = json.optString("senderId", _partnerDeviceId.value ?: "Partner")

                        when (type) {
                            "LEADERSHIP_TRANSFER" -> {
                                _isLeader.value = false
                                _leaderDeviceId.value = sender
                                Log.d("EunoiaSessionManager", "[WebRTC P2P Direct] Leadership transferred to partner: $sender")
                            }

                            "chatMessage" -> {
                                val text = json.optString("text")
                                val ts = json.optLong("timestampMs", System.currentTimeMillis())
                                if (text.isNotBlank()) {
                                    val newMsg = EunoiaChatMessage(
                                        id = UUID.randomUUID().toString(),
                                        senderId = sender,
                                        senderName = sender,
                                        isPartner = true,
                                        messageText = text,
                                        timestampMs = ts
                                    )
                                    _sessionMessages.value = _sessionMessages.value + newMsg
                                }
                            }

                            "reaction" -> {
                                val emoji = json.optString("emoji")
                                if (emoji.isNotBlank()) {
                                    triggerFlyingReactionLocally(emoji, isPartner = true)
                                }
                            }

                            else -> {
                                val socketEvt = EunoiaSocketEvent(
                                    eventId = json.optString("eventId", UUID.randomUUID().toString()),
                                    sessionId = _sessionId.value ?: "",
                                    senderDeviceId = sender,
                                    eventType = type,
                                    sequence = json.optInt("sequence", 0),
                                    clientSentAt = json.optLong("clientSentAt", System.currentTimeMillis()),
                                    serverTime = System.currentTimeMillis(),
                                    payload = json
                                )
                                syncController?.handleRemoteSocketEvent(socketEvt)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("EunoiaSessionManager", "Error handling WebRTC DataChannel message: ${e.message}")
                    }
                }
            }

            override fun onDataChannelStateChanged(isOpen: Boolean) {
                Log.d("EunoiaSessionManager", "WebRTC P2P DataChannel state: isOpen=$isOpen")
            }
        })
    }

    private fun startSignalPolling() {
        scope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val myId = _myDeviceId.value.trim().uppercase()
                    if (myId.isNotBlank()) {
                        val req = Request.Builder()
                            .url("$API_BASE_URL/api/eunoia-signal?deviceId=$myId")
                            .get()
                            .build()

                        httpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val bodyText = resp.body?.string() ?: ""
                                if (bodyText.isNotBlank()) {
                                    val resJson = JSONObject(bodyText)
                                    val signals = resJson.optJSONArray("signals")
                                    if (signals != null) {
                                        for (i in 0 until signals.length()) {
                                            val sigObj = signals.getJSONObject(i)
                                            val from = sigObj.optString("fromId").uppercase()
                                            if (from == myId) continue

                                            val payload = sigObj.optJSONObject("payload") ?: JSONObject()
                                            val type = payload.optString("type")

                                            scope.launch(Dispatchers.Main) {
                                                when (type) {
                                                    "LEADERSHIP_TRANSFER" -> {
                                                        _isLeader.value = false
                                                        _leaderDeviceId.value = from
                                                    }
                                                    "EUNOIA_INVITE" -> {
                                                        val sessId = payload.optString("sessionId")
                                                        val inviterName = payload.optString("inviterName").takeIf { it.isNotBlank() }
                                                            ?: payload.optString("senderName").takeIf { it.isNotBlank() }
                                                        val photoB64 = payload.optString("photoBase64").takeIf { it.isNotBlank() }
                                                         if (photoB64 != null) {
                                                             _partnerPhotoUri.value = "data:image/jpeg;base64,$photoB64"
                                                         } else {
                                                             val cachedPhoto = EunoiaFriendManager.getInstance(context).getFriends().find { it.friendId.equals(from, ignoreCase = true) }?.photoUri
                                                             if (cachedPhoto != null) _partnerPhotoUri.value = cachedPhoto
                                                         }
                                                         if (inviterName != null) {
                                                             _partnerName.value = inviterName
                                                         }
                                                         scope.launch(Dispatchers.IO) {
                                                             val localPhotoUri = photoB64?.let { saveBase64ToFileUri(it, "partner_$from") }
                                                             withContext(Dispatchers.Main) {
                                                                 if (localPhotoUri != null) _partnerPhotoUri.value = localPhotoUri
                                                                 if (inviterName != null) {
                                                                     EunoiaFriendManager.getInstance(context).addOrSendFriendRequest(from, inviterName, isOutgoing = false, photoUri = localPhotoUri ?: _partnerPhotoUri.value)
                                                                 }
                                                             }
                                                         }
                                                         fetchAndResolvePartnerProfile(from)
                                                         receiveSimulatedInvite(from, sessId)
                                                     }
                                                     "ACCEPT" -> {
                                                         val sessId = payload.optString("sessionId")
                                                         dismissInviteNotification()
                                                         _pendingInviteSender.value = null
                                                         _partnerDeviceId.value = from
                                                         val senderName = payload.optString("senderName").takeIf { it.isNotBlank() }
                                                             ?: payload.optString("inviterName").takeIf { it.isNotBlank() }
                                                         val photoB64 = payload.optString("photoBase64").takeIf { it.isNotBlank() }
                                                         if (photoB64 != null) {
                                                             _partnerPhotoUri.value = "data:image/jpeg;base64,$photoB64"
                                                         } else {
                                                             val cachedPhoto = EunoiaFriendManager.getInstance(context).getFriends().find { it.friendId.equals(from, ignoreCase = true) }?.photoUri
                                                             if (cachedPhoto != null) _partnerPhotoUri.value = cachedPhoto
                                                         }
                                                        if (senderName != null) {
                                                            _partnerName.value = senderName
                                                        }
                                                        scope.launch(Dispatchers.IO) {
                                                            val localPhotoUri = photoB64?.let { saveBase64ToFileUri(it, "partner_$from") }
                                                            withContext(Dispatchers.Main) {
                                                                if (localPhotoUri != null) _partnerPhotoUri.value = localPhotoUri
                                                                if (senderName != null) {
                                                                    EunoiaFriendManager.getInstance(context).addOrSendFriendRequest(from, senderName, isOutgoing = false, photoUri = localPhotoUri ?: _partnerPhotoUri.value)
                                                                }
                                                            }
                                                        }
                                                        fetchAndResolvePartnerProfile(from)
                                                        _sessionStatus.value = EunoiaSessionStatus.CONNECTED
                                                        if (sessId.isNotBlank()) {
                                                            _sessionId.value = sessId
                                                        }
                                                        initiateWebRtcHandshake(from)
                                                        broadcastProfileUpdateSignal()
                                                    }
                                                    "REJECT" -> {
                                                        dismissInviteNotification()
                                                        _pendingInviteSender.value = null
                                                        _sessionStatus.value = EunoiaSessionStatus.REJECTED_BUSY
                                                    }
                                                    "WEBRTC_OFFER" -> {
                                                        val offerSdp = payload.optString("sdp")
                                                        webrtcManager.createPeerConnection { candidateJson ->
                                                            sendHttpSignal(from, "WEBRTC_ICE", candidateJson)
                                                        }
                                                        webrtcManager.handleRemoteOffer(offerSdp) { answerJson ->
                                                            sendHttpSignal(from, "WEBRTC_ANSWER", answerJson)
                                                        }
                                                    }
                                                    "WEBRTC_ANSWER" -> {
                                                        val answerSdp = payload.optString("sdp")
                                                        webrtcManager.handleRemoteAnswer(answerSdp)
                                                    }
                                                    "WEBRTC_ICE" -> {
                                                        val mid = payload.optString("sdpMid")
                                                        val mLine = payload.optInt("sdpMLineIndex", 0)
                                                        val candidate = payload.optString("candidate")
                                                        webrtcManager.addRemoteIceCandidate(mid, mLine, candidate)
                                                    }
                                                    "chatMessage" -> {
                                                        val text = payload.optString("text")
                                                        val ts = payload.optLong("timestampMs", System.currentTimeMillis())
                                                        val newMsg = EunoiaChatMessage(
                                                            id = UUID.randomUUID().toString(),
                                                            senderId = from,
                                                            senderName = from,
                                                            isPartner = true,
                                                            messageText = text,
                                                            timestampMs = ts
                                                        )
                                                        _sessionMessages.value = _sessionMessages.value + newMsg
                                                    }
                                                    "reaction" -> {
                                                        val emoji = payload.optString("emoji")
                                                        if (emoji.isNotBlank()) {
                                                            triggerFlyingReactionLocally(emoji, isPartner = true)
                                                        }
                                                    }
                                                    "FRIEND_REQUEST" -> {
                                                        val senderName = payload.optString("senderName").takeIf { it.isNotBlank() && !it.startsWith("SPASIBA-") } ?: "Friend (${from.takeLast(6)})"
                                                        val photoB64 = payload.optString("photoBase64").takeIf { it.isNotBlank() }
                                                        val localPhotoUri = photoB64?.let { saveBase64ToFileUri(it, "friend_$from") }

                                                        Log.d("EunoiaSessionManager", "FRIEND_REQUEST signal received from $from ($senderName)!")
                                                        EunoiaFriendManager.getInstance(context).addOrSendFriendRequest(from, senderName, isOutgoing = false, photoUri = localPhotoUri)
                                                        EunoiaNotificationHelper.showFriendRequestNotification(context, from, senderName)
                                                        app.spasiba.nativeapp2.ui.screens.NotificationState.updateCount(context)
                                                        friendEventFlow.value = System.currentTimeMillis()
                                                    }
                                                    "FRIEND_ACCEPT" -> {
                                                        val senderName = payload.optString("senderName").takeIf { it.isNotBlank() && !it.startsWith("SPASIBA-") } ?: "Friend (${from.takeLast(6)})"
                                                        val photoB64 = payload.optString("photoBase64").takeIf { it.isNotBlank() }
                                                        val localPhotoUri = photoB64?.let { saveBase64ToFileUri(it, "friend_$from") }

                                                        Log.d("EunoiaSessionManager", "FRIEND_ACCEPT signal received from $from ($senderName)!")
                                                        EunoiaFriendManager.getInstance(context).acceptFriendRequest(from, newName = senderName, photoUri = localPhotoUri)
                                                        EunoiaNotificationHelper.showFriendAcceptedNotification(context, from, senderName)
                                                        Toast.makeText(context, "$senderName accepted your friend request!", Toast.LENGTH_LONG).show()
                                                        friendEventFlow.value = System.currentTimeMillis()
                                                    }
                                                    "PROFILE_UPDATE" -> {
                                                        val senderName = payload.optString("senderName").takeIf { it.isNotBlank() && !it.startsWith("SPASIBA-") }
                                                        val photoB64 = payload.optString("photoBase64").takeIf { it.isNotBlank() }
                                                        val localPhotoUri = photoB64?.let { saveBase64ToFileUri(it, "friend_$from") }

                                                        if (senderName != null) {
                                                            EunoiaFriendManager.getInstance(context).updateFriendName(from, senderName)
                                                            if (_partnerDeviceId.value.equals(from, ignoreCase = true)) {
                                                                _partnerName.value = senderName
                                                            }
                                                        }
                                                        if (localPhotoUri != null) {
                                                            EunoiaFriendManager.getInstance(context).updateFriendPhoto(from, localPhotoUri)
                                                            if (_partnerDeviceId.value.equals(from, ignoreCase = true)) {
                                                                _partnerPhotoUri.value = localPhotoUri
                                                            }
                                                        }
                                                        friendEventFlow.value = System.currentTimeMillis()
                                                    }
                                                    "FRIEND_CHAT_MESSAGE" -> {
                                                        val senderName = payload.optString("senderName", from)
                                                        val text = payload.optString("text")
                                                        if (text.isNotBlank()) {
                                                            EunoiaFriendManager.getInstance(context).addChatMessage(from, from, senderName, text, isPartner = true)
                                                            friendEventFlow.value = System.currentTimeMillis()

                                                            // Only post notification if user is NOT currently in this friend's chat screen window
                                                            val isUserInThisFriendChatWindow = (activeScreen.value == 7 && activeChatFriendId.value?.trim()?.uppercase() == from.trim().uppercase())
                                                            if (!isUserInThisFriendChatWindow) {
                                                                EunoiaNotificationHelper.showFriendChatMessageNotification(context, from, senderName, text)
                                                            }
                                                        }
                                                    }
                                                    "FRIEND_CHAT_DELETE" -> {
                                                        EunoiaFriendManager.getInstance(context).clearChatHistory(from)
                                                        friendEventFlow.value = System.currentTimeMillis()
                                                    }
                                                     "END_SESSION" -> {
                                                         dismissInviteNotification()
                                                         _partnerDeviceId.value = null
                                                         _partnerName.value = null
                                                         _sessionStatus.value = EunoiaSessionStatus.IDLE
                                                         _sessionMessages.value = emptyList()
                                                         webrtcManager.close()
                                                         Toast.makeText(context, "Partner ended the session", Toast.LENGTH_SHORT).show()
                                                     }
                                                    "FRIEND_REMOVE" -> {
                                                        EunoiaFriendManager.getInstance(context).removeFriend(from)
                                                        friendEventFlow.value = System.currentTimeMillis()
                                                    }
                                                    else -> {
                                                        val socketEvt = EunoiaSocketEvent(
                                                            eventId = UUID.randomUUID().toString(),
                                                            sessionId = _sessionId.value ?: "",
                                                            senderDeviceId = from,
                                                            eventType = type,
                                                            sequence = payload.optInt("sequence", 0),
                                                            clientSentAt = payload.optLong("clientSentAt", System.currentTimeMillis()),
                                                            serverTime = System.currentTimeMillis(),
                                                            payload = payload
                                                        )
                                                        syncController?.handleRemoteSocketEvent(socketEvt)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("EunoiaSessionManager", "Signal polling exception: ${e.message}")
                }
                delay(1500)
            }
        }
    }

    private fun initiateWebRtcHandshake(targetId: String) {
        webrtcManager.createPeerConnection { candidateJson ->
            sendHttpSignal(targetId, "WEBRTC_ICE", candidateJson)
        }
        webrtcManager.initiateDataChannel { offerJson ->
            sendHttpSignal(targetId, "WEBRTC_OFFER", offerJson)
        }
    }

    fun normalizeDeviceId(id: String): String {
        var trimmed = id.trim()
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1).trim()
        }
        if (!trimmed.startsWith("SPASIBA-", ignoreCase = true) && trimmed.length <= 8 && trimmed.isNotBlank()) {
            trimmed = "SPASIBA-$trimmed"
        }
        return trimmed.uppercase()
    }

    private fun sendHttpSignal(targetId: String, type: String, payloadObj: JSONObject) {
        val target = normalizeDeviceId(targetId)
        scope.launch(Dispatchers.IO) {
            try {
                val fullPayload = JSONObject(payloadObj.toString()).apply {
                    put("type", type)
                }
                val jsonBody = JSONObject().apply {
                    put("targetId", target)
                    put("fromId", _myDeviceId.value)
                    put("sessionId", _sessionId.value ?: "")
                    put("payload", fullPayload)
                }.toString()

                val body = jsonBody.toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("$API_BASE_URL/api/eunoia-signal")
                    .post(body)
                    .build()

                httpClient.newCall(req).execute().close()
            } catch (e: Exception) {
                Log.e("EunoiaSessionManager", "Failed sending HTTP signal '$type': ${e.message}")
            }
        }
    }

    fun getMyDisplayName(): String {
        val prefs = context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE)
        val savedName = prefs.getString("username", null)?.takeIf {
            it.isNotBlank() && it != "Spasiba Listener" && !it.startsWith("SPASIBA-") && !it.startsWith("Friend (")
        }
        if (savedName != null) {
            return savedName.trim()
        }
        val roomName = runCatching {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                app.spasiba.nativeapp2.data.db.SpasibaDatabase.getDatabase(context).userProfileDao().getUserProfileSync()?.username
            }
        }.getOrNull()?.takeIf {
            it.isNotBlank() && it != "Spasiba Listener" && !it.startsWith("SPASIBA-") && !it.startsWith("Friend (")
        }
        if (roomName != null) {
            return roomName.trim()
        }
        val model = android.os.Build.MODEL?.takeIf { it.isNotBlank() } ?: "Spasiba Listener"
        return model
    }

    val friendEventFlow = MutableStateFlow<Long>(0L)

    fun sendFriendRequestSignal(targetId: String, friendName: String) {
        val target = normalizeDeviceId(targetId)
        val friendManager = EunoiaFriendManager.getInstance(context)
        friendManager.addOrSendFriendRequest(target, friendName, isOutgoing = true)
        fetchAndResolvePartnerProfile(target)
        val myName = getMyDisplayName()
        val myPhotoUri = context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE).getString("profile_photo_uri", null)
        val myB64 = myPhotoUri?.let { uriToBase64(it) }

        val payload = JSONObject().apply {
            put("type", "FRIEND_REQUEST")
            put("senderId", _myDeviceId.value)
            put("senderName", myName)
            if (!myB64.isNullOrBlank()) {
                put("photoBase64", myB64)
            }
        }
        sendHttpSignal(target, "FRIEND_REQUEST", payload)
        friendEventFlow.value = System.currentTimeMillis()
    }

    fun acceptFriendRequestSignal(targetId: String, friendName: String = "") {
        val target = normalizeDeviceId(targetId)
        val friendManager = EunoiaFriendManager.getInstance(context)
        friendManager.acceptFriendRequest(target)
        val myName = getMyDisplayName()
        val myPhotoUri = context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE).getString("profile_photo_uri", null)
        val myB64 = myPhotoUri?.let { uriToBase64(it) }

        val payload = JSONObject().apply {
            put("type", "FRIEND_ACCEPT")
            put("senderId", _myDeviceId.value)
            put("senderName", myName)
            if (!myB64.isNullOrBlank()) {
                put("photoBase64", myB64)
            }
        }
        sendHttpSignal(target, "FRIEND_ACCEPT", payload)
        friendEventFlow.value = System.currentTimeMillis()
    }

    fun sendFriendChatMessageSignal(targetId: String, text: String) {
        val target = normalizeDeviceId(targetId)
        val friendManager = EunoiaFriendManager.getInstance(context)
        val myName = getMyDisplayName()

        friendManager.addChatMessage(target, _myDeviceId.value, myName, text, isPartner = false)
        val payload = JSONObject().apply {
            put("type", "FRIEND_CHAT_MESSAGE")
            put("senderId", _myDeviceId.value)
            put("senderName", myName)
            put("text", text)
            put("timestampMs", System.currentTimeMillis())
        }
        sendHttpSignal(target, "FRIEND_CHAT_MESSAGE", payload)
        friendEventFlow.value = System.currentTimeMillis()
    }

    fun deleteFriendChatHistorySignal(targetId: String) {
        val target = normalizeDeviceId(targetId)
        val friendManager = EunoiaFriendManager.getInstance(context)
        friendManager.clearChatHistory(target)

        val payload = JSONObject().apply {
            put("type", "FRIEND_CHAT_DELETE")
            put("senderId", _myDeviceId.value)
        }
        sendHttpSignal(target, "FRIEND_CHAT_DELETE", payload)
        friendEventFlow.value = System.currentTimeMillis()
    }

    fun removeFriendSignal(targetId: String) {
        val target = normalizeDeviceId(targetId)
        val friendManager = EunoiaFriendManager.getInstance(context)
        friendManager.removeFriend(target)

        val payload = JSONObject().apply {
            put("type", "FRIEND_REMOVE")
            put("senderId", _myDeviceId.value)
        }
        sendHttpSignal(target, "FRIEND_REMOVE", payload)
        friendEventFlow.value = System.currentTimeMillis()
    }

    fun broadcastProfileUpdateSignal(newName: String? = null, newPhotoUri: String? = null) {
        val myName = newName ?: getMyDisplayName()
        val myPhotoUri = newPhotoUri ?: context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE).getString("profile_photo_uri", null)
        val myB64 = myPhotoUri?.let { uriToBase64(it) }

        val targets = EunoiaFriendManager.getInstance(context).getFriends().map { it.friendId }.toMutableSet()
        val currentPartner = _partnerDeviceId.value
        if (!currentPartner.isNullOrBlank()) {
            targets.add(currentPartner)
        }

        scope.launch(Dispatchers.IO) {
            for (targetId in targets) {
                try {
                    val payload = JSONObject().apply {
                        put("type", "PROFILE_UPDATE")
                        put("senderId", _myDeviceId.value)
                        put("senderName", myName)
                        if (!myB64.isNullOrBlank()) {
                            put("photoBase64", myB64)
                        }
                    }
                    sendHttpSignal(targetId, "PROFILE_UPDATE", payload)
                } catch (e: Exception) {
                    // Ignore single signal errors
                }
            }
        }
    }

    fun attachPlayerManager(playerManager: NativePlayerManager) {
        if (syncController == null) {
            syncController = EunoiaSyncController(playerManager, socketClient)
            playerManager.eunoiaTrackChangeListener = { song ->
                if (_sessionStatus.value == EunoiaSessionStatus.CONNECTED) {
                    emitPlaybackAction("trackChanged", JSONObject().apply {
                        put("songId", song.id)
                        put("title", song.title)
                        put("author", song.author)
                        put("thumbnail", song.thumbnail)
                        put("durationSeconds", song.durationSeconds)
                    })
                }
            }
        }
    }

    private fun saveBase64ToFileUri(base64Str: String, prefix: String): String? {
        return try {
            val pureBase64 = base64Str.substringAfter("base64,")
            val bytes = android.util.Base64.decode(pureBase64, android.util.Base64.DEFAULT)
            val destFile = java.io.File(context.filesDir, "${prefix}_photo.jpg")
            java.io.FileOutputStream(destFile).use { it.write(bytes) }
            android.net.Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun uriToBase64(uriStr: String): String? {
        return try {
            val uri = android.net.Uri.parse(uriStr)
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun fetchAndResolvePartnerProfile(partnerId: String) {
        val cleanId = normalizeDeviceId(partnerId)
        val localFriend = EunoiaFriendManager.getInstance(context).getFriends().find {
            it.friendId.equals(cleanId, ignoreCase = true) || cleanId.contains(it.friendId, ignoreCase = true) || it.friendId.contains(cleanId, ignoreCase = true)
        }
        if (localFriend != null && localFriend.name.isNotBlank() && !localFriend.name.startsWith("SPASIBA-")) {
            _partnerName.value = localFriend.name
        }
        if (localFriend != null && !localFriend.photoUri.isNullOrBlank()) {
            _partnerPhotoUri.value = localFriend.photoUri
        }

        scope.launch(Dispatchers.IO) {
            try {
                var fetchedName: String? = null
                var fetchedPhotoB64: String? = null

                Log.e("EXACT_EUNOIA_DEBUG", "fetchAndResolvePartnerProfile started for partnerId='$partnerId', cleanId='$cleanId'")

                val req1 = Request.Builder().url("$API_BASE_URL/api/user/sync?hardwareId=$cleanId").get().build()
                httpClient.newCall(req1).execute().use { resp ->
                    val bodyStr = resp.body?.string() ?: ""
                    Log.e("EXACT_EUNOIA_DEBUG", "Req1 hardwareId=$cleanId code=${resp.code} body='$bodyStr'")
                    if (resp.isSuccessful && bodyStr.isNotBlank()) {
                        val json = JSONObject(bodyStr)
                        fetchedName = json.optString("username").takeIf { it.isNotBlank() && !it.startsWith("SPASIBA-") }
                        fetchedPhotoB64 = json.optString("photoBase64").takeIf { it.isNotBlank() }
                    }
                }

                if (fetchedName == null) {
                    val req2 = Request.Builder().url("$API_BASE_URL/api/user/sync?visitorId=$cleanId").get().build()
                    httpClient.newCall(req2).execute().use { resp ->
                        val bodyStr = resp.body?.string() ?: ""
                        Log.e("EXACT_EUNOIA_DEBUG", "Req2 visitorId=$cleanId code=${resp.code} body='$bodyStr'")
                        if (resp.isSuccessful && bodyStr.isNotBlank()) {
                            val json = JSONObject(bodyStr)
                            fetchedName = json.optString("username").takeIf { it.isNotBlank() && !it.startsWith("SPASIBA-") }
                            if (fetchedPhotoB64 == null) {
                                fetchedPhotoB64 = json.optString("photoBase64").takeIf { it.isNotBlank() }
                            }
                        }
                    }
                }

                val localPhotoUri = fetchedPhotoB64?.let { saveBase64ToFileUri(it, "partner_$cleanId") }

                Log.e("EXACT_EUNOIA_DEBUG", "Profile fetch final result: fetchedName='$fetchedName', localPhotoUri='$localPhotoUri'")

                if (fetchedName != null) {
                    _partnerName.value = fetchedName
                    EunoiaFriendManager.getInstance(context).updateFriendName(cleanId, fetchedName)
                }
                if (localPhotoUri != null) {
                    _partnerPhotoUri.value = localPhotoUri
                }
                if (fetchedName != null || localPhotoUri != null) {
                    EunoiaFriendManager.getInstance(context).addOrSendFriendRequest(
                        friendId = cleanId,
                        name = fetchedName ?: _partnerName.value ?: "Friend (${cleanId.takeLast(6)})",
                        isOutgoing = false,
                        photoUri = localPhotoUri ?: _partnerPhotoUri.value
                    )
                }
            } catch (e: Exception) {
                Log.d("EunoiaSessionManager", "Cloud partner profile fetch note: ${e.message}")
            }
        }
    }

    fun sendInvite(targetDeviceId: String, customName: String? = null, playerManager: NativePlayerManager? = null) {
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
        val localName = customName?.trim()?.ifBlank { null } ?: EunoiaFriendManager.getInstance(context).getFriends().find { it.friendId.equals(trimmed, ignoreCase = true) }?.name
        _partnerName.value = localName ?: "Friend (${trimmed.takeLast(6)})"

        fetchAndResolvePartnerProfile(trimmed)

        if (playerManager != null) {
            attachPlayerManager(playerManager)
        }

        val currentSong = playerManager?.currentSong?.value
        val positionMs = playerManager?.positionMs?.value ?: 0L
        val isPlaying = playerManager?.isPlaying?.value ?: false
        val myName = getMyDisplayName()

        scope.launch(Dispatchers.IO) {
            try {
                val jsonPayload = JSONObject().apply {
                    put("inviterDeviceId", _myDeviceId.value)
                    put("inviteeDeviceId", trimmed)
                    put("inviterName", myName)
                    put("positionMs", positionMs)
                    put("isPlaying", isPlaying)
                    if (currentSong != null) {
                        put("currentSong", JSONObject().apply {
                            put("id", currentSong.id)
                            put("title", currentSong.title)
                            put("author", currentSong.author)
                            put("thumbnail", currentSong.thumbnail)
                            put("durationSeconds", currentSong.durationSeconds)
                        })
                    }
                }.toString()

                val body = jsonPayload.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$API_BASE_URL/api/eunoia/invite")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyText = resp.body?.string() ?: ""
                        val resJson = JSONObject(bodyText)
                        val createdSessId = resJson.optString("sessionId")
                        if (createdSessId.isNotBlank()) {
                            _sessionId.value = createdSessId
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("EunoiaSessionManager", "Failed sending API invite: ${e.message}")
            }
        }
    }

    fun receiveSimulatedInvite(fromDeviceId: String, inviteSessionId: String? = null) {
        _pendingInviteSender.value = fromDeviceId
        if (!inviteSessionId.isNullOrBlank()) {
            _sessionId.value = inviteSessionId
        }
        _sessionStatus.value = EunoiaSessionStatus.INVITE_RECEIVED
        fetchAndResolvePartnerProfile(fromDeviceId)

        friendEventFlow.value = System.currentTimeMillis()
        try {
            app.spasiba.nativeapp2.ui.screens.NotificationState.updateCount(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun acceptInvite(senderId: String, playerManager: NativePlayerManager, explicitSessionId: String? = null, customName: String? = null) {
        dismissInviteNotification()
        _pendingInviteSender.value = null
        _partnerDeviceId.value = senderId
        val localName = customName?.trim()?.ifBlank { null } ?: EunoiaFriendManager.getInstance(context).getFriends().find { it.friendId.equals(senderId, ignoreCase = true) }?.name
        _partnerName.value = localName ?: "Friend (${senderId.takeLast(6)})"
        _sessionStatus.value = EunoiaSessionStatus.CONNECTED

        fetchAndResolvePartnerProfile(senderId)

        attachPlayerManager(playerManager)

        val activeSessionId = explicitSessionId ?: _sessionId.value ?: "EUNOIA-${UUID.randomUUID().toString().take(8).uppercase()}"
        _sessionId.value = activeSessionId

        initiateWebRtcHandshake(senderId)

        val myName = getMyDisplayName()
        val acceptPayload = JSONObject().apply {
            put("type", "ACCEPT")
            put("sessionId", activeSessionId)
            put("senderId", _myDeviceId.value)
            put("senderName", myName)
        }
        sendHttpSignal(senderId, "ACCEPT", acceptPayload)

        scope.launch(Dispatchers.IO) {
            try {
                val jsonPayload = JSONObject().apply {
                    put("sessionId", activeSessionId)
                    put("deviceId", _myDeviceId.value)
                }.toString()

                val body = jsonPayload.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$API_BASE_URL/api/eunoia/invite/accept")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyText = resp.body?.string() ?: ""
                        val resJson = JSONObject(bodyText)
                        val snapshotObj = resJson.optJSONObject("sessionSnapshot")
                        if (snapshotObj != null) {
                            val currentSongObj = snapshotObj.optJSONObject("currentSong")
                            val isPlaying = snapshotObj.optBoolean("isPlaying", false)
                            val positionMs = snapshotObj.optLong("positionMs", 0L)

                            if (currentSongObj != null) {
                                val songId = currentSongObj.optString("id")
                                val title = currentSongObj.optString("title")
                                val author = currentSongObj.optString("author")
                                val thumb = currentSongObj.optString("thumbnail")
                                val dur = currentSongObj.optInt("durationSeconds", 180)

                                if (songId.isNotBlank()) {
                                    val song = app.spasiba.nativeapp2.data.model.Song(songId, title, author, thumb, dur)
                                    scope.launch(Dispatchers.Main) {
                                        playerManager.playSong(song)
                                        if (positionMs > 0) playerManager.seekTo(positionMs)
                                        if (!isPlaying) playerManager.exoPlayer.pause()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("EunoiaSessionManager", "Failed sending API accept invite: ${e.message}")
            }
        }
    }

    fun dismissInviteLocally() {
        dismissInviteNotification()
        _pendingInviteSender.value = null
        if (_sessionStatus.value == EunoiaSessionStatus.INVITE_RECEIVED) {
            _sessionStatus.value = EunoiaSessionStatus.IDLE
        }
    }

    fun rejectInvite(senderId: String) {
        dismissInviteNotification()
        _pendingInviteSender.value = null
        _sessionStatus.value = EunoiaSessionStatus.REJECTED_BUSY

        val activeSessionId = _sessionId.value ?: ""

        scope.launch(Dispatchers.IO) {
            try {
                val jsonPayload = JSONObject().apply {
                    put("sessionId", activeSessionId)
                    put("deviceId", _myDeviceId.value)
                }.toString()

                val body = jsonPayload.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$API_BASE_URL/api/eunoia/invite/reject")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    Log.d("EunoiaSessionManager", "API reject invite response: ${resp.code}")
                }
            } catch (e: Exception) {
                Log.e("EunoiaSessionManager", "Failed sending API reject invite: ${e.message}")
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val partnerId = _partnerDeviceId.value ?: return

        val ts = System.currentTimeMillis()
        val myMsg = EunoiaChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = _myDeviceId.value,
            senderName = "You",
            isPartner = false,
            messageText = text,
            timestampMs = ts
        )
        _sessionMessages.value = _sessionMessages.value + myMsg

        val payload = JSONObject().apply {
            put("type", "chatMessage")
            put("text", text)
            put("senderId", _myDeviceId.value)
            put("timestampMs", ts)
        }

        val p2pOk = webrtcManager.sendP2pMessage(payload.toString())
        if (!p2pOk) {
            sendHttpSignal(partnerId, "chatMessage", payload)
        }
    }

    fun triggerFlyingReaction(emoji: String) {
        triggerFlyingReactionLocally(emoji, isPartner = false)
        val partnerId = _partnerDeviceId.value ?: return

        val payload = JSONObject().apply {
            put("type", "reaction")
            put("emoji", emoji)
            put("senderId", _myDeviceId.value)
        }

        val p2pOk = webrtcManager.sendP2pMessage(payload.toString())
        if (!p2pOk) {
            sendHttpSignal(partnerId, "reaction", payload)
        }
    }

    fun removeFlyingReaction(id: String) {
        _flyingReactions.value = _flyingReactions.value.filter { it.id != id }
    }

    private fun triggerFlyingReactionLocally(emoji: String, isPartner: Boolean) {
        val startXRatio = if (isPartner) {
            0.05f + (Math.random().toFloat() * 0.38f) // LEFT side fly up for incoming partner reaction
        } else {
            0.55f + (Math.random().toFloat() * 0.38f) // RIGHT side fly up for outgoing self reaction
        }
        val newReaction = FlyingReaction(
            id = UUID.randomUUID().toString(),
            emoji = emoji,
            startXRatio = startXRatio,
            isPartner = isPartner
        )
        _flyingReactions.value = _flyingReactions.value + newReaction
    }

    override fun onEventReceived(event: EunoiaSocketEvent) {
        scope.launch(Dispatchers.Main) {
            when (event.eventType) {
                "LEADERSHIP_TRANSFER" -> {
                    _isLeader.value = false
                    _leaderDeviceId.value = event.senderDeviceId
                }
                "sessionSnapshot" -> {
                    val currentSongObj = event.payload.optJSONObject("currentSong")
                    if (currentSongObj != null) {
                        val songId = currentSongObj.optString("id")
                        val title = currentSongObj.optString("title")
                        val author = currentSongObj.optString("author")
                        val thumb = currentSongObj.optString("thumbnail")
                        val dur = currentSongObj.optInt("durationSeconds", 180)
                        if (songId.isNotBlank()) {
                            val song = app.spasiba.nativeapp2.data.model.Song(songId, title, author, thumb, dur)
                            syncController?.handleRemoteSocketEvent(event.copy(
                                eventType = "trackChanged",
                                payload = JSONObject().apply {
                                    put("songId", songId)
                                    put("title", title)
                                    put("author", author)
                                    put("thumbnail", thumb)
                                    put("durationSeconds", dur)
                                }
                            ))
                        }
                    }
                    syncController?.handleRemoteSocketEvent(event)
                }
                "ACCEPT" -> {
                    dismissInviteNotification()
                    _pendingInviteSender.value = null
                    _partnerDeviceId.value = event.senderDeviceId
                    _partnerName.value = event.senderDeviceId
                    _sessionStatus.value = EunoiaSessionStatus.CONNECTED
                    initiateWebRtcHandshake(event.senderDeviceId)
                }
                "chatMessage" -> {
                    val text = event.payload.optString("text")
                    val ts = event.payload.optLong("timestampMs", System.currentTimeMillis())
                    val newMsg = EunoiaChatMessage(
                        id = UUID.randomUUID().toString(),
                        senderId = event.senderDeviceId,
                        senderName = event.senderDeviceId,
                        isPartner = true,
                        messageText = text,
                        timestampMs = ts
                    )
                    _sessionMessages.value = _sessionMessages.value + newMsg
                }
                "reaction" -> {
                    val emoji = event.payload.optString("emoji")
                    if (emoji.isNotBlank()) {
                        triggerFlyingReactionLocally(emoji, isPartner = true)
                    }
                }
                else -> {
                    syncController?.handleRemoteSocketEvent(event)
                }
            }
        }
    }

    override fun onConnectionStatusChanged(isConnected: Boolean) {
        Log.d("EunoiaSessionManager", "Socket connection status changed: isConnected=$isConnected")
    }

    private fun startHeartbeatTicker() {
        scope.launch(Dispatchers.IO) {
            while (true) {
                if (_sessionStatus.value == EunoiaSessionStatus.CONNECTED) {
                    emitPlaybackAction("heartbeat")
                }
                delay(1000)
            }
        }
    }

    fun emitPlaybackAction(action: String, extraPayload: JSONObject = JSONObject()) {
        val partnerId = _partnerDeviceId.value
        val payload = JSONObject(extraPayload.toString()).apply {
            put("type", action)
            put("senderId", _myDeviceId.value)
            put("clientSentAt", System.currentTimeMillis())
        }

        val p2pOk = webrtcManager.sendP2pMessage(payload.toString())
        if (!p2pOk) {
            syncController?.emitLocalPlaybackCommand(action, extraPayload)
            if (!partnerId.isNullOrBlank()) {
                sendHttpSignal(partnerId, action, payload)
            }
        }
    }
}
