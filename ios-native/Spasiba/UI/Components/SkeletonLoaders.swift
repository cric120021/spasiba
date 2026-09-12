import SwiftUI

// MARK: - Shimmer Effect & Brush

public struct ShimmerBrushModifier: ViewModifier {
    @State private var phase: CGFloat = -1.0

    public func body(content: Content) -> some View {
        content
            .overlay(
                GeometryReader { geo in
                    LinearGradient(
                        colors: [
                            Color.white.opacity(0.05),
                            Color.white.opacity(0.15),
                            Color.white.opacity(0.05)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    .frame(width: geo.size.width * 1.6)
                    .offset(x: phase * (geo.size.width + 200))
                    .mask(content)
                }
            )
            .onAppear {
                withAnimation(
                    Animation.timingCurve(0.4, 0.0, 0.2, 1.0, duration: 1.2)
                        .repeatForever(autoreverses: false)
                ) {
                    phase = 1.0
                }
            }
    }
}

public extension View {
    func shimmer() -> some View {
        self.modifier(ShimmerBrushModifier())
    }
}

public struct ShimmerBox: View {
    public let width: CGFloat?
    public let height: CGFloat?
    public let cornerRadius: CGFloat

    public init(width: CGFloat? = nil, height: CGFloat? = nil, cornerRadius: CGFloat = 4) {
        self.width = width
        self.height = height
        self.cornerRadius = cornerRadius
    }

    public var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius)
            .fill(Color.white.opacity(0.08))
            .shimmer()
            .frame(width: width, height: height)
    }
}

// MARK: - Song Tile Skeleton

public struct SongTileSkeleton: View {
    public init() {}

    public var body: some View {
        HStack(alignment: .center, spacing: 14) {
            ShimmerBox(width: 52, height: 52, cornerRadius: 10)

            VStack(alignment: .leading, spacing: 6) {
                GeometryReader { geo in
                    ShimmerBox(width: geo.size.width * 0.7, height: 16, cornerRadius: 4)
                }
                .frame(height: 16)

                GeometryReader { geo in
                    ShimmerBox(width: geo.size.width * 0.4, height: 13, cornerRadius: 4)
                }
                .frame(height: 13)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

// MARK: - Hero Banner Skeleton

public struct HeroBannerSkeleton: View {
    public init() {}

    public var body: some View {
        ShimmerBox(height: 200, cornerRadius: 20)
            .padding(.horizontal, 16)
    }
}

// MARK: - Chart Item Skeleton

public struct ChartItemSkeleton: View {
    public init() {}

    public var body: some View {
        HStack(alignment: .center, spacing: 10) {
            ShimmerBox(width: 24, height: 18, cornerRadius: 4)

            ShimmerBox(width: 52, height: 52, cornerRadius: 8)

            VStack(alignment: .leading, spacing: 6) {
                GeometryReader { geo in
                    ShimmerBox(width: geo.size.width * 0.65, height: 15, cornerRadius: 4)
                }
                .frame(height: 15)

                GeometryReader { geo in
                    ShimmerBox(width: geo.size.width * 0.35, height: 12, cornerRadius: 4)
                }
                .frame(height: 12)
            }
            .padding(.leading, 4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
    }
}

// MARK: - Home Screen Skeleton

public struct HomeScreenSkeleton: View {
    public init() {}

    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                // Header row
                HStack(alignment: .center) {
                    ShimmerBox(width: 140, height: 28, cornerRadius: 8)
                    Spacer()
                    Circle()
                        .fill(Color.white.opacity(0.08))
                        .shimmer()
                        .frame(width: 40, height: 40)
                    Circle()
                        .fill(Color.white.opacity(0.08))
                        .shimmer()
                        .frame(width: 40, height: 40)
                        .padding(.leading, 12)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)

                // Category Chips row
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(0..<4, id: \.self) { _ in
                            ShimmerBox(width: 90, height: 34, cornerRadius: 16)
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .padding(.top, 12)

                // Quick picks section title
                ShimmerBox(width: 180, height: 24, cornerRadius: 6)
                    .padding(.horizontal, 16)
                    .padding(.top, 24)

                // Quick picks 2 columns of 4 items
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(0..<2, id: \.self) { _ in
                            VStack(spacing: 10) {
                                ForEach(0..<4, id: \.self) { _ in
                                    HStack(alignment: .center, spacing: 12) {
                                        ShimmerBox(width: 52, height: 52, cornerRadius: 8)
                                        VStack(alignment: .leading, spacing: 6) {
                                            ShimmerBox(width: 160, height: 16, cornerRadius: 4)
                                            ShimmerBox(width: 100, height: 12, cornerRadius: 4)
                                        }
                                    }
                                }
                            }
                            .frame(width: 300)
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .padding(.top, 14)

                // Horizontal album cards row
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 14) {
                        ForEach(0..<3, id: \.self) { _ in
                            VStack(alignment: .leading, spacing: 8) {
                                ShimmerBox(width: 150, height: 150, cornerRadius: 12)
                                ShimmerBox(width: 120, height: 14, cornerRadius: 4)
                            }
                            .frame(width: 150)
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .padding(.top, 28)
            }
            .padding(.top, 16)
        }
        .background(Color.black.ignoresSafeArea())
    }
}
