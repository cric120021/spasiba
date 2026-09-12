import SwiftUI
import AVFoundation

@main
struct SpasibaApp: App {
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
