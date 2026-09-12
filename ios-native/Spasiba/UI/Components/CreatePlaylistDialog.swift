import SwiftUI

public struct CreatePlaylistDialog: View {
    public var onDismiss: () -> Void
    public var onCreate: (String) -> Void

    @State private var name: String = ""

    public init(
        onDismiss: @escaping () -> Void = {},
        onCreate: @escaping (String) -> Void
    ) {
        self.onDismiss = onDismiss
        self.onCreate = onCreate
    }

    public var body: some View {
        ZStack {
            Color.black.opacity(0.6)
                .ignoresSafeArea()
                .onTapGesture {
                    onDismiss()
                }

            VStack(alignment: .leading, spacing: 16) {
                Text("New Playlist")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(SpasibaTheme.textStrong)

                Text("Give your playlist a name:")
                    .font(.system(size: 14))
                    .foregroundColor(SpasibaTheme.textMuted)

                TextField("Playlist Name", text: $name)
                    .foregroundColor(SpasibaTheme.textStrong)
                    .padding(12)
                    .background(Color.white.opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.3), lineWidth: 1)
                    )

                HStack(spacing: 12) {
                    Spacer()

                    Button(action: onDismiss) {
                        Text("Cancel")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(SpasibaTheme.textMuted)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                    }

                    Button(action: {
                        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !trimmed.isEmpty {
                            onCreate(trimmed)
                        }
                    }) {
                        Text("Create")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.black)
                            .padding(.horizontal, 18)
                            .padding(.vertical, 8)
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                }
                .padding(.top, 8)
            }
            .padding(24)
            .background(SpasibaTheme.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(SpasibaTheme.border, lineWidth: 1)
            )
            .padding(.horizontal, 32)
        }
    }
}
