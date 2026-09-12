import Foundation

public struct EunoiaSocketEvent {
    public let eventId: String
    public let sessionId: String
    public let senderDeviceId: String
    public let eventType: String
    public let sequence: Int
    public let clientSentAt: Int64
    public let serverTime: Int64
    public let payload: [String: Any]

    public init(
        eventId: String,
        sessionId: String,
        senderDeviceId: String,
        eventType: String,
        sequence: Int,
        clientSentAt: Int64,
        serverTime: Int64,
        payload: [String: Any]
    ) {
        self.eventId = eventId
        self.sessionId = sessionId
        self.senderDeviceId = senderDeviceId
        self.eventType = eventType
        self.sequence = sequence
        self.clientSentAt = clientSentAt
        self.serverTime = serverTime
        self.payload = payload
    }
}

public protocol EunoiaSocketEventListener: AnyObject {
    func onEventReceived(event: EunoiaSocketEvent)
    func onConnectionStatusChanged(isConnected: Bool)
}

public final class EunoiaSocketClient: NSObject, URLSessionWebSocketDelegate {
    public static let DEFAULT_SOCKET_URL = "" // Render dependency removed - Vercel HTTP Signals active
    public static let API_BASE_URL = "https://spasiba.vercel.app"

    private let tag = "EunoiaSocketClient"
    private var socketUrl: String
    private var webSocketTask: URLSessionWebSocketTask?
    private var urlSession: URLSession?
    private var sequenceCounter: Int = 0
    private var currentSessionId: String?
    private var myDeviceId: String?
    public weak var listener: EunoiaSocketEventListener?
    public private(set) var isConnected: Bool = false

    public init(socketUrl: String = DEFAULT_SOCKET_URL) {
        self.socketUrl = socketUrl
        super.init()
    }

    public func updateSocketUrl(newUrl: String) {
        if !newUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            self.socketUrl = newUrl
        }
    }

    public func setEventListener(listener: EunoiaSocketEventListener) {
        self.listener = listener
    }

    public func connect(sessionId: String, deviceId: String) {
        self.currentSessionId = sessionId
        self.myDeviceId = deviceId

        if socketUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            print("[\(tag)] No socket URL configured (Render removed). Using Vercel HTTP signals.")
            return
        }

        if isConnected {
            print("[\(tag)] Already connected to socket for session \(sessionId)")
            return
        }

        guard let url = URL(string: socketUrl) else { return }
        let sessionConfig = URLSessionConfiguration.default
        self.urlSession = URLSession(configuration: sessionConfig, delegate: self, delegateQueue: OperationQueue.main)
        self.webSocketTask = urlSession?.webSocketTask(with: url)
        self.webSocketTask?.resume()

        self.isConnected = true
        self.listener?.onConnectionStatusChanged(isConnected: true)

        // Join room upon connection
        sendEvent(eventType: "joinSession", payload: [
            "sessionId": sessionId,
            "deviceId": deviceId
        ])

        listenForMessages()
    }

    private func listenForMessages() {
        webSocketTask?.receive { [weak self] result in
            guard let self = self else { return }
            switch result {
            case .failure(let error):
                print("[\(self.tag)] WebSocket receive error: \(error). Falling back to HTTP signal relay.")
                self.isConnected = false
                self.listener?.onConnectionStatusChanged(isConnected: false)
            case .success(let message):
                switch message {
                case .string(let text):
                    self.handleIncomingText(text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        self.handleIncomingText(text)
                    }
                @unknown default:
                    break
                }
                if self.isConnected {
                    self.listenForMessages()
                }
            }
        }
    }

    private func handleIncomingText(_ text: String) {
        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return
        }

        let session = currentSessionId ?? ""
        let event = EunoiaSocketEvent(
            eventId: json["eventId"] as? String ?? UUID().uuidString,
            sessionId: json["sessionId"] as? String ?? session,
            senderDeviceId: json["senderDeviceId"] as? String ?? "",
            eventType: json["eventType"] as? String ?? "unknown",
            sequence: json["sequence"] as? Int ?? 0,
            clientSentAt: json["clientSentAt"] as? Int64 ?? Int64(Date().timeIntervalSince1970 * 1000),
            serverTime: json["serverTime"] as? Int64 ?? Int64(Date().timeIntervalSince1970 * 1000),
            payload: json["payload"] as? [String: Any] ?? [:]
        )

        // Ignore self messages
        if event.senderDeviceId != myDeviceId {
            listener?.onEventReceived(event: event)
        }
    }

    public func sendEvent(eventType: String, payload: [String: Any]) {
        guard let session = currentSessionId, let device = myDeviceId else { return }

        var payloadObj = payload
        payloadObj["type"] = eventType
        payloadObj["sessionId"] = session

        sequenceCounter += 1
        let eventDict: [String: Any] = [
            "eventId": UUID().uuidString,
            "sessionId": session,
            "senderDeviceId": device,
            "eventType": eventType,
            "sequence": sequenceCounter,
            "clientSentAt": Int64(Date().timeIntervalSince1970 * 1000),
            "payload": payloadObj
        ]

        if isConnected, let task = webSocketTask,
           let data = try? JSONSerialization.data(withJSONObject: eventDict, options: []),
           let jsonString = String(data: data, encoding: .utf8) {
            task.send(.string(jsonString)) { error in
                if let error = error {
                    print("[\(self.tag)] Failed to send socket message: \(error)")
                }
            }
        } else {
            // Relay via HTTP signal fallback so sync never breaks!
            relayEventViaHttpSignal(fromDeviceId: device, payload: payloadObj)
        }
    }

    private func relayEventViaHttpSignal(fromDeviceId: String, payload: [String: Any]) {
        guard let session = currentSessionId else { return }

        let bodyJson: [String: Any] = [
            "fromId": fromDeviceId,
            "sessionId": session,
            "payload": payload
        ]

        guard let url = URL(string: "\(Self.API_BASE_URL)/api/eunoia-signal"),
              let bodyData = try? JSONSerialization.data(withJSONObject: bodyJson, options: []) else {
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = bodyData

        URLSession.shared.dataTask(with: request) { _, response, error in
            if let httpResp = response as? HTTPURLResponse {
                print("[\(self.tag)] HTTP signal fallback relay status: \(httpResp.statusCode)")
            } else if let error = error {
                print("[\(self.tag)] HTTP signal relay failed: \(error.localizedDescription)")
            }
        }.resume()
    }

    public func disconnect() {
        webSocketTask?.cancel(with: .normalClosure, reason: "User disconnected session".data(using: .utf8))
        webSocketTask = nil
        urlSession?.invalidateAndCancel()
        urlSession = nil
        isConnected = false
        listener?.onConnectionStatusChanged(isConnected: false)
    }

    // MARK: - URLSessionWebSocketDelegate
    public func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        isConnected = true
        listener?.onConnectionStatusChanged(isConnected: true)
    }

    public func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        isConnected = false
        listener?.onConnectionStatusChanged(isConnected: false)
    }
}
