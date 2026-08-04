package app.spasiba.nativeapp2.data.engine

import android.util.Log
import app.spasiba.nativeapp2.data.model.Song
import app.spasiba.nativeapp2.player.NativePlayerManager
import org.json.JSONObject
import kotlin.math.abs

class EunoiaSyncController(
    private val playerManager: NativePlayerManager,
    private val socketClient: EunoiaSocketClient
) {
    private val TAG = "EunoiaSyncController"
    private var isApplyingRemoteCommand = false

    companion object {
        private const val DRIFT_IGNORE_MS = 2000L     // +-2 sec error acceptance threshold
        private const val DRIFT_SMOOTH_MS = 4000L     // 2s - 4s: adaptive speed rate adjustment
        private const val SPEED_CATCH_UP = 1.025f    // 2.5% faster to catch up smoothly
        private const val SPEED_SLOW_DOWN = 0.975f   // 2.5% slower to let remote catch up
    }

    /**
     * Called when a playback action happens locally (Play, Pause, Seek, Next, Previous)
     */
    fun emitLocalPlaybackCommand(action: String, extraPayload: JSONObject = JSONObject()) {
        if (isApplyingRemoteCommand) return // Avoid echo loops

        val song = playerManager.currentSong.value
        val payload = JSONObject().apply {
            put("action", action)
            put("songId", song?.id ?: "")
            put("positionMs", playerManager.positionMs.value)
            put("isPlaying", playerManager.isPlaying.value)
            put("timestamp", System.currentTimeMillis())

            val keys = extraPayload.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                put(k, extraPayload.get(k))
            }
        }

        socketClient.sendEvent(action, payload)
        Log.d(TAG, "Emitted local playback command '$action'")
    }

    /**
     * Called when a socket event arrives from partner
     */
    fun handleRemoteSocketEvent(event: EunoiaSocketEvent) {
        val payload = event.payload
        val action = event.eventType

        Log.d(TAG, "Handling remote socket event '$action' from ${event.senderDeviceId}")

        isApplyingRemoteCommand = true
        try {
            when (action) {
                "play" -> {
                    if (!playerManager.isPlaying.value) {
                        playerManager.exoPlayer.play()
                    }
                    syncPositionWithDriftCorrection(payload, event.clientSentAt, forceSeek = false)
                }

                "pause" -> {
                    if (playerManager.isPlaying.value) {
                        playerManager.exoPlayer.pause()
                    }
                    playerManager.setPlaybackSpeed(1.0f)
                    syncPositionWithDriftCorrection(payload, event.clientSentAt, forceSeek = false)
                }

                "seek" -> {
                    val remotePosMs = payload.optLong("positionMs", -1L)
                    if (remotePosMs >= 0) {
                        playerManager.setPlaybackSpeed(1.0f)
                        playerManager.seekTo(remotePosMs)
                    }
                }

                "next" -> {
                    playerManager.playNext()
                }

                "previous" -> {
                    playerManager.playPrevious()
                }

                "trackChanged" -> {
                    val songId = payload.optString("songId")
                    val songTitle = payload.optString("title")
                    val songAuthor = payload.optString("author")
                    val songThumb = payload.optString("thumbnail")
                    val duration = payload.optInt("durationSeconds", 180)

                    if (songId.isNotBlank() && playerManager.currentSong.value?.id != songId) {
                        val newSong = Song(
                            id = songId,
                            title = songTitle.ifBlank { "Track" },
                            author = songAuthor.ifBlank { "Artist" },
                            thumbnail = songThumb.ifBlank { "https://i.ytimg.com/vi/$songId/hqdefault.jpg" },
                            durationSeconds = duration
                        )
                        playerManager.playSong(newSong)
                    }
                }

                "heartbeat" -> {
                    syncPositionWithDriftCorrection(payload, event.clientSentAt, forceSeek = false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing remote event '$action': ${e.message}")
        } finally {
            isApplyingRemoteCommand = false
        }
    }

    private fun syncPositionWithDriftCorrection(
        payload: JSONObject,
        clientSentAt: Long,
        forceSeek: Boolean = false
    ) {
        val remotePosMs = payload.optLong("positionMs", -1L)
        if (remotePosMs < 0) return

        val networkDelayMs = (System.currentTimeMillis() - clientSentAt).coerceIn(0L, 2000L)
        val isRemotePlaying = payload.optBoolean("isPlaying", playerManager.isPlaying.value)

        val correctedPositionMs = if (isRemotePlaying) {
            remotePosMs + networkDelayMs
        } else {
            remotePosMs
        }

        val myPosMs = playerManager.positionMs.value
        val diffMs = correctedPositionMs - myPosMs
        val absDriftMs = abs(diffMs)

        // +-2 sec error acceptance threshold: do NOT seek or adjust if drift is <= 2000ms
        if (absDriftMs <= DRIFT_IGNORE_MS) {
            playerManager.setPlaybackSpeed(1.0f)
            return
        }

        if (forceSeek || absDriftMs > DRIFT_SMOOTH_MS) {
            Log.d(TAG, "Hard seek required ($absDriftMs ms drift > $DRIFT_SMOOTH_MS ms threshold). Seeking to $correctedPositionMs ms")
            playerManager.setPlaybackSpeed(1.0f)
            playerManager.seekTo(correctedPositionMs)
        } else {
            // Adaptive Playback Speed Rate Smoothing for 2000ms - 4000ms drift
            if (diffMs > 0) {
                Log.d(TAG, "Follower behind host by $diffMs ms: boosting speed to ${SPEED_CATCH_UP}x")
                playerManager.setPlaybackSpeed(SPEED_CATCH_UP)
            } else {
                Log.d(TAG, "Follower ahead of host by ${abs(diffMs)} ms: slowing speed to ${SPEED_SLOW_DOWN}x")
                playerManager.setPlaybackSpeed(SPEED_SLOW_DOWN)
            }
        }
    }
}
