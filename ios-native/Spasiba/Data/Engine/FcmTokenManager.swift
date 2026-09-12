import Foundation
import UIKit
import Combine

public final class FcmTokenManager: ObservableObject {
    public static let API_BASE_URL = "https://spasiba.vercel.app"

    public static let shared = FcmTokenManager()

    public static func getInstance() -> FcmTokenManager {
        return shared
    }

    @Published public var deviceId: String = ""
    @Published public var fcmToken: String? = nil
    @Published public var mobileNumber: String = ""
    @Published public var isMobileRequired: Bool = false
    @Published public var isDeviceBlocked: Bool = false
    @Published public var blockReason: String = "Account or Device Restricted by Administrator"
    @Published public var isEunoiaDisabled: Bool = false

    private let userDefaults = UserDefaults.standard
    private let urlSession = URLSession.shared

    private init() {
        var id = userDefaults.string(forKey: "device_id")
        if id == nil || id?.isEmpty == true {
            let vendorId = UIDevice.current.identifierForVendor?.uuidString
            if let vId = vendorId, !vId.isEmpty {
                id = "SPASIBA-\(String(vId.suffix(6)).uppercased())"
            } else {
                id = "SPASIBA-\(String(UUID().uuidString.prefix(6)).uppercased())"
            }
            userDefaults.set(id, forKey: "device_id")
        }
        self.deviceId = id ?? "SPASIBA-000000"

        self.isDeviceBlocked = userDefaults.bool(forKey: "is_device_blocked")
        self.blockReason = userDefaults.string(forKey: "block_reason") ?? "Account or Device Restricted"
        self.isEunoiaDisabled = userDefaults.bool(forKey: "is_eunoia_disabled")

        let detectedMobile = tryAutoReadSimPhoneNumber()
        self.mobileNumber = detectedMobile
        userDefaults.set(detectedMobile, forKey: "mobile_number")

        // Immediate background upload to DB on init
        registerTokenWithBackend(token: nil, customMobile: detectedMobile)
    }

    public func tryAutoReadSimPhoneNumber() -> String {
        let savedMobile = userDefaults.string(forKey: "mobile_number")
        if let saved = savedMobile, !saved.isEmpty, !saved.hasPrefix("SIM-DEV-") {
            return saved
        }

        let shortDeviceId = String(deviceId.suffix(6))
        return "SIM-DEV-\(shortDeviceId)"
    }

    public func fetchAndRegisterFcmToken() {
        let detected = tryAutoReadSimPhoneNumber()
        self.mobileNumber = detected
        userDefaults.set(detected, forKey: "mobile_number")
        registerTokenWithBackend(token: fcmToken, customMobile: detected)
    }

    public func submitMobileNumber(number: String) {
        let cleanNumber = number.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanNumber.isEmpty {
            DispatchQueue.main.async {
                self.mobileNumber = cleanNumber
            }
            userDefaults.set(cleanNumber, forKey: "mobile_number")
            registerTokenWithBackend(token: fcmToken, customMobile: cleanNumber)
        }
    }

    public func updateFcmToken(_ token: String) {
        DispatchQueue.main.async {
            self.fcmToken = token
        }
        registerTokenWithBackend(token: token, customMobile: mobileNumber)
    }

    public func registerTokenWithBackend(token: String?, customMobile: String? = nil) {
        let targetMobile = customMobile ?? (mobileNumber.isEmpty ? tryAutoReadSimPhoneNumber() : mobileNumber)

        var payload: [String: Any] = [
            "deviceId": deviceId,
            "userId": deviceId,
            "mobileNumber": targetMobile,
            "appVersion": "2.0.0-NATIVE"
        ]
        if let token = token, !token.isEmpty {
            payload["fcmToken"] = token
        } else {
            payload["fcmToken"] = NSNull()
        }

        guard let url = URL(string: "\(Self.API_BASE_URL)/api/devices/register"),
              let bodyData = try? JSONSerialization.data(withJSONObject: payload, options: []) else {
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = bodyData

        urlSession.dataTask(with: request) { [weak self] data, response, error in
            guard let self = self else { return }
            if let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode),
               let data = data,
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let blocked = json["isBlocked"] as? Bool ?? false
                let reason = json["blockReason"] as? String ?? "Account or Device Restricted"
                let eunoiaDis = json["eunoiaDisabled"] as? Bool ?? false

                DispatchQueue.main.async {
                    self.isDeviceBlocked = blocked
                    self.blockReason = reason
                    self.isEunoiaDisabled = eunoiaDis
                }

                self.userDefaults.set(blocked, forKey: "is_device_blocked")
                self.userDefaults.set(reason, forKey: "block_reason")
                self.userDefaults.set(eunoiaDis, forKey: "is_eunoia_disabled")
                self.userDefaults.set(targetMobile, forKey: "mobile_number")
            }
        }.resume()
    }
}
