#!/bin/bash
# =============================================================================
# stop-emulator.sh — Graceful Android Emulator Shutdown
# =============================================================================
# ASPICE: Supports L2_SWE5_integration and L3_SWE6_qualification test execution
#
# This script:
#   1. Finds running emulator via ADB
#   2. Sends graceful shutdown command
#   3. Waits for process to exit
#   4. Cleans up PID file
#
# Usage:
#   ./stop-emulator.sh [--force]
#
# Options:
#   --force   Force kill if graceful shutdown fails
#
# Exit Codes:
#   0 - Success (emulator stopped)
#   1 - No emulator running
# =============================================================================

set -e

FORCE_KILL=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --force)
            FORCE_KILL=true
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
# Find SDK
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
    ADB="adb"  # Try system PATH
fi

# =============================================================================
# Find running emulator
# =============================================================================
log_info "Looking for running emulator..."

DEVICE=$("$ADB" devices 2>/dev/null | grep -E "^emulator-[0-9]+" | head -1 || true)

if [ -z "$DEVICE" ]; then
    log_info "No emulator running."
    
    # Clean up stale PID file
    if [ -f /tmp/driving_coach_emulator.pid ]; then
        rm -f /tmp/driving_coach_emulator.pid
    fi
    
    exit 0
fi

DEVICE_ID=$(echo "$DEVICE" | awk '{print $1}')
log_info "Found emulator: $DEVICE_ID"

# =============================================================================
# Graceful shutdown via ADB
# =============================================================================
log_info "Sending shutdown command..."

"$ADB" -s "$DEVICE_ID" emu kill 2>/dev/null || {
    log_warn "ADB emu kill failed. Trying alternative method..."
}

# =============================================================================
# Wait for emulator to exit
# =============================================================================
log_info "Waiting for emulator to exit..."

WAIT_TIMEOUT=30
WAIT_START=$(date +%s)

while true; do
    RUNNING=$("$ADB" devices 2>/dev/null | grep -E "^emulator-[0-9]+" || true)
    
    if [ -z "$RUNNING" ]; then
        log_info "Emulator stopped."
        break
    fi
    
    ELAPSED=$(($(date +%s) - WAIT_START))
    if [ $ELAPSED -ge $WAIT_TIMEOUT ]; then
        if [ "$FORCE_KILL" = true ]; then
            log_warn "Graceful shutdown timed out. Force killing..."
            
            if [ -f /tmp/driving_coach_emulator.pid ]; then
                PID=$(cat /tmp/driving_coach_emulator.pid)
                kill -9 "$PID" 2>/dev/null || true
            fi
            break
        else
            log_error "Shutdown timed out. Use --force to force kill."
            exit 1
        fi
    fi
    
    echo -ne "\r  Waiting... ${ELAPSED}s / ${WAIT_TIMEOUT}s"
    sleep 1
done

echo ""

# =============================================================================
# Clean up
# =============================================================================
if [ -f /tmp/driving_coach_emulator.pid ]; then
    rm -f /tmp/driving_coach_emulator.pid
    log_info "Cleaned up PID file."
fi

# =============================================================================
# Summary
# =============================================================================
echo ""
log_info "=========================================="
log_info "Emulator stopped successfully."
log_info "=========================================="

exit 0
