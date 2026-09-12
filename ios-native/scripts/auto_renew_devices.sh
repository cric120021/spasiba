#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

echo "=== Spasiba Auto-Renew Started: $(date) ==="

# 1. Rebuild and refresh provisioning profiles via Xcode Developer Services
xcodebuild -project "Spasiba.xcodeproj" -scheme "Spasiba" -destination "id=4654717E-424D-5817-9AA4-57A7A2983492" -allowProvisioningUpdates build | grep -E '\*\* BUILD|error:' || true

APP_PATH="/Users/jigar/Library/Developer/Xcode/DerivedData/Spasiba-fegvcpqbrzkhkmafrpkrwzfvgkjw/Build/Products/Debug-iphoneos/Spasiba.app"

if [ ! -d "$APP_PATH" ]; then
    echo "Error: App build not found at $APP_PATH"
    exit 1
fi

DEVICES=(
    "4654717E-424D-5817-9AA4-57A7A2983492" # Simran's iPhone
    "678D49E0-19B5-5115-9804-B1340A3D29BB" # Jigar's iPad
    "345AFF88-CE33-52B3-9AC8-7C4635D205FE" # Jigar's iPhone
)

DEVICE_LIST=$(xcrun devicectl list devices 2>/dev/null || true)

for DEV_ID in "${DEVICES[@]}"; do
    DEV_LINE=$(echo "$DEVICE_LIST" | grep "$DEV_ID" || true)
    if [ -z "$DEV_LINE" ]; then
        continue
    fi

    if echo "$DEV_LINE" | grep -qE "connected|available \(paired\)"; then
        echo "Device $DEV_ID is online. Silently renewing certificate & app over Wi-Fi..."
        if xcrun devicectl device install app --device "$DEV_ID" "$APP_PATH" >/dev/null 2>&1; then
            echo "✅ Successfully renewed $DEV_ID"
        else
            echo "⚠️ Could not install to $DEV_ID"
        fi
    else
        echo "Device $DEV_ID is currently offline."
    fi
done

# 3. Check for legacy / USB devices via ios-deploy
if command -v /opt/homebrew/bin/ios-deploy >/dev/null 2>&1; then
    USB_DEV=$(/opt/homebrew/bin/ios-deploy -c --timeout 2 2>/dev/null | grep "Found" || true)
    if [ -n "$USB_DEV" ]; then
        echo "USB Device detected: $USB_DEV"
        echo "Installing to USB device..."
        if /opt/homebrew/bin/ios-deploy --bundle "$APP_PATH" --no-wifi >/dev/null 2>&1; then
            echo "✅ Successfully renewed USB device"
        else
            echo "⚠️ Could not install to USB device"
        fi
    fi
fi

echo "=== Spasiba Auto-Renew Finished: $(date) ==="
