#!/bin/bash
# =============================================================================
# run-instrumented.sh — Run L2 Integration Tests on Device/Emulator
# =============================================================================
# ASPICE: L2_SWE5_integration test execution
#
# This script:
#   1. Checks for connected device or running emulator
#   2. Optionally starts emulator if none available
#   3. Runs instrumented tests via Gradle
#   4. Returns test exit code
#
# Usage:
#   ./run-instrumented.sh [--start-emulator] [--stop-after]
#
# Options:
#   --start-emulator  Start emulator if no device connected
#   --stop-after      Stop emulator after tests complete
#
# Exit Codes:
#   0 - All tests passed
#   1 - Tests failed
#   2 - No device available (and --start-emulator not specified)
#   3 - Emulator setup/start failed
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

START_EMULATOR=false
STOP_AFTER=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --start-emulator)
            START_EMULATOR=true
            shift
            ;;
        --stop-after)
            STOP_AFTER=true
            shift
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
# Find ADB
# =============================================================================
if [ -z "$ANDROID_SDK_ROOT" ]; then
    if [ -d "$HOME/android-sdk" ]; then
        export ANDROID_SDK_ROOT="$HOME/android-sdk"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
    fi
fi

ADB="$ANDROID_SDK_ROOT/platform-tools/adb"

if [ ! -f "$ADB" ]; then
    ADB="adb"
fi

# =============================================================================
# Check for device/emulator
# =============================================================================
log_info "Checking for connected device or emulator..."

DEVICE=$("$ADB" devices 2>/dev/null | grep -E "device$" | head -1 || true)

if [ -z "$DEVICE" ]; then
    log_warn "No device or emulator connected."
    
    if [ "$START_EMULATOR" = true ]; then
        log_info "Starting emulator..."
        
        # Run setup if needed
        "$SCRIPT_DIR/setup-emulator.sh" || {
            log_error "Emulator setup failed."
            exit 3
        }
        
        # Start emulator
        "$SCRIPT_DIR/start-emulator.sh" || {
            log_error "Emulator start failed."
            exit 3
        }
        
        DEVICE=$("$ADB" devices 2>/dev/null | grep -E "device$" | head -1 || true)
    else
        log_error "No device available. Use --start-emulator to start one."
        exit 2
    fi
fi

DEVICE_ID=$(echo "$DEVICE" | awk '{print $1}')
log_info "Using device: $DEVICE_ID"

# =============================================================================
# Run instrumented tests
# =============================================================================
log_info "Running L2_SWE5 instrumented tests..."
echo ""

cd "$PROJECT_ROOT"

TEST_EXIT_CODE=0
./gradlew connectedDebugAndroidTest --no-daemon || TEST_EXIT_CODE=$?

# =============================================================================
# Stop emulator if requested
# =============================================================================
if [ "$STOP_AFTER" = true ]; then
    log_info "Stopping emulator..."
    "$SCRIPT_DIR/stop-emulator.sh" || true
fi

# =============================================================================
# Summary
# =============================================================================
echo ""
if [ $TEST_EXIT_CODE -eq 0 ]; then
    log_info "=========================================="
    log_info "L2_SWE5 Integration tests PASSED"
    log_info "=========================================="
else
    log_error "=========================================="
    log_error "L2_SWE5 Integration tests FAILED"
    log_error "=========================================="
fi

echo ""
echo "Report location: $PROJECT_ROOT/app/build/reports/androidTests/connected/"
echo ""

exit $TEST_EXIT_CODE
