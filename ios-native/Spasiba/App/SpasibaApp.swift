import SwiftUI
import AVFoundation
import UIKit
import UserNotifications

// MARK: - Universal Keyboard Dismiss Helper
public enum UniversalKeyboardDismissHelper {
    public static func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        for scene in UIApplication.shared.connectedScenes {
            if let windowScene = scene as? UIWindowScene {
                for window in windowScene.windows {
                    window.endEditing(true)
                }
            }
        }
    }
}

extension UIApplication {
    public func dismissKeyboard() {
        UniversalKeyboardDismissHelper.dismissKeyboard()
    }
}

final class SpasibaAppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        // Configure notification categories & delegate
        let center = UNUserNotificationCenter.current()
        center.delegate = EunoiaInviteReceiver.shared
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if granted {
                DispatchQueue.main.async {
                    application.registerForRemoteNotifications()
                }
            }
            if let error = error {
                print("[SpasibaAppDelegate] Notification authorization error: \(error)")
            }
        }

        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let tokenString = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
        print("[SpasibaAppDelegate] Registered for remote notifications with APNs token: \(tokenString)")
        SpasibaFcmService.shared.onNewToken(tokenString)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("[SpasibaAppDelegate] Failed to register for remote notifications: \(error)")
    }

    func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable : Any], fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        SpasibaFcmService.shared.handleRemoteNotification(userInfo: userInfo)
        completionHandler(.newData)
    }
}

@main
struct SpasibaApp: App {
    @UIApplicationDelegateAdaptor(SpasibaAppDelegate.self) var appDelegate

    init() {
        // Activate background audio playback session
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playback, mode: .default, policy: .longFormAudio, options: [])
            try audioSession.setActive(true)
        } catch {
            print("AVAudioSession configuration failed: \(error)")
        }
        UIApplication.shared.beginReceivingRemoteControlEvents()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
