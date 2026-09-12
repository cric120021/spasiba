import SwiftUI

public struct ContentView: View {
    public init() {}

    public var body: some View {
        ZStack {
            // Keep the audio bridge rendering in the view hierarchy behind the opaque UI
            // so WebKit does not throttle or suspend audio playback.
            BackgroundAudioBridgeRepresentable()
                .frame(width: 200, height: 200)
                .allowsHitTesting(false)

            SpasibaAppShell()
        }
        .preferredColorScheme(.dark)
    }
}
