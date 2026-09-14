#!/bin/bash
# =============================================================================
# start-emulator.sh — Start Headless Android Emulator
# =============================================================================
# ASPICE: Supports L2_SWE5_integration and L3_SWE6_qualification test execution
#
# This script:
#   1. Checks if emulator is already running
#   2. Starts emulator in headless mode (no window)
#   3. Waits for boot completion with timeout
#   4. Verifies ADB connection
#
# Usage:
#   ./start-emulator.sh [--timeout SECONDS]
#
# Options:
#   --timeout SECONDS   Boot timeout in seconds (default: 120)
#
# Exit Codes:
#   0 - Success (emulator running and ready)
#   1 - Emulator binary not found
#   2 - AVD not found
#   3 - Boot timeout
#   4 - ADB connection failed
# =============================================================================

set -e

# Configuration
AVD_NAME="DrivingCoach_Test"
BOOT_TIMEOUT=120

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --timeout)
            BOOT_TIMEOUT="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# =============================================================================
# Find SDK and emulator
# =============================================================================
if [ -z "$ANDROID_SDK_ROOT" ]; then
    if [ -d "$HOME/android-sdk" ]; then
        export ANDROID_SDK_ROOT="$HOME/android-sdk"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
    fi
fi

EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"

if [ ! -f "$EMULATOR" ]; then
    log_error "Emulator not found at: $EMULATOR"
    log_error "Run setup-emulator.sh first."
    exit 1
fi

if [ ! -f "$ADB" ]; then
    log_error "ADB not found at: $ADB"
    exit 1
fi

# =============================================================================
# Check if emulator is already running
# =============================================================================
log_info "Checking for running emulator..."

RUNNING_EMULATOR=$("$ADB" devices 2>/dev/null | grep -E "^emulator-[0-9]+" | head -1 || true)

if [ -n "$RUNNING_EMULATOR" ]; then
    DEVICE_ID=$(echo "$RUNNING_EMULATOR" | awk '{print $1}')
    log_info "Emulator already running: $DEVICE_ID"
    
    # Verify it's ready
    BOOT_COMPLETE=$("$ADB" -s "$DEVICE_ID" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || echo "")
    
    if [ "$BOOT_COMPLETE" = "1" ]; then
        log_info "Emulator is ready."
        exit 0
    else
        log_warn "Emulator running but not fully booted. Waiting..."
    fi
fi

# =============================================================================
# Check AVD exists
# =============================================================================
AVDMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
if [ ! -f "$AVDMANAGER" ]; then
    AVDMANAGER="$ANDROID_SDK_ROOT/tools/bin/avdmanager"
fi

AVD_EXISTS=$("$AVDMANAGER" list avd 2>/dev/null | grep "Name: $AVD_NAME" || true)

if [ -z "$AVD_EXISTS" ]; then
    log_error "AVD not found: $AVD_NAME"
    log_error "Run setup-emulator.sh first."
    exit 2
fi

# =============================================================================
# Start emulator in headless mode
# =============================================================================
log_info "Starting emulator: $AVD_NAME (headless mode)"
log_info "Boot timeout: ${BOOT_TIMEOUT}s"

# Start emulator in background
"$EMULATOR" -avd "$AVD_NAME" \
    -no-window \
    -no-audio \
    -no-boot-anim \
    -gpu swiftshader_indirect \
    -no-snapshot-save \
    -wipe-data \
    2>/dev/null &

EMULATOR_PID=$!
echo "$EMULATOR_PID" > /tmp/driving_coach_emulator.pid

log_info "Emulator started with PID: $EMULATOR_PID"

# =============================================================================
# Wait for ADB device
# =============================================================================
log_info "Waiting for ADB device..."

WAIT_START=$(date +%s)

while true; do
    DEVICE=$("$ADB" devices 2>/dev/null | grep -E "^emulator-[0-9]+\s+device" | head -1 || true)
    
    if [ -n "$DEVICE" ]; then
        DEVICE_ID=$(echo "$DEVICE" | awk '{print $1}')
        log_info "ADB device found: $DEVICE_ID"
        break
    fi
    
    ELAPSED=$(($(date +%s) - WAIT_START))
    if [ $ELAPSED -ge $BOOT_TIMEOUT ]; then
        log_error "Timeout waiting for ADB device."
        kill $EMULATOR_PID 2>/dev/null || true
        exit 3
    fi
    
    echo -ne "\r  Waiting... ${ELAPSED}s / ${BOOT_TIMEOUT}s"
    sleep 2
done

echo ""

# =============================================================================
# Wait for boot completion
# =============================================================================
log_info "Waiting for boot completion..."

while true; do
    BOOT_COMPLETE=$("$ADB" -s "$DEVICE_ID" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || echo "")
    
    if [ "$BOOT_COMPLETE" = "1" ]; then
        log_info "Boot completed!"
        break
    fi
    
    ELAPSED=$(($(date +%s) - WAIT_START))
    if [ $ELAPSED -ge $BOOT_TIMEOUT ]; then
        log_error "Timeout waiting for boot completion."
        log_error "Emulator may still be booting. Check with: adb devices"
        exit 3
    fi
    
    echo -ne "\r  Booting... ${ELAPSED}s / ${BOOT_TIMEOUT}s"
    sleep 2
done

echo ""

# =============================================================================
# Final verification
# =============================================================================
log_info "Verifying emulator is responsive..."

"$ADB" -s "$DEVICE_ID" shell "echo 'Emulator ready'" || {
    log_error "Emulator not responding to shell commands."
    exit 4
}

# =============================================================================
# Disable animations (required by Espresso)
# =============================================================================
# Espresso synchronises by waiting for the UI thread to go idle. An animation
# that never ends -- an indeterminate ProgressBar, for instance -- means the
# thread is never idle, and the test hangs forever rather than failing. The
# Android test docs require these scales to be zero on any device running
# Espresso; without them, screens such as Session Result (which shows an
# upload spinner) deadlock the whole suite.
log_info "Disabling animations for Espresso..."
for SCALE in window_animation_scale transition_animation_scale animator_duration_scale; do
    "$ADB" -s "$DEVICE_ID" shell settings put global "$SCALE" 0 || \
        log_warn "Could not set $SCALE; Espresso tests may hang on animated views."
done

# =============================================================================
# Summary
# =============================================================================
echo ""
log_info "=========================================="
log_info "Emulator is ready!"
log_info "=========================================="
echo ""
echo "  Device:    $DEVICE_ID"
echo "  AVD:       $AVD_NAME"
echo "  PID:       $EMULATOR_PID"
echo "  PID File:  /tmp/driving_coach_emulator.pid"
echo ""
echo "Next steps:"
echo "  Run tests: ./run-instrumented.sh"
echo "  Stop:      ./stop-emulator.sh"
echo ""

exit 0
