import SwiftUI

public struct MiniPlayerDock: View {
    public let song: Song
    public let isPlaying: Bool
    public let progress: Double
    public let onDockClick: () -> Void
    public let onPlayPauseClick: () -> Void
    public let onNextClick: () -> Void

    public init(
        song: Song,
        isPlaying: Bool,
        progress: Double,
        onDockClick: @escaping () -> Void,
        onPlayPauseClick: @escaping () -> Void,
        onNextClick: @escaping () -> Void
    ) {
        self.song = song
        self.isPlaying = isPlaying
        self.progress = progress
        self.onDockClick = onDockClick
        self.onPlayPauseClick = onPlayPauseClick
        self.onNextClick = onNextClick
    }

    public var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                // Tappable track details area (opens full player sheet)
                HStack(spacing: 12) {
                    // Artwork
                    SongArtworkView(song: song, cornerRadius: 8)
                        .id(song.id)
                        .frame(width: 44, height: 44)

                    // Track details
                    VStack(alignment: .leading, spacing: 2) {
                        Text(song.title)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .truncationMode(.tail)

                        Text(song.author)
                            .font(.system(size: 12))
                            .foregroundColor(Color(hex: 0xB3B3B3))
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(Rectangle())
                .onTapGesture {
                    onDockClick()
                }

                // Independent Action Buttons (Play/Pause, Next) with Apple HIG 44x44 touch targets
                HStack(spacing: 4) {
                    // Play/Pause button
                    Button(action: {
                        NSLog("[MiniPlayerDock] Tapped play/pause, current isPlaying: %@", isPlaying ? "true" : "false")
                        onPlayPauseClick()
                    }) {
                        Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(PlainButtonStyle())

                    // Next button
                    Button(action: {
                        NSLog("[MiniPlayerDock] Tapped next track")
                        onNextClick()
                    }) {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .padding(.horizontal, 12)
            .frame(height: 56)

            // Bottom Progress Line
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Rectangle()
                        .fill(Color.white.opacity(0.12))
                        .frame(height: 2)

                    Rectangle()
                        .fill(Color.white)
                        .frame(width: geo.size.width * max(0.0, min(1.0, progress)), height: 2)
                }
            }
            .frame(height: 2)
        }
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(hex: 0x212121))
                .shadow(color: Color.black.opacity(0.5), radius: 10, x: 0, y: 4)
        )
        .padding(.horizontal, 10)
    }
}
