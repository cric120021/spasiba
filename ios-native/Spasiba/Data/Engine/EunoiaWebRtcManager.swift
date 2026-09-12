import Foundation

public protocol EunoiaWebRtcListener: AnyObject {
    func onDataChannelMessage(message: String)
    func onDataChannelStateChanged(isOpen: Bool)
}

public final class EunoiaWebRtcManager {
    private let tag = "EunoiaWebRtcManager"
    public weak var listener: EunoiaWebRtcListener?
    public private(set) var isConnected: Bool = false
    private var isInitiator: Bool = false
    private var localDescription: String?
    private var remoteDescription: String?
    private var iceCandidates: [[String: Any]] = []

    public init() {
        print("[\(tag)] EunoiaWebRtcManager initialized.")
    }

    public func setListener(listener: EunoiaWebRtcListener) {
        self.listener = listener
    }

    public func createPeerConnection(onIceCandidateGenerated: @escaping ([String: Any]) -> Void) {
        print("[\(tag)] Creating PeerConnection with STUN servers...")
        // Simulated STUN candidate generation for p2p discovery
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { [weak self] in
            guard let self = self else { return }
            let candidate: [String: Any] = [
                "type": "ice-candidate",
                "sdpMid": "0",
                "sdpMLineIndex": 0,
                "candidate": "candidate:1 1 UDP 2122260223 127.0.0.1 50000 typ host"
            ]
            self.iceCandidates.append(candidate)
            onIceCandidateGenerated(candidate)
        }
    }

    public func initiateDataChannel(onOfferCreated: @escaping ([String: Any]) -> Void) {
        self.isInitiator = true
        print("[\(tag)] Initiating DataChannel 'EunoiaSync'...")
        let mockSdp = "v=0\r\no=SpasibaIOS 12345 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=application 9 DTLS/SCTP 5000\r\nc=IN IP4 127.0.0.1\r\na=sctpmap:5000 webrtc-datachannel 1024\r\n"
        self.localDescription = mockSdp

        let offerJson: [String: Any] = [
            "type": "offer",
            "sdp": mockSdp
        ]
        onOfferCreated(offerJson)
    }

    public func handleRemoteOffer(offerSdp: String, onAnswerCreated: @escaping ([String: Any]) -> Void) {
        self.isInitiator = false
        self.remoteDescription = offerSdp
        print("[\(tag)] Handling remote offer SDP...")

        let mockAnswerSdp = "v=0\r\no=SpasibaIOSAnswer 12345 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=application 9 DTLS/SCTP 5000\r\nc=IN IP4 127.0.0.1\r\na=sctpmap:5000 webrtc-datachannel 1024\r\n"
        self.localDescription = mockAnswerSdp

        let answerJson: [String: Any] = [
            "type": "answer",
            "sdp": mockAnswerSdp
        ]
        onAnswerCreated(answerJson)

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
            guard let self = self else { return }
            self.isConnected = true
            self.listener?.onDataChannelStateChanged(isOpen: true)
        }
    }

    public func handleRemoteAnswer(answerSdp: String) {
        self.remoteDescription = answerSdp
        print("[\(tag)] Handling remote answer SDP...")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
            guard let self = self else { return }
            self.isConnected = true
            self.listener?.onDataChannelStateChanged(isOpen: true)
        }
    }

    public func addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidateSdp: String) {
        print("[\(tag)] Adding remote ICE candidate: \(sdpMid), \(candidateSdp)")
    }

    public func sendP2pMessage(messageText: String) -> Bool {
        if isConnected {
            print("[\(tag)] Sent P2P message: \(messageText)")
            return true
        }
        return false
    }

    public func close() {
        print("[\(tag)] Closing WebRTC PeerConnection & DataChannel")
        self.isConnected = false
        self.listener?.onDataChannelStateChanged(isOpen: false)
    }
}
