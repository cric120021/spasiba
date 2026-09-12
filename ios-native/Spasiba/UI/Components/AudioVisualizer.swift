import SwiftUI

public struct AudioVisualizer: View {
    public let isPlaying: Bool
    @State private var barHeights: [CGFloat] = [0.3, 0.7, 0.5, 0.9, 0.4]

    public init(isPlaying: Bool = false) {
        self.isPlaying = isPlaying
    }

    public var body: some View {
        HStack(spacing: 3) {
            ForEach(0..<barHeights.count, id: \.self) { index in
                RoundedRectangle(cornerRadius: 1.5)
                    .fill(Color.white)
                    .frame(width: 3, height: isPlaying ? barHeights[index] * 20 : 4)
                    .animation(
                        isPlaying ? Animation.easeInOut(duration: 0.35).repeatForever().delay(Double(index) * 0.08) : .default,
                        value: barHeights[index]
                    )
            }
        }
        .onAppear {
            if isPlaying {
                barHeights = [0.8, 0.4, 0.9, 0.3, 0.7]
            }
        }
    }
}
