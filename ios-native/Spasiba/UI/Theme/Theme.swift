import SwiftUI

public enum UiThemeMode: String, CaseIterable {
    case modernGlass = "Modern Glass"
    case oledDark = "OLED Dark"
    case classicMidnight = "Classic Midnight"
    case neonCyberpunk = "Neon Cyberpunk"

    public var title: String { rawValue }
    public var badge: String {
        switch self {
        case .modernGlass: return "GLASS"
        case .oledDark: return "OLED"
        case .classicMidnight: return "NAVY"
        case .neonCyberpunk: return "CYBER"
        }
    }
}

// Colors for default Modern Glass
public let UiBackground = Color.black
public let UiBackgroundMid = Color(hex: 0x0A0A0A)
public let UiBackgroundEnd = Color.black
public let UiSurface = Color(hex: 0x0C0C0E)
public let UiSurfaceElevated = Color(hex: 0x141418)
public let UiBorder = Color.white.opacity(0.11)

public let AccentTeal = Color.white
public let AccentTealStrong = Color.white
public let AccentGlow = Color.white.opacity(0.2)

public let TextStrong = Color(hex: 0xF8FAFC)
public let TextMuted = Color(hex: 0xE2E8F0).opacity(0.72)
public let TextFaint = Color(hex: 0x94A3B8).opacity(0.6)

public func getBackgroundGradient(mode: UiThemeMode) -> LinearGradient {
    return LinearGradient(
        colors: [Color.black, Color.black],
        startPoint: .top,
        endPoint: .bottom
    )
}

public func getCategoryAccentColor(id: String) -> Color {
    switch id.lowercased() {
    case "workout": return Color(hex: 0xFB923C)
    case "relax": return Color(hex: 0x22D3EE)
    case "energize": return Color(hex: 0xF43F5E)
    case "feelgood": return Color(hex: 0x84CC16)
    case "romance": return Color(hex: 0xFB7185)
    case "party": return Color(hex: 0xD946EF)
    case "commute": return Color(hex: 0xF87171)
    case "sleep": return Color(hex: 0x818CF8)
    case "focus": return Color(hex: 0x38BDF8)
    case "gaming": return Color(hex: 0xA855F7)
    case "podcasts": return Color(hex: 0x94A3B8)
    case "hiphop": return Color(hex: 0xEAB308)
    case "rock": return Color(hex: 0xEF4444)
    case "bollywood": return Color(hex: 0xFFFF007F)
    case "chill": return Color(hex: 0x2DD4BF)
    case "dance": return Color(hex: 0x06B6D4)
    default: return AccentTeal
    }
}

extension UiThemeMode: Identifiable {
    public var id: String { rawValue }
    public static let MODERN_GLASS = UiThemeMode.modernGlass
    public static let OLED_DARK = UiThemeMode.oledDark
    public static let CLASSIC_MIDNIGHT = UiThemeMode.classicMidnight
    public static let NEON_CYBERPUNK = UiThemeMode.neonCyberpunk
}

public struct SpasibaTheme {
    public static let background = UiBackground
    public static let backgroundMid = UiBackgroundMid
    public static let backgroundEnd = UiBackgroundEnd
    public static let surface = UiSurface
    public static let surfaceElevated = UiSurfaceElevated
    public static let border = UiBorder
    public static let accent = AccentTeal
    public static let accentGlow = AccentGlow
    public static let silverGradient = LinearGradient(
        colors: [Color.white, Color(hex: 0xD1D5DB), Color(hex: 0x9CA3AF)],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
    public static let textStrong = TextStrong
    public static let textMuted = TextMuted
    public static let textFaint = TextFaint
    public static func categoryAccentColor(for id: String) -> Color {
        getCategoryAccentColor(id: id)
    }
}

extension Color {
    public init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }
}

public enum DeviceSafeArea {
    public static var top: CGFloat {
        let windowScene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene ?? UIApplication.shared.connectedScenes.first as? UIWindowScene
        if let window = windowScene?.windows.first(where: { $0.isKeyWindow }) ?? windowScene?.windows.first {
            return window.safeAreaInsets.top
        }
        return 44
    }

    public static var bottom: CGFloat {
        let windowScene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene ?? UIApplication.shared.connectedScenes.first as? UIWindowScene
        if let window = windowScene?.windows.first(where: { $0.isKeyWindow }) ?? windowScene?.windows.first {
            return window.safeAreaInsets.bottom
        }
        return 34
    }
}
