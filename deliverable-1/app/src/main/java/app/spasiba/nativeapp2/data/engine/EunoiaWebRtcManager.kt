package app.spasiba.nativeapp2.data.engine

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.webrtc.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

interface EunoiaWebRtcListener {
    fun onDataChannelMessage(message: String)
    fun onDataChannelStateChanged(isOpen: Boolean)
}

class EunoiaWebRtcManager(private val context: Context) {
    private val TAG = "EunoiaWebRtcManager"

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var listener: EunoiaWebRtcListener? = null

    private var isInitiator = false
    var isConnected = false
        private set

    init {
        initPeerConnectionFactory()
    }

    fun setListener(listener: EunoiaWebRtcListener) {
        this.listener = listener
    }

    private fun initPeerConnectionFactory() {
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)

            factory = PeerConnectionFactory.builder()
                .createPeerConnectionFactory()
            Log.d(TAG, "WebRTC PeerConnectionFactory initialized successfully.")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed initializing WebRTC PeerConnectionFactory: ${e.message}")
            factory = null
        }
    }

    fun createPeerConnection(onIceCandidateGenerated: (JSONObject) -> Unit) {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val candidateJson = JSONObject().apply {
                    put("type", "ice-candidate")
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("candidate", candidate.sdp)
                }
                onIceCandidateGenerated(candidateJson)
            }

            override fun onDataChannel(dc: DataChannel) {
                Log.d(TAG, "Incoming DataChannel received from remote peer: ${dc.label()}")
                bindDataChannel(dc)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE Connection State: $state")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })
    }

    fun initiateDataChannel(onOfferCreated: (JSONObject) -> Unit) {
        isInitiator = true
        val dcInit = DataChannel.Init().apply {
            ordered = true
        }
        val dc = peerConnection?.createDataChannel("EunoiaSync", dcInit)
        if (dc != null) {
            bindDataChannel(dc)
        }

        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                val offerJson = JSONObject().apply {
                    put("type", "offer")
                    put("sdp", desc.description)
                }
                onOfferCreated(offerJson)
            }
        }, constraints)
    }

    fun handleRemoteOffer(offerSdp: String, onAnswerCreated: (JSONObject) -> Unit) {
        isInitiator = false
        val desc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                val constraints = MediaConstraints()
                peerConnection?.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(desc: SessionDescription) {
                        peerConnection?.setLocalDescription(SimpleSdpObserver(), desc)
                        val answerJson = JSONObject().apply {
                            put("type", "answer")
                            put("sdp", desc.description)
                        }
                        onAnswerCreated(answerJson)
                    }
                }, constraints)
            }
        }, desc)
    }

    fun handleRemoteAnswer(answerSdp: String) {
        val desc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), desc)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidateSdp: String) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateSdp)
        peerConnection?.addIceCandidate(candidate)
    }

    private fun bindDataChannel(dc: DataChannel) {
        this.dataChannel = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                val state = dc.state()
                Log.d(TAG, "DataChannel State Changed -> $state")
                isConnected = (state == DataChannel.State.OPEN)
                listener?.onDataChannelStateChanged(isConnected)
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = buffer.data
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                val text = String(bytes, StandardCharsets.UTF_8)
                listener?.onDataChannelMessage(text)
            }
        })
    }

    fun sendP2pMessage(messageText: String): Boolean {
        val dc = dataChannel
        if (dc != null && dc.state() == DataChannel.State.OPEN) {
            val bytes = messageText.toByteArray(StandardCharsets.UTF_8)
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
            return dc.send(buffer)
        }
        return false
    }

    fun close() {
        dataChannel?.close()
        dataChannel = null
        peerConnection?.close()
        peerConnection = null
        isConnected = false
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(reason: String?) { Log.e("WebRtcObserver", "onCreateFailure: $reason") }
        override fun onSetFailure(reason: String?) { Log.e("WebRtcObserver", "onSetFailure: $reason") }
    }
}
