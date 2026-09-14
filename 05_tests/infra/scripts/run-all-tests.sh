#!/bin/bash
# =============================================================================
# run-all-tests.sh — Master Test Orchestrator
# =============================================================================
# ASPICE: Executes all automated test levels and generates consolidated report
#
# Test Levels:
#   L1_SWE4_unit         - Always runs (JVM)
#   L2_SWE5_integration  - Runs if device/emulator available
#   L3_SWE6_qualification - Future (not yet implemented)
#   L4_SYS5_acceptance   - Manual only (skipped)
#
# Usage:
#   ./run-all-tests.sh [OPTIONS]
#
# Options:
#   --level LEVEL        Run specific level only (L1, L2, L3, all)
#   --start-emulator     Start emulator if no device for L2
#   --stop-emulator      Stop emulator after L2 tests
#   --skip-report        Skip report generation
#   --help               Show this help
#
# Exit Codes:
#   0 - All executed tests passed
#   1 - One or more tests failed
#   2 - Invalid arguments
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

# Options
RUN_LEVEL="all"
START_EMULATOR=false
STOP_EMULATOR=false
SKIP_REPORT=false
NO_PROMPT=false

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_header() { echo -e "\n${BLUE}════════════════════════════════════════════════════════════${NC}"; echo -e "${BLUE}  $1${NC}"; echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}\n"; }

# =============================================================================
# Parse arguments
# =============================================================================
show_help() {
    cat << EOF
Usage: ./run-all-tests.sh [OPTIONS]

Test Orchestrator for Driving Coach (ASPICE-aligned)

Options:
  --level LEVEL        Run specific level only
                       L1  - Unit tests only
                       L2  - Integration tests only (requires device)
                       L3  - SW Qualification tests (not implemented)
                       all - Run L1 and L2 (default)
  
  --start-emulator     Start emulator if no device connected (for L2)
  --stop-emulator      Stop emulator after tests complete
  --skip-report        Skip generating test report
  --no-prompt          Never ask anything; for CI
  --help               Show this help message

Examples:
  ./run-all-tests.sh                     # Run L1 + L2 (if device), generate report
  ./run-all-tests.sh --level L1          # Run unit tests only
  ./run-all-tests.sh --start-emulator    # Start emulator for L2 tests
  ./run-all-tests.sh --level L2 --start-emulator --stop-emulator

Test Levels (ASPICE):
  L1_SWE4_unit         - Unit Verification (JVM)
  L2_SWE5_integration  - Integration Test (Device/Emulator)
  L3_SWE6_qualification - SW Qualification Test (Future)
  L4_SYS5_acceptance   - System Qualification (Human/Manual)

EOF
}

while [[ $# -gt 0 ]]; do
    case $1 in
        --level)
            RUN_LEVEL="$2"
            shift 2
            ;;
        --start-emulator)
            START_EMULATOR=true
            shift
            ;;
        --stop-emulator)
            STOP_EMULATOR=true
            shift
            ;;
        --skip-report)
            SKIP_REPORT=true
            shift
            ;;
        --no-prompt)
            NO_PROMPT=true
            shift
            ;;
        --help)
            show_help
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            show_help
            exit 2
            ;;
    esac
done

# Validate level
case $RUN_LEVEL in
    L1|L2|L3|all)
        ;;
    *)
        log_error "Invalid level: $RUN_LEVEL"
        log_error "Valid levels: L1, L2, L3, all"
        exit 2
        ;;
esac

# =============================================================================
# Setup
# =============================================================================
log_header "Driving Coach Test Suite"

echo "Configuration:"
echo "  Level:          $RUN_LEVEL"
echo "  Start Emulator: $START_EMULATOR"
echo "  Stop Emulator:  $STOP_EMULATOR"
echo "  Generate Report: $([ "$SKIP_REPORT" = true ] && echo "No" || echo "Yes")"
echo ""

cd "$PROJECT_ROOT"

# Track results
L1_RESULT="skipped"
L2_RESULT="skipped"
L3_RESULT="skipped"
OVERALL_EXIT_CODE=0

# =============================================================================
# L1: Unit Tests
# =============================================================================
if [ "$RUN_LEVEL" = "L1" ] || [ "$RUN_LEVEL" = "all" ]; then
    log_header "L1_SWE4_unit — Unit Tests"
    
    log_info "Running unit tests..."
    
    if ./gradlew testDebugUnitTest --no-daemon; then
        L1_RESULT="passed"
        log_info "L1 Unit tests: PASSED"
    else
        L1_RESULT="failed"
        OVERALL_EXIT_CODE=1
        log_error "L1 Unit tests: FAILED"
    fi
else
    log_info "L1 Unit tests: SKIPPED (not in run level)"
fi

# =============================================================================
# L2: Integration Tests
# =============================================================================
if [ "$RUN_LEVEL" = "L2" ] || [ "$RUN_LEVEL" = "all" ]; then
    log_header "L2_SWE5_integration — Integration Tests"
    
    # Find ADB
    if [ -z "$ANDROID_SDK_ROOT" ]; then
        if [ -d "$HOME/android-sdk" ]; then
            export ANDROID_SDK_ROOT="$HOME/android-sdk"
        elif [ -d "$HOME/Android/Sdk" ]; then
            export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
        fi
    fi
    
    ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
    [ ! -f "$ADB" ] && ADB="adb"
    
    # Check for device
    DEVICE=$("$ADB" devices 2>/dev/null | grep -E "device$" | head -1 || true)
    
    if [ -z "$DEVICE" ]; then
        if [ "$START_EMULATOR" = true ]; then
            log_info "No device connected. Starting emulator..."
            
            "$SCRIPT_DIR/setup-emulator.sh" || {
                log_error "Emulator setup failed."
                L2_RESULT="error"
                OVERALL_EXIT_CODE=1
            }
            
            if [ "$L2_RESULT" != "error" ]; then
                "$SCRIPT_DIR/start-emulator.sh" || {
                    log_error "Emulator start failed."
                    L2_RESULT="error"
                    OVERALL_EXIT_CODE=1
                }
            fi
            
            # Reset L2_RESULT to allow test execution after successful emulator start
            if [ "$L2_RESULT" != "error" ]; then
                L2_RESULT="pending"
            fi
        else
            log_warn "No device or emulator connected."
            log_warn "Use --start-emulator to start one, or connect a device."
            L2_RESULT="skipped"
        fi
    else
        # Device already connected, mark as pending for test execution
        L2_RESULT="pending"
    fi
    
    # Run integration tests if device available and no error
    if [ "$L2_RESULT" != "error" ] && [ "$L2_RESULT" != "skipped" ]; then
        log_info "Running integration tests..."
        
        if ./gradlew connectedDebugAndroidTest --no-daemon; then
            L2_RESULT="passed"
            log_info "L2 Integration tests: PASSED"
        else
            L2_RESULT="failed"
            OVERALL_EXIT_CODE=1
            log_error "L2 Integration tests: FAILED"
        fi
    fi
    
    # Stop emulator if requested
    if [ "$STOP_EMULATOR" = true ]; then
        log_info "Stopping emulator..."
        "$SCRIPT_DIR/stop-emulator.sh" || true
    fi
else
    log_info "L2 Integration tests: SKIPPED (not in run level)"
fi

# =============================================================================
# L3: SW Qualification Tests (Future)
# =============================================================================
if [ "$RUN_LEVEL" = "L3" ] || [ "$RUN_LEVEL" = "all" ]; then
    log_header "L3_SWE6_qualification — SW Qualification Tests"
    
    log_warn "L3 SW Qualification tests not yet implemented."
    log_warn "This level will include E2E tests with simulated GPS data."
    L3_RESULT="not_implemented"
fi

# =============================================================================
# Generate Report
# =============================================================================
RUN_DIR=""
if [ "$SKIP_REPORT" != true ]; then
    log_header "Generating Test Report"

    # Every run gets its own directory, so a report is always tied to the run
    # that produced it and nothing is overwritten.
    RUN_DIR="$PROJECT_ROOT/05_tests/reports/RUN_$(date +%Y%m%d_%H%M%S)"
    mkdir -p "$RUN_DIR"

    "$SCRIPT_DIR/generate-report.sh" --output "$RUN_DIR" || {
        log_warn "Markdown report generation had issues, but continuing..."
    }
    # generate-report.sh names its file TEST_REPORT_<timestamp>.md; give the
    # run directory a stable name as well so packaging never has to guess.
    LATEST_MD="$(ls -t "$RUN_DIR"/TEST_REPORT_*.md 2>/dev/null | head -1)"
    [ -n "$LATEST_MD" ] && cp "$LATEST_MD" "$RUN_DIR/TEST_REPORT.md"

    python3 "$SCRIPT_DIR/generate-html-report.py" --output "$RUN_DIR/TEST_REPORT.html" || {
        log_warn "HTML report generation had issues, but continuing..."
    }

    log_info "Run directory: ${RUN_DIR#$PROJECT_ROOT/}"
fi

# =============================================================================
# Summary
# =============================================================================
log_header "Test Execution Summary"

echo "Results:"
echo ""
printf "  %-25s %s\n" "L1_SWE4_unit:" "$(case $L1_RESULT in passed) echo "✅ PASSED";; failed) echo "❌ FAILED";; *) echo "⏭️ SKIPPED";; esac)"
printf "  %-25s %s\n" "L2_SWE5_integration:" "$(case $L2_RESULT in passed) echo "✅ PASSED";; failed) echo "❌ FAILED";; error) echo "❌ ERROR";; *) echo "⏭️ SKIPPED";; esac)"
printf "  %-25s %s\n" "L3_SWE6_qualification:" "$(case $L3_RESULT in passed) echo "✅ PASSED";; failed) echo "❌ FAILED";; *) echo "⏭️ NOT IMPLEMENTED";; esac)"
printf "  %-25s %s\n" "L4_SYS5_acceptance:" "⏭️ MANUAL ONLY"
echo ""

if [ $OVERALL_EXIT_CODE -eq 0 ]; then
    log_info "Overall: ✅ ALL EXECUTED TESTS PASSED"
else
    log_error "Overall: ❌ SOME TESTS FAILED"
fi

echo ""
if [ -n "$RUN_DIR" ]; then
    echo "Report: ${RUN_DIR#$PROJECT_ROOT/}/TEST_REPORT.html"
else
    echo "Report: skipped"
fi
echo ""

# =============================================================================
# Optional local release
# =============================================================================
# Only offered interactively, only when the run is clean, and only when there
# is a report to ship. A release that carries a report from a failed run would
# be worse than no report at all.
if [ "$NO_PROMPT" != true ] && [ -t 0 ] && [ $OVERALL_EXIT_CODE -eq 0 ] && [ -n "$RUN_DIR" ]; then
    log_header "Local Release"
    echo "All executed tests passed. A local release would build the APK and place it"
    echo "next to this run's HTML test report under releases/."
    echo ""
    read -r -p "Create a local release now? [y/N] " REPLY
    case "$REPLY" in
        [yY]|[yY][eE][sS])
            "$SCRIPT_DIR/package-release.sh" --report "$RUN_DIR/TEST_REPORT.html" || {
                log_error "Release packaging failed"
                exit 1
            }
            ;;
        *)
            echo ""
            log_info "No release created. To do it later:"
            echo "  ./05_tests/infra/scripts/package-release.sh --report ${RUN_DIR#$PROJECT_ROOT/}/TEST_REPORT.html"
            ;;
    esac
elif [ $OVERALL_EXIT_CODE -ne 0 ]; then
    log_warn "Not offering a release: some tests failed."
fi

exit $OVERALL_EXIT_CODE
