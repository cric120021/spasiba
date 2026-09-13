import SwiftUI

public struct SongBadge: View {
    public let text: String
    public let color: Color
    public let textColor: Color

    public init(
        text: String,
        color: Color = Color(hex: 0x2DD4BF).opacity(0.2),
        textColor: Color = Color(hex: 0x1DB954)
    ) {
        self.text = text
        self.color = color
        self.textColor = textColor
    }

    public var body: some View {
        Text(text)
            .font(.system(size: 9, weight: .bold))
            .foregroundColor(textColor)
            .padding(.horizontal, 4)
            .padding(.vertical, 1)
            .background(color)
            .cornerRadius(4)
    }
}

public struct SongTile: View {
    public let song: Song
    public let isPlaying: Bool
    public let isLiked: Bool
    public let isDownloaded: Bool
    public let enableSwipe: Bool
    public let onSongClick: () -> Void
    public var onLikeClick: () -> Void
    public var onOptionsClick: () -> Void
    public var onLongClick: (() -> Void)?
    public var onSwipeToQueue: () -> Void

    @State private var dragOffset: CGFloat = 0

    public init(
        song: Song,
        isPlaying: Bool = false,
        isLiked: Bool = false,
        isDownloaded: Bool = false,
        enableSwipe: Bool = false,
        onSongClick: @escaping () -> Void,
        onLikeClick: @escaping () -> Void = {},
        onOptionsClick: @escaping () -> Void = {},
        onLongClick: (() -> Void)? = nil,
        onSwipeToQueue: @escaping () -> Void = {}
    ) {
        self.song = song
        self.isPlaying = isPlaying
        self.isLiked = isLiked
        self.isDownloaded = isDownloaded
        self.enableSwipe = enableSwipe
        self.onSongClick = onSongClick
        self.onLikeClick = onLikeClick
        self.onOptionsClick = onOptionsClick
        self.onLongClick = onLongClick
        self.onSwipeToQueue = onSwipeToQueue
    }

    public var body: some View {
        ZStack {
            // Swipe Background Action Indicators (when enableSwipe is true)
            if enableSwipe && dragOffset != 0 {
                HStack {
                    if dragOffset > 0 {
                        Image(systemName: "heart.fill")
                            .font(.system(size: 20))
                            .foregroundColor(Color.white)
                            .padding(.leading, 20)
                        Spacer()
                    } else if dragOffset < 0 {
                        Spacer()
                        Image(systemName: "text.badge.plus")
                            .font(.system(size: 20))
                            .foregroundColor(Color(hex: 0x10B981))
                            .padding(.trailing, 20)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(
                    dragOffset > 0
                        ? Color.white.opacity(0.25)
                        : Color(hex: 0x10B981).opacity(0.25)
                )
                .cornerRadius(14)
            }

            // Foreground Song Tile Content
            HStack(spacing: 16) {
                SongArtworkView(song: song, cornerRadius: 8)
                    .frame(width: 54, height: 54)

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        if isPlaying {
                            EqualizerWaveformAnimation(isPlaying: true)
                        }

                        Text(song.title)
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }

                    HStack(spacing: 6) {
                        if isDownloaded {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 13))
                                .foregroundColor(Color.white.opacity(0.85))
                        }

                        if song.title.localizedCaseInsensitiveContains("explicit") {
                            SongBadge(text: "E", color: Color(hex: 0xFF0055).opacity(0.2), textColor: Color(hex: 0xFFFF2A6D))
                        }

                        if song.title.localizedCaseInsensitiveContains("official") || song.views > 1_000_000 {
                            SongBadge(text: "HD", color: Color(hex: 0x10B981).opacity(0.2), textColor: Color(hex: 0xFF10B981))
                        }

                        let displayAuthor = (!song.author.trimmingCharacters(in: .whitespaces).isEmpty &&
                                             song.author.caseInsensitiveCompare("Artist") != .orderedSame &&
                                             song.author.caseInsensitiveCompare("YouTube Music") != .orderedSame) ? song.author : "Spasiba Music"
                        let durationInfo = song.durationSeconds > 0 ? " • \(song.durationFormatted)" : ""

                        Text("\(displayAuthor)\(durationInfo)")
                            .font(.system(size: 12))
                            .foregroundColor(Color.white.opacity(0.6))
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Like Button
                Button(action: onLikeClick) {
                    Image(systemName: isLiked ? "heart.fill" : "heart")
                        .font(.system(size: 18))
                        .foregroundColor(isLiked ? Color.white : Color.white.opacity(0.5))
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(PlainButtonStyle())

                // Options Button
                Button(action: onOptionsClick) {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 18))
                        .foregroundColor(Color.white.opacity(0.5))
                        .rotationEffect(.degrees(90))
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(PlainButtonStyle())
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 8)
            .background(isPlaying ? Color.white.opacity(0.12) : Color.clear)
            .cornerRadius(14)
            .offset(x: dragOffset)
            .gesture(
                enableSwipe ?
                DragGesture()
                    .onChanged { value in
                        let translation = value.translation.width
                        dragOffset = min(max(translation, -180), 180)
                    }
                    .onEnded { _ in
                        withAnimation(.easeOut(duration: 0.25)) {
                            if dragOffset > 120 {
                                onLikeClick()
                            } else if dragOffset < -120 {
                                onSwipeToQueue()
                            }
                            dragOffset = 0
                        }
                    }
                : nil
            )
            .onTapGesture {
                onSongClick()
            }
            .onLongPressGesture {
                onLongClick?()
            }
        }
        .frame(maxWidth: .infinity)
        .clipped()
        .padding(.vertical, 4)
    }
}
