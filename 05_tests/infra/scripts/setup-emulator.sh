#!/bin/bash
# =============================================================================
# setup-emulator.sh — Idempotent Android Emulator Setup
# =============================================================================
# ASPICE: Supports L2_SWE5_integration and L3_SWE6_qualification test execution
#
# This script:
#   1. Checks KVM availability (required for emulator performance)
#   2. Installs emulator package if missing
#   3. Installs system image (API 30 / Android 11) if missing
#   4. Creates AVD (DrivingCoach_Test) if missing
#
# Configuration:
#   - API Level: 30 (Android 11 - wide compatibility)
#   - Device Profile: pixel_4
#   - ABI: x86_64
#
# Usage:
#   ./setup-emulator.sh
#
# Exit Codes:
#   0 - Success (emulator ready)
#   1 - KVM not available
#   2 - ANDROID_SDK_ROOT not set
#   3 - SDK manager failed
#   4 - AVD creation failed
# =============================================================================

set -e

# Configuration
API_LEVEL="30"
SYSTEM_IMAGE="system-images;android-${API_LEVEL};google_apis;x86_64"
AVD_NAME="DrivingCoach_Test"
DEVICE_PROFILE="pixel_4"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# =============================================================================
# Step 1: Check KVM availability
# =============================================================================
log_info "Checking KVM availability..."

if [ ! -e /dev/kvm ]; then
    log_error "KVM not available. /dev/kvm does not exist."
    log_error "Emulator requires hardware acceleration."
    log_error "On Linux: sudo apt install qemu-kvm && sudo adduser \$USER kvm"
    exit 1
fi

if [ ! -r /dev/kvm ] || [ ! -w /dev/kvm ]; then
    log_error "KVM exists but no read/write permission."
    log_error "Run: sudo chmod 666 /dev/kvm"
    log_error "Or add user to kvm group: sudo adduser \$USER kvm"
    exit 1
fi

log_info "KVM is available and accessible."

# =============================================================================
# Step 2: Check ANDROID_SDK_ROOT
# =============================================================================
log_info "Checking Android SDK..."

# Try common SDK locations if not set
if [ -z "$ANDROID_SDK_ROOT" ]; then
    if [ -d "$HOME/android-sdk" ]; then
        export ANDROID_SDK_ROOT="$HOME/android-sdk"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
    elif [ -d "/opt/android-sdk" ]; then
        export ANDROID_SDK_ROOT="/opt/android-sdk"
    else
        log_error "ANDROID_SDK_ROOT not set and could not find SDK."
        log_error "Set ANDROID_SDK_ROOT environment variable."
        exit 2
    fi
    log_warn "ANDROID_SDK_ROOT not set, using: $ANDROID_SDK_ROOT"
fi

if [ ! -d "$ANDROID_SDK_ROOT" ]; then
    log_error "ANDROID_SDK_ROOT directory does not exist: $ANDROID_SDK_ROOT"
    exit 2
fi

log_info "Using Android SDK at: $ANDROID_SDK_ROOT"

# Set up paths
SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"

# Fallback to older cmdline-tools location
if [ ! -f "$SDKMANAGER" ]; then
    SDKMANAGER="$ANDROID_SDK_ROOT/tools/bin/sdkmanager"
    AVDMANAGER="$ANDROID_SDK_ROOT/tools/bin/avdmanager"
fi

if [ ! -f "$SDKMANAGER" ]; then
    log_error "sdkmanager not found. Install Android SDK command-line tools."
    exit 2
fi

# =============================================================================
# Step 3: Install emulator package if missing
# =============================================================================
log_info "Checking emulator package..."

if [ ! -f "$EMULATOR" ]; then
    log_info "Installing emulator package..."
    yes | "$SDKMANAGER" "emulator" || {
        log_error "Failed to install emulator package."
        exit 3
    }
    log_info "Emulator package installed."
else
    log_info "Emulator package already installed."
fi

# =============================================================================
# Step 4: Install system image if missing
# =============================================================================
log_info "Checking system image: $SYSTEM_IMAGE"

SYSTEM_IMAGE_DIR="$ANDROID_SDK_ROOT/system-images/android-${API_LEVEL}/google_apis/x86_64"

if [ ! -d "$SYSTEM_IMAGE_DIR" ]; then
    log_info "Installing system image (this may take a while, ~1.5GB)..."
    yes | "$SDKMANAGER" "$SYSTEM_IMAGE" || {
        log_error "Failed to install system image."
        exit 3
    }
    log_info "System image installed."
else
    log_info "System image already installed."
fi

# =============================================================================
# Step 5: Create AVD if missing
# =============================================================================
log_info "Checking AVD: $AVD_NAME"

# Check if AVD exists
AVD_LIST=$("$AVDMANAGER" list avd 2>/dev/null | grep "Name: $AVD_NAME" || true)

if [ -z "$AVD_LIST" ]; then
    log_info "Creating AVD: $AVD_NAME (Pixel 4, API $API_LEVEL)..."
    
    echo "no" | "$AVDMANAGER" create avd \
        --name "$AVD_NAME" \
        --package "$SYSTEM_IMAGE" \
        --device "$DEVICE_PROFILE" \
        --force || {
        log_error "Failed to create AVD."
        exit 4
    }
    
    log_info "AVD created: $AVD_NAME"
else
    log_info "AVD already exists: $AVD_NAME"
fi

# =============================================================================
# Summary
# =============================================================================
echo ""
log_info "=========================================="
log_info "Emulator setup complete!"
log_info "=========================================="
echo ""
echo "  SDK:          $ANDROID_SDK_ROOT"
echo "  System Image: $SYSTEM_IMAGE"
echo "  AVD Name:     $AVD_NAME"
echo "  Device:       $DEVICE_PROFILE"
echo ""
echo "Next steps:"
echo "  1. Start emulator: ./start-emulator.sh"
echo "  2. Run tests:      ./run-instrumented.sh"
echo ""

exit 0
