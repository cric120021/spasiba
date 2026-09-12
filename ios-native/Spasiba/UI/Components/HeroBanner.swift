import SwiftUI

public struct HeroBanner: View {
    public let song: Song
    public let onPlayClick: () -> Void

    public init(song: Song, onPlayClick: @escaping () -> Void) {
        self.song = song
        self.onPlayClick = onPlayClick
    }

    public var body: some View {
        Button(action: onPlayClick) {
            ZStack(alignment: .bottomLeading) {
                SongArtworkView(song: song, cornerRadius: 0)
                    .frame(maxWidth: .infinity, maxHeight: 220)
                    .clipped()

                // Gradient Overlay
                LinearGradient(
                    colors: [Color.clear, Color.black.opacity(0.85)],
                    startPoint: .top,
                    endPoint: .bottom
                )

                // Track Info & Play Badge
                HStack(alignment: .bottom) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("TRENDING HIT")
                            .font(.system(size: 11, weight: .black))
                            .foregroundColor(Color(hex: 0x22D3EE))
                            .tracking(2.0)

                        Text(song.title)
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)

                        Text(song.author)
                            .font(.system(size: 14))
                            .foregroundColor(Color.white.opacity(0.8))
                            .lineLimit(1)
                    }

                    Spacer()

                    ZStack {
                        Circle()
                            .fill(Color.white)
                            .frame(width: 48, height: 48)
                        Image(systemName: "play.fill")
                            .foregroundColor(.black)
                            .font(.system(size: 20))
                            .offset(x: 2)
                    }
                    .shadow(color: Color.black.opacity(0.4), radius: 8, x: 0, y: 4)
                }
                .padding(16)
            }
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .padding(.horizontal)
        }
        .buttonStyle(PlainButtonStyle())
    }
}
