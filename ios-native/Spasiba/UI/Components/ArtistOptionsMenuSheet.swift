import SwiftUI

public struct ArtistOptionsMenuSheet: View {
    public let artistName: String
    public var subtitle: String
    public var onDismiss: () -> Void
    public var onShufflePlay: () -> Void
    public var onStartMix: () -> Void
    public var onShare: () -> Void
    public var onSubscribe: () -> Void
    public var onRemoveFromHistory: () -> Void

    public init(
        artistName: String,
        subtitle: String = "Artist",
        onDismiss: @escaping () -> Void = {},
        onShufflePlay: @escaping () -> Void = {},
        onStartMix: @escaping () -> Void = {},
        onShare: @escaping () -> Void = {},
        onSubscribe: @escaping () -> Void = {},
        onRemoveFromHistory: @escaping () -> Void = {}
    ) {
        self.artistName = artistName
        self.subtitle = subtitle
        self.onDismiss = onDismiss
        self.onShufflePlay = onShufflePlay
        self.onStartMix = onStartMix
        self.onShare = onShare
        self.onSubscribe = onSubscribe
        self.onRemoveFromHistory = onRemoveFromHistory
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header Row: Artist Name, Subtitle, Close X
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(artistName)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)

                    Text(subtitle)
                        .font(.system(size: 13))
                        .foregroundColor(Color.white.opacity(0.6))
                        .lineLimit(1)
                }

                Spacer()

                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(Color.white.opacity(0.7))
                        .frame(width: 36, height: 36)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 10)

            Spacer().frame(height: 10)

            // Action Cards Row: [ 🔀 Shuffle play ] [ ((►)) Start mix ] [ ➔ Share ]
            HStack(spacing: 10) {
                // Shuffle play
                Button(action: {
                    onShufflePlay()
                    onDismiss()
                }) {
                    VStack(spacing: 6) {
                        Image(systemName: "shuffle")
                            .font(.system(size: 22))
                            .foregroundColor(.white)
                        Text("Shuffle play")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.white)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 80)
                    .background(Color.white.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                }

                // Start mix
                Button(action: {
                    onStartMix()
                    onDismiss()
                }) {
                    VStack(spacing: 6) {
                        Image(systemName: "play.fill")
                            .font(.system(size: 22))
                            .foregroundColor(.white)
                        Text("Start mix")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.white)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 80)
                    .background(Color.white.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                }

                // Share
                Button(action: {
                    onShare()
                    onDismiss()
                }) {
                    VStack(spacing: 6) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                        Text("Share")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.white)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 80)
                    .background(Color.white.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                }
            }
            .padding(.horizontal, 20)

            Spacer().frame(height: 16)

            // Subscribe option
            Button(action: {
                onSubscribe()
                onDismiss()
            }) {
                HStack(spacing: 18) {
                    Image(systemName: "play.circle")
                        .font(.system(size: 22))
                        .foregroundColor(.white)
                        .frame(width: 24)

                    Text("Subscribe")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.white)

                    Spacer()
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 14)
            }

            // Remove from history option
            Button(action: {
                onRemoveFromHistory()
                onDismiss()
            }) {
                HStack(spacing: 18) {
                    Image(systemName: "trash")
                        .font(.system(size: 22))
                        .foregroundColor(.white)
                        .frame(width: 24)

                    Text("Remove from history")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.white)

                    Spacer()
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 14)
            }

            Spacer().frame(height: 28)
        }
        .background(Color(hex: 0x1C1C1E).ignoresSafeArea())
    }
}
