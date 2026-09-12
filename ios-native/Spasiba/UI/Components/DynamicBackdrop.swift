import SwiftUI

public struct DynamicBackdrop<Content: View>: View {
    public let imageUrl: String?
    public let content: () -> Content

    public init(imageUrl: String? = nil, @ViewBuilder content: @escaping () -> Content = { EmptyView() }) {
        self.imageUrl = imageUrl
        self.content = content
    }

    public var body: some View {
        GeometryReader { geo in
            ZStack {
                if let imageUrl = imageUrl, let url = URL(string: imageUrl) {
                    AsyncImage(url: url) { phase in
                        if let image = phase.image {
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: geo.size.width, height: geo.size.height)
                                .clipped()
                                .blur(radius: 50)
                                .opacity(0.35)
                        }
                    }
                    .frame(width: geo.size.width, height: geo.size.height)
                    .clipped()
                }

                LinearGradient(
                    colors: [
                        Color(hex: 0x121212).opacity(0.85),
                        Color(hex: 0x080808).opacity(0.90),
                        Color.black
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(width: geo.size.width, height: geo.size.height)

                content()
                    .frame(width: geo.size.width, height: geo.size.height)
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .clipped()
        }
        .ignoresSafeArea()
    }
}
