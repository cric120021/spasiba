import SwiftUI

public struct EqualizerWaveformAnimation: View {
    public let isPlaying: Bool

    @State private var bar1Height: CGFloat = 0.3
    @State private var bar2Height: CGFloat = 0.8
    @State private var bar3Height: CGFloat = 0.4

    public init(isPlaying: Bool) {
        self.isPlaying = isPlaying
    }

    public var body: some View {
        HStack(alignment: .bottom, spacing: 2) {
            RoundedRectangle(cornerRadius: 2)
                .fill(Color.white)
                .frame(width: 3, height: (isPlaying ? bar1Height : 0.3) * 16)

            RoundedRectangle(cornerRadius: 2)
                .fill(Color.white)
                .frame(width: 3, height: (isPlaying ? bar2Height : 0.6) * 16)

            RoundedRectangle(cornerRadius: 2)
                .fill(Color.white)
                .frame(width: 3, height: (isPlaying ? bar3Height : 0.4) * 16)
        }
        .frame(width: 16, height: 16, alignment: .bottom)
        .onAppear {
            updateAnimations()
        }
        .onChange(of: isPlaying) { playing in
            updateAnimations()
        }
    }

    private func updateAnimations() {
        if isPlaying {
            withAnimation(
                Animation.easeInOut(duration: 0.40)
                    .repeatForever(autoreverses: true)
            ) {
                bar1Height = 0.9
            }

            withAnimation(
                Animation.easeInOut(duration: 0.55)
                    .repeatForever(autoreverses: true)
            ) {
                bar2Height = 0.2
            }

            withAnimation(
                Animation.easeInOut(duration: 0.45)
                    .repeatForever(autoreverses: true)
            ) {
                bar3Height = 1.0
            }
        } else {
            bar1Height = 0.3
            bar2Height = 0.6
            bar3Height = 0.4
        }
    }
}
