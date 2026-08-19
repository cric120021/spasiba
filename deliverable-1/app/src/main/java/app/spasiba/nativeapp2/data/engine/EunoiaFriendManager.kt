package app.spasiba.nativeapp2.data.engine

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class EunoiaFriendStatus {
    ACCEPTED,
    PENDING_OUTGOING,
    PENDING_INCOMING
}

data class EunoiaFriend(
    val friendId: String,
    val name: String,
    val status: EunoiaFriendStatus = EunoiaFriendStatus.ACCEPTED,
    val addedAtMs: Long = System.currentTimeMillis(),
    val photoUri: String? = null
) {
    val displayName: String
        get() = name.takeIf { it.isNotBlank() && !it.startsWith("SPASIBA-") && !it.startsWith("Friend (") }
            ?: "Friend (${friendId.takeLast(6)})"
}

data class EunoiaFriendChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val friendId: String,
    val senderId: String,
    val senderName: String,
    val isPartner: Boolean,
    val messageText: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val replyToText: String? = null,
    val replyToSender: String? = null,
    val mediaUri: String? = null,
    val isVoiceNote: Boolean = false,
    val voiceDurationSec: Int = 0
)

class EunoiaFriendManager private constructor(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("spasiba_friends_prefs", Context.MODE_PRIVATE)

    fun getFriends(statusFilter: EunoiaFriendStatus? = null): List<EunoiaFriend> {
        val jsonStr = prefs.getString("friends_list_v2", null)
        val list = mutableListOf<EunoiaFriend>()

        if (jsonStr != null) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val statusStr = obj.optString("status", EunoiaFriendStatus.ACCEPTED.name)
                    val status = runCatching { EunoiaFriendStatus.valueOf(statusStr) }.getOrDefault(EunoiaFriendStatus.ACCEPTED)
                    list.add(
                        EunoiaFriend(
                            friendId = obj.getString("friendId"),
                            name = obj.getString("name"),
                            status = status,
                            addedAtMs = obj.optLong("addedAtMs", System.currentTimeMillis()),
                            photoUri = obj.optString("photoUri", "").takeIf { it.isNotBlank() }
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Migrate legacy v1 friends
            val legacyJson = prefs.getString("friends_list", "[]") ?: "[]"
            try {
                val array = JSONArray(legacyJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        EunoiaFriend(
                            friendId = obj.getString("friendId"),
                            name = obj.getString("name"),
                            status = EunoiaFriendStatus.ACCEPTED,
                            addedAtMs = obj.optLong("addedAtMs", System.currentTimeMillis())
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (list.isNotEmpty()) {
                saveFriends(list)
            }
        }

        return if (statusFilter == null) list else list.filter { it.status == statusFilter }
    }

    fun getAcceptedFriends(): List<EunoiaFriend> = getFriends(EunoiaFriendStatus.ACCEPTED)

    fun getPendingRequests(): List<EunoiaFriend> = getFriends().filter {
        it.status == EunoiaFriendStatus.PENDING_OUTGOING || it.status == EunoiaFriendStatus.PENDING_INCOMING
    }

    fun addOrSendFriendRequest(friendId: String, name: String, isOutgoing: Boolean = true, photoUri: String? = null): Boolean {
        val trimmedId = friendId.trim().uppercase()
        if (trimmedId.isBlank()) return false
        val displayId = if (!trimmedId.startsWith("SPASIBA-", ignoreCase = true) && trimmedId.length <= 8) {
            "SPASIBA-$trimmedId"
        } else {
            trimmedId
        }
        val trimmedName = name.trim().ifBlank { "Friend (${displayId.takeLast(6)})" }
        val status = if (isOutgoing) EunoiaFriendStatus.PENDING_OUTGOING else EunoiaFriendStatus.PENDING_INCOMING

        val current = getFriends().toMutableList()
        val existing = current.find { it.friendId.equals(displayId, ignoreCase = true) }
        val finalPhoto = photoUri ?: existing?.photoUri
        current.removeAll { it.friendId.equals(displayId, ignoreCase = true) }
        current.add(0, EunoiaFriend(friendId = displayId, name = trimmedName, status = status, photoUri = finalPhoto))

        if (!isOutgoing) {
            try {
                val userPrefs = context.getSharedPreferences("spasiba_user_prefs", Context.MODE_PRIVATE)
                val currentDismissed = userPrefs.getStringSet("dismissed_notification_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
                if (currentDismissed.remove(displayId)) {
                    userPrefs.edit().putStringSet("dismissed_notification_ids", currentDismissed).apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        saveFriends(current)
        return true
    }

    fun acceptFriendRequest(friendId: String, newName: String? = null, photoUri: String? = null): Boolean {
        val current = getFriends().toMutableList()
        val index = current.indexOfFirst { it.friendId.equals(friendId.trim(), ignoreCase = true) }
        if (index != -1) {
            val existing = current[index]
            val cleanName = newName?.trim()?.takeIf { it.isNotBlank() && !it.startsWith("SPASIBA-") && !it.startsWith("Friend (") }
                ?: existing.name
            val finalPhoto = photoUri ?: existing.photoUri
            current[index] = existing.copy(name = cleanName, status = EunoiaFriendStatus.ACCEPTED, photoUri = finalPhoto)
            saveFriends(current)
            return true
        }
        return false
    }

    fun updateFriendName(friendId: String, newName: String): Boolean {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank() || trimmedName.startsWith("SPASIBA-") || trimmedName.startsWith("Friend (")) return false
        val current = getFriends().toMutableList()
        val index = current.indexOfFirst { it.friendId.equals(friendId.trim(), ignoreCase = true) }
        if (index != -1) {
            val existing = current[index]
            current[index] = existing.copy(name = trimmedName)
            saveFriends(current)
            return true
        }
        return false
    }

    fun updateFriendPhoto(friendId: String, newPhotoUri: String): Boolean {
        if (newPhotoUri.isBlank()) return false
        val current = getFriends().toMutableList()
        val index = current.indexOfFirst { it.friendId.equals(friendId.trim(), ignoreCase = true) }
        if (index != -1) {
            val existing = current[index]
            current[index] = existing.copy(photoUri = newPhotoUri)
            saveFriends(current)
            return true
        }
        return false
    }

    fun removeFriend(friendId: String): Boolean {
        val current = getFriends().toMutableList()
        val removed = current.removeAll { it.friendId.equals(friendId.trim(), ignoreCase = true) }
        if (removed) {
            saveFriends(current)
        }
        return removed
    }

    private fun saveFriends(friends: List<EunoiaFriend>) {
        val array = JSONArray()
        for (f in friends) {
            val obj = JSONObject().apply {
                put("friendId", f.friendId)
                put("name", f.name)
                put("status", f.status.name)
                put("addedAtMs", f.addedAtMs)
                if (!f.photoUri.isNullOrBlank()) {
                    put("photoUri", f.photoUri)
                }
            }
            array.put(obj)
        }
        prefs.edit().putString("friends_list_v2", array.toString()).apply()
        try {
            EunoiaSessionManager.getInstance(context).friendEventFlow.value = System.currentTimeMillis()
            app.spasiba.nativeapp2.ui.screens.NotificationState.updateCount(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- 7-Day Chat History Persistence ---

    fun getChatHistory(friendId: String, days: Int = 7): List<EunoiaFriendChatMessage> {
        val key = "chat_history_${friendId.trim().uppercase()}"
        val jsonStr = prefs.getString(key, "[]") ?: "[]"
        val list = mutableListOf<EunoiaFriendChatMessage>()
        val cutoffMs = System.currentTimeMillis() - (days * 86400 * 1000L)

        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val ts = obj.optLong("timestampMs", System.currentTimeMillis())
                if (ts >= cutoffMs) {
                    list.add(
                        EunoiaFriendChatMessage(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            friendId = obj.getString("friendId"),
                            senderId = obj.getString("senderId"),
                            senderName = obj.optString("senderName", "Friend"),
                            isPartner = obj.optBoolean("isPartner", false),
                            messageText = obj.optString("messageText", ""),
                            timestampMs = ts,
                            replyToText = obj.optString("replyToText").takeIf { it.isNotBlank() },
                            replyToSender = obj.optString("replyToSender").takeIf { it.isNotBlank() },
                            mediaUri = obj.optString("mediaUri").takeIf { it.isNotBlank() },
                            isVoiceNote = obj.optBoolean("isVoiceNote", false),
                            voiceDurationSec = obj.optInt("voiceDurationSec", 0)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedBy { it.timestampMs }
    }

    fun addChatMessage(
        friendId: String,
        senderId: String,
        senderName: String,
        text: String,
        isPartner: Boolean,
        replyToText: String? = null,
        replyToSender: String? = null,
        mediaUri: String? = null,
        isVoiceNote: Boolean = false,
        voiceDurationSec: Int = 0
    ): EunoiaFriendChatMessage {
        val trimmedFriendId = friendId.trim().uppercase()
        val msg = EunoiaFriendChatMessage(
            friendId = trimmedFriendId,
            senderId = senderId,
            senderName = senderName,
            isPartner = isPartner,
            messageText = text,
            replyToText = replyToText,
            replyToSender = replyToSender,
            mediaUri = mediaUri,
            isVoiceNote = isVoiceNote,
            voiceDurationSec = voiceDurationSec
        )

        val history = getChatHistory(trimmedFriendId, days = 7).toMutableList()
        history.add(msg)
        saveChatHistory(trimmedFriendId, history)
        return msg
    }

    fun clearChatHistory(friendId: String): Boolean {
        val key = "chat_history_${friendId.trim().uppercase()}"
        prefs.edit().remove(key).apply()
        return true
    }

    private fun saveChatHistory(friendId: String, messages: List<EunoiaFriendChatMessage>) {
        val key = "chat_history_${friendId.trim().uppercase()}"
        val array = JSONArray()
        for (m in messages) {
            val obj = JSONObject().apply {
                put("id", m.id)
                put("friendId", m.friendId)
                put("senderId", m.senderId)
                put("senderName", m.senderName)
                put("isPartner", m.isPartner)
                put("messageText", m.messageText)
                put("timestampMs", m.timestampMs)
                if (m.replyToText != null) put("replyToText", m.replyToText)
                if (m.replyToSender != null) put("replyToSender", m.replyToSender)
                if (m.mediaUri != null) put("mediaUri", m.mediaUri)
                if (m.isVoiceNote) {
                    put("isVoiceNote", true)
                    put("voiceDurationSec", m.voiceDurationSec)
                }
            }
            array.put(obj)
        }
        prefs.edit().putString(key, array.toString()).apply()
        try {
            EunoiaSessionManager.getInstance(context).friendEventFlow.value = System.currentTimeMillis()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: EunoiaFriendManager? = null

        fun getInstance(context: Context): EunoiaFriendManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EunoiaFriendManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
