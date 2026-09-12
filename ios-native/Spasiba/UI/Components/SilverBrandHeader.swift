import SwiftUI

public struct SilverBrandHeader: View {
    public let subtitle: String?
    public var onSearchTapped: (() -> Void)?
    public var onProfileTapped: (() -> Void)?

    public init(
        subtitle: String? = "Music tailored for your flow",
        onSearchTapped: (() -> Void)? = nil,
        onProfileTapped: (() -> Void)? = nil
    ) {
        self.subtitle = subtitle
        self.onSearchTapped = onSearchTapped
        self.onProfileTapped = onProfileTapped
    }

    public var body: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("MUSIC")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Color(hex: 0x94A3B8))
                    .tracking(2.4)

                Text("Spasiba")
                    .font(.system(size: 28, weight: .black))
                    .foregroundStyle(SpasibaTheme.silverGradient)

                if let subtitle = subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: 0xB3B3B3))
                }
            }

            Spacer()

            HStack(spacing: 16) {
                if let onSearch = onSearchTapped {
                    Button(action: onSearch) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 38, height: 38)
                            .background(Color.white.opacity(0.08))
                            .clipShape(Circle())
                    }
                }

                if let onProfile = onProfileTapped {
                    Button(action: onProfile) {
                        Image(systemName: "person.crop.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.white)
                    }
                }
            }
        }
        .padding(.horizontal)
        .padding(.top, 8)
    }
}
