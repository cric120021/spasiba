import SwiftUI
import AVFoundation
import UIKit

// MARK: - Universal Tap-Outside-to-Dismiss Keyboard Recognizer
public final class UniversalKeyboardDismissGestureRecognizer: UITapGestureRecognizer, UIGestureRecognizerDelegate {
    public static let identifier = "UniversalKeyboardDismissGestureRecognizer"

    public init() {
        super.init(target: nil, action: nil)
        self.name = UniversalKeyboardDismissGestureRecognizer.identifier
        self.cancelsTouchesInView = false
        self.delaysTouchesBegan = false
        self.delaysTouchesEnded = false
        self.delegate = self
        self.addTarget(self, action: #selector(handleUniversalTap))
    }

    @objc private func handleUniversalTap() {
        UniversalKeyboardDismissHelper.dismissKeyboard()
    }

    public func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        // If the tap occurred inside an edit box (UITextField, UITextView, UISearchBar),
        // let the edit box receive the touch without dismissing the keyboard.
        var current: UIView? = touch.view
        while let view = current {
            if view is UITextField || view is UITextView || view is UISearchBar {
                return false
            }
            current = view.superview
        }
        return true
    }

    public func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        return true
    }
}

// MARK: - Universal Keyboard Dismiss Helper & Window Manager
public final class UniversalKeyboardDismissHelper: NSObject {
    public static let shared = UniversalKeyboardDismissHelper()
    private var isStarted = false

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

    public func start() {
        guard !isStarted else { return }
        isStarted = true

        attachToAllCurrentWindows()

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(windowDidBecomeKeyOrVisible(_:)),
            name: UIWindow.didBecomeKeyNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(windowDidBecomeKeyOrVisible(_:)),
            name: UIWindow.didBecomeVisibleNotification,
            object: nil
        )
    }

    public func attach(to window: UIWindow) {
        if window.gestureRecognizers?.contains(where: { $0.name == UniversalKeyboardDismissGestureRecognizer.identifier }) == true {
            return
        }
        let recognizer = UniversalKeyboardDismissGestureRecognizer()
        window.addGestureRecognizer(recognizer)
    }

    public func attachToAllCurrentWindows() {
        for scene in UIApplication.shared.connectedScenes {
            if let windowScene = scene as? UIWindowScene {
                for window in windowScene.windows {
                    attach(to: window)
                }
            }
        }
    }

    @objc private func windowDidBecomeKeyOrVisible(_ notification: Notification) {
        if let window = notification.object as? UIWindow {
            attach(to: window)
        } else {
            attachToAllCurrentWindows()
        }
    }
}

extension UIApplication {
    public func dismissKeyboard() {
        UniversalKeyboardDismissHelper.dismissKeyboard()
    }
}

// MARK: - Window Gesture Attacher (Zero-size Representable)
public struct WindowGestureAttacher: UIViewRepresentable {
    public init() {}
    public func makeUIView(context: Context) -> AttacherView {
        AttacherView()
    }
    public func updateUIView(_ uiView: AttacherView, context: Context) {}

    public final class AttacherView: UIView {
        public override func didMoveToWindow() {
            super.didMoveToWindow()
            if let window = self.window {
                UniversalKeyboardDismissHelper.shared.attach(to: window)
            }
        }
    }
}

final class SpasibaAppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        UniversalKeyboardDismissHelper.shared.start()
        return true
    }
}

@main
struct SpasibaApp: App {
    @UIApplicationDelegateAdaptor(SpasibaAppDelegate.self) var appDelegate

    init() {
        UniversalKeyboardDismissHelper.shared.start()
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
                .background(WindowGestureAttacher())
        }
    }
}
