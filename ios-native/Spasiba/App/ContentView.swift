import SwiftUI

public struct ContentView: View {
    public init() {}

    public var body: some View {
        ZStack {
            SpasibaAppShell()
            HeadlessAudioBridgeRepresentable()
                .frame(width: 320, height: 240)
                .opacity(0.001)
                .allowsHitTesting(false)
        }
        .preferredColorScheme(.dark)
    }
}
