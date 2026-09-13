import SwiftUI

public struct TrackOptionsMenu: View {
    public let song: Song
    public var isLiked: Bool
    public var onDismiss: () -> Void
    public var onLikeToggle: () -> Void
    public var onAddToPlaylist: () -> Void
    public var onPlayNow: () -> Void
    public var onPlayNext: () -> Void
    public var onAddToQueue: () -> Void
    public var onRemoveFromQueue: (() -> Void)?
    public var onRemoveFromHistory: (() -> Void)?
    public var onViewArtist: (String) -> Void
    public var onDownloadTrack: (Song, DownloadType, String) -> Void

    @State private var isInfoDialogOpen = false
    @State private var isDownloadDialogOpen = false
    @State private var isQualityPickerOpen = false

    @ObservedObject private var downloadManager = SpasibaDownloadManager.shared

    public init(
        song: Song,
        isLiked: Bool = false,
        onDismiss: @escaping () -> Void = {},
        onLikeToggle: @escaping () -> Void = {},
        onAddToPlaylist: @escaping () -> Void = {},
        onPlayNow: @escaping () -> Void = {},
        onPlayNext: @escaping () -> Void = {},
        onAddToQueue: @escaping () -> Void = {},
        onRemoveFromQueue: (() -> Void)? = nil,
        onRemoveFromHistory: (() -> Void)? = nil,
        onViewArtist: @escaping (String) -> Void = { _ in },
        onDownloadTrack: @escaping (Song, DownloadType, String) -> Void = { _, _, _ in }
    ) {
        self.song = song
        self.isLiked = isLiked
        self.onDismiss = onDismiss
        self.onLikeToggle = onLikeToggle
        self.onAddToPlaylist = onAddToPlaylist
        self.onPlayNow = onPlayNow
        self.onPlayNext = onPlayNext
        self.onAddToQueue = onAddToQueue
        self.onRemoveFromQueue = onRemoveFromQueue
        self.onRemoveFromHistory = onRemoveFromHistory
        self.onViewArtist = onViewArtist
        self.onDownloadTrack = onDownloadTrack
    }

    private var isDownloaded: Bool {
        if let item = downloadManager.downloads[song.id], item.status == .completed {
            return true
        }
        return downloadManager.getDownloadedFile(videoId: song.id) != nil
    }

    private var savedQualityPref: String {
        UserDefaults.standard.string(forKey: "video_download_quality") ?? "Ask each time"
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Drag indicator
            Capsule()
                .fill(Color.white.opacity(0.2))
                .frame(width: 36, height: 4)
                .padding(.top, 10)
                .padding(.bottom, 12)

            // Song Header Card
            HStack(spacing: 14) {
                SongArtworkView(song: song, cornerRadius: 10)
                    .frame(width: 56, height: 56)

                VStack(alignment: .leading, spacing: 4) {
                    Text(song.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                    Text(song.author)
                        .font(.system(size: 14))
                        .foregroundColor(Color.white.opacity(0.7))
                        .lineLimit(1)
                }

                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)

            Divider().background(Color.white.opacity(0.08))

            ScrollView {
                VStack(spacing: 0) {
                    if isDownloaded {
                        trackOptionRow(
                            icon: "trash",
                            title: "Remove download",
                            tint: .white
                        ) {
                            downloadManager.deleteDownload(videoId: song.id)
                            onDismiss()
                        }
                    } else {
                        trackOptionRow(
                            icon: "arrow.down.circle",
                            title: "Download Track",
                            tint: .white
                        ) {
                            isDownloadDialogOpen = true
                        }
                    }

                    trackOptionRow(
                        icon: isLiked ? "heart.fill" : "heart",
                        title: isLiked ? "Remove from Liked Tracks" : "Save to Liked Tracks",
                        tint: .white
                    ) {
                        onLikeToggle()
                        onDismiss()
                    }

                    trackOptionRow(
                        icon: "text.badge.plus",
                        title: "Add to Playlist"
                    ) {
                        onAddToPlaylist()
                        onDismiss()
                    }

                    trackOptionRow(
                        icon: "info.circle",
                        title: "Details"
                    ) {
                        isInfoDialogOpen = true
                    }

                    if let onRemoveFromQueue = onRemoveFromQueue {
                        trackOptionRow(
                            icon: "trash",
                            title: "Remove from Queue",
                            tint: Color(hex: 0xFB7185)
                        ) {
                            onRemoveFromQueue()
                            onDismiss()
                        }
                    }

                    if let onRemoveFromHistory = onRemoveFromHistory {
                        trackOptionRow(
                            icon: "trash",
                            title: "Remove from history",
                            tint: .white
                        ) {
                            onRemoveFromHistory()
                            onDismiss()
                        }
                    }

                    trackOptionRow(
                        icon: "person",
                        title: "View Artist (\(song.author))"
                    ) {
                        onViewArtist(song.author)
                        onDismiss()
                    }

                    trackOptionRow(
                        icon: "square.and.arrow.up",
                        title: "Share Song"
                    ) {
                        ShareUtils.shareSong(song: song)
                        onDismiss()
                    }
                }
                .padding(.bottom, 28)
            }
        }
        .background(Color(hex: 0x121214).ignoresSafeArea())
        .sheet(isPresented: $isInfoDialogOpen) {
            infoDialogView
        }
        .sheet(isPresented: $isDownloadDialogOpen) {
            downloadDialogView
        }
        .sheet(isPresented: $isQualityPickerOpen) {
            qualityPickerView
        }
    }

    private func trackOptionRow(
        icon: String,
        title: String,
        tint: Color = .white,
        onClick: @escaping () -> Void
    ) -> some View {
        Button(action: onClick) {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.system(size: 20))
                    .foregroundColor(tint)
                    .frame(width: 24, height: 24)

                Text(title)
                    .font(.system(size: 15))
                    .foregroundColor(.white)

                Spacer()
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }

    private var infoDialogView: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Track Details")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)

            VStack(alignment: .leading, spacing: 8) {
                Text("Title: \(song.title)")
                    .foregroundColor(.white)
                    .font(.system(size: 14))

                Text("Artist: \(song.author)")
                    .foregroundColor(Color.white.opacity(0.7))
                    .font(.system(size: 13))

                Text("Duration: \(song.durationFormatted)")
                    .foregroundColor(Color.white.opacity(0.7))
                    .font(.system(size: 13))

                Text("Video ID: \(song.id)")
                    .foregroundColor(Color.white.opacity(0.7))
                    .font(.system(size: 13))

                Text("Quality: 256kbps / High Definition")
                    .foregroundColor(.white)
                    .font(.system(size: 13, weight: .semibold))
            }

            Spacer()

            Button(action: { isInfoDialogOpen = false }) {
                Text("Close")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color(hex: 0x262628))
                    .cornerRadius(12)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(hex: 0x121214).ignoresSafeArea())
    }

    private var downloadDialogView: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Download Track")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)

            Text(song.title)
                .font(.system(size: 14))
                .foregroundColor(Color.white.opacity(0.7))
                .lineLimit(1)

            // Audio Only Option (Default)
            Button(action: {
                onDownloadTrack(song, .audioOnly, "320k")
                isDownloadDialogOpen = false
                onDismiss()
            }) {
                HStack(spacing: 12) {
                    Image(systemName: "arrow.down.circle.fill")
                        .font(.system(size: 20))
                        .foregroundColor(.white)

                    Text("Audio Only (Default)")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.white)

                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .background(Color.white.opacity(0.15))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.white, lineWidth: 1)
                )
                .cornerRadius(12)
            }

            // Video + Audio Option
            Button(action: {
                isDownloadDialogOpen = false
                if savedQualityPref == "Ask each time" {
                    isQualityPickerOpen = true
                } else {
                    onDownloadTrack(song, .videoAndAudio, savedQualityPref)
                    onDismiss()
                }
            }) {
                HStack(spacing: 12) {
                    Image(systemName: "arrow.down.circle")
                        .font(.system(size: 20))
                        .foregroundColor(Color.white.opacity(0.7))

                    Text("Video + Audio (\(savedQualityPref))")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.white)

                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .background(Color.white.opacity(0.08))
                .cornerRadius(12)
            }

            Spacer()

            Button(action: { isDownloadDialogOpen = false }) {
                Text("Cancel")
                    .font(.system(size: 15))
                    .foregroundColor(Color.white.opacity(0.7))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(hex: 0x262628).ignoresSafeArea())
    }

    private var qualityPickerView: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Select Video Quality")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)

            ForEach(["1080p", "720p", "480p", "360p"], id: \.self) { quality in
                Button(action: {
                    onDownloadTrack(song, .videoAndAudio, quality)
                    isQualityPickerOpen = false
                    onDismiss()
                }) {
                    Text("\(quality) Video Quality")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(14)
                        .background(Color.white.opacity(0.08))
                        .cornerRadius(10)
                }
            }

            Spacer()

            Button(action: { isQualityPickerOpen = false }) {
                Text("Cancel")
                    .font(.system(size: 15))
                    .foregroundColor(Color.white.opacity(0.7))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(hex: 0x121214).ignoresSafeArea())
    }
}
