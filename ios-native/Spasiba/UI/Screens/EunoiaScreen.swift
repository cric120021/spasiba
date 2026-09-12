import SwiftUI

public struct EunoiaScreen: View {
    @ObservedObject var player = NativePlayerManager.shared
    @Environment(\.dismiss) private var dismiss

    @State private var roomId: String = "ROOM-SPASIBA-88"
    @State private var participants: [String] = ["You (Host)", "Simran", "Rahul"]
    @State private var isPartyActive: Bool = true
    @State private var chatMessage: String = ""
    @State private var messages: [String] = [
        "Welcome to Spasiba Eunoia Party!",
        "Simran: This beat is insane 🔥",
        "Rahul: Adding next track to queue..."
    ]

    public init() {}

    public var body: some View {
        ZStack {
            SpasibaTheme.background.ignoresSafeArea()

            VStack(spacing: 16) {
                // Header
                HStack {
                    Button(action: { dismiss() }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text("EUNOIA LISTEN-ALONG")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(Color(hex: 0x22D3EE))
                            .tracking(2.0)
                        Text(roomId)
                            .font(.system(size: 18, weight: .black))
                            .foregroundColor(.white)
                    }

                    Spacer()

                    HStack(spacing: 6) {
                        Circle()
                            .fill(Color.green)
                            .frame(width: 8, height: 8)
                        Text("LIVE")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.green)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Color.green.opacity(0.15))
                    .clipShape(Capsule())
                }
                .padding(.horizontal)
                .padding(.top, 16)

                // Currently Playing in Room
                if let song = player.currentSong {
                    HStack(spacing: 12) {
                        SongArtworkView(song: song, cornerRadius: 10)
                            .frame(width: 56, height: 56)

                        VStack(alignment: .leading, spacing: 3) {
                            Text(song.title)
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(.white)
                                .lineLimit(1)
                            Text(song.author)
                                .font(.system(size: 13))
                                .foregroundColor(Color.white.opacity(0.7))
                                .lineLimit(1)
                        }

                        Spacer()

                        AudioVisualizer(isPlaying: player.isPlaying)
                    }
                    .padding(12)
                    .background(SpasibaTheme.surfaceElevated)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal)
                }

                // Participants
                VStack(alignment: .leading, spacing: 8) {
                    Text("Party Members (\(participants.count))")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(SpasibaTheme.textStrong)
                        .padding(.horizontal)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(participants, id: \.self) { p in
                                HStack(spacing: 6) {
                                    Circle()
                                        .fill(Color(hex: 0x6366F1))
                                        .frame(width: 24, height: 24)
                                        .overlay(Text(p.prefix(1)).font(.system(size: 11, weight: .bold)).foregroundColor(.white))
                                    Text(p)
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(.white)
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(SpasibaTheme.surfaceElevated)
                                .clipShape(Capsule())
                            }
                        }
                        .padding(.horizontal)
                    }
                }

                Divider().background(Color.white.opacity(0.08))

                // Chat Messages
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(messages, id: \.self) { msg in
                            Text(msg)
                                .font(.system(size: 14))
                                .foregroundColor(Color.white.opacity(0.9))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(SpasibaTheme.surfaceElevated)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                    }
                    .padding(.horizontal)
                }

                // Chat Input Bar
                HStack(spacing: 10) {
                    HStack(spacing: 10) {
                        Image(systemName: "slider.horizontal.3")
                            .font(.system(size: 18))
                            .foregroundColor(!chatMessage.trimmingCharacters(in: .whitespaces).isEmpty ? .white : SpasibaTheme.textMuted)

                        ZStack(alignment: .leading) {
                            if chatMessage.isEmpty {
                                Text("Write a message in room...")
                                    .font(.system(size: 15))
                                    .foregroundColor(SpasibaTheme.textMuted)
                            }
                            TextField("", text: $chatMessage)
                                .font(.system(size: 15))
                                .foregroundColor(SpasibaTheme.textStrong)
                                .accentColor(.white)
                        }
                    }
                    .padding(.horizontal, 16)
                    .frame(height: 54)
                    .background(SpasibaTheme.surfaceElevated)
                    .clipShape(RoundedRectangle(cornerRadius: 27))
                    .overlay(
                        RoundedRectangle(cornerRadius: 27)
                            .stroke(Color.white.opacity(0.14), lineWidth: 1)
                    )

                    Button(action: {
                        let clean = chatMessage.trimmingCharacters(in: .whitespaces)
                        if !clean.isEmpty {
                            messages.append("You: \(clean)")
                            chatMessage = ""
                        }
                    }) {
                        Image(systemName: "paperplane.fill")
                            .font(.system(size: 16))
                            .foregroundColor(.black)
                            .frame(width: 44, height: 44)
                            .background(Color.white)
                            .clipShape(Circle())
                    }
                }
                .padding(.horizontal)
                .padding(.bottom, 20)
            }
        }
    }
}
