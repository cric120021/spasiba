import SwiftUI

public struct EqualizerSleepTimerModal: View {
    public let currentSpeed: Float
    public let currentSleepTimerMinutes: Int
    public let onDismiss: () -> Void
    public let onSelectSpeed: (Float) -> Void
    public let onSelectSleepTimer: (Int) -> Void

    public init(
        currentSpeed: Float = 1.0,
        currentSleepTimerMinutes: Int = 0,
        onDismiss: @escaping () -> Void = {},
        onSelectSpeed: @escaping (Float) -> Void = { _ in },
        onSelectSleepTimer: @escaping (Int) -> Void = { _ in }
    ) {
        self.currentSpeed = currentSpeed
        self.currentSleepTimerMinutes = currentSleepTimerMinutes
        self.onDismiss = onDismiss
        self.onSelectSpeed = onSelectSpeed
        self.onSelectSleepTimer = onSelectSleepTimer
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Drag Handle Bar
            HStack {
                Spacer()
                RoundedRectangle(cornerRadius: 2.5)
                    .fill(Color.white.opacity(0.2))
                    .frame(width: 36, height: 5)
                Spacer()
            }
            .padding(.top, 12)
            .padding(.bottom, 16)

            Text("Audio & Playback Controls")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(SpasibaTheme.textStrong)

            Spacer().frame(height: 20)

            // Playback Speed Section
            Text("PLAYBACK SPEED")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(SpasibaTheme.textMuted)
                .tracking(1.0)

            Spacer().frame(height: 8)

            HStack(spacing: 8) {
                ForEach([Float(0.75), Float(1.0), Float(1.25), Float(1.5), Float(2.0)], id: \.self) { speed in
                    let isSelected = abs(currentSpeed - speed) < 0.01
                    Button(action: {
                        onSelectSpeed(speed)
                    }) {
                        Text(String(format: speed == 1.0 || speed == 2.0 ? "%.1fx" : "%.2fx", speed))
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(isSelected ? SpasibaTheme.surfaceElevated : SpasibaTheme.textStrong)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(isSelected ? SpasibaTheme.accent : SpasibaTheme.surfaceElevated)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }

            Spacer().frame(height: 24)

            // Sleep Timer Section
            Text("SLEEP TIMER")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(SpasibaTheme.textMuted)
                .tracking(1.0)

            Spacer().frame(height: 8)

            HStack(spacing: 8) {
                ForEach([0, 15, 30, 45, 60], id: \.self) { minutes in
                    let isSelected = currentSleepTimerMinutes == minutes
                    Button(action: {
                        onSelectSleepTimer(minutes)
                    }) {
                        Text(minutes == 0 ? "Off" : "\(minutes)m")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(isSelected ? SpasibaTheme.surfaceElevated : SpasibaTheme.textStrong)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(isSelected ? SpasibaTheme.accent : SpasibaTheme.surfaceElevated)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }

            Spacer().frame(height: 24)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(SpasibaTheme.surfaceElevated.ignoresSafeArea())
    }
}
