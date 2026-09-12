import SwiftUI

public struct DynamicBackdrop<Content: View>: View {
    public let imageUrl: String?
    public let content: () -> Content

    public init(imageUrl: String? = nil, @ViewBuilder content: @escaping () -> Content = { EmptyView() }) {
        self.imageUrl = imageUrl
        self.content = content
    }

    public var body: some View {
        ZStack {
            if let imageUrl = imageUrl, let url = URL(string: imageUrl) {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .blur(radius: 50)
                            .opacity(0.35)
                    }
                }
                .ignoresSafeArea()
            }

            LinearGradient(
                colors: [
                    Color(hex: 0x1E1C1A).opacity(0.85),
                    Color(hex: 0x0F0E0C).opacity(0.90),
                    Color.black
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            content()
        }
    }
}
