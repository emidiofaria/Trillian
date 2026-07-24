#!/bin/bash
# =============================================================================
# generate-report.sh — Generate Consolidated Test Report
# =============================================================================
# ASPICE: Generates single-file test report covering all test levels
#
# This script:
#   1. Collects test results from Gradle output directories
#   2. Parses JUnit XML files for pass/fail counts
#   3. Generates a single Markdown report with all levels
#   4. Marks unexecuted levels as "NOT EXECUTED"
#
# Usage:
#   ./generate-report.sh [--output DIR]
#
# Options:
#   --output DIR   Output directory (default: 05_tests/reports/)
#
# Output:
#   05_tests/reports/TEST_REPORT_YYYY-MM-DD_HH-MM-SS.md
#
# Exit Codes:
#   0 - Report generated successfully
#   1 - Error generating report
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/05_tests/reports"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --output)
            OUTPUT_DIR="$2"
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
# Setup
# =============================================================================
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
REPORT_FILE="$OUTPUT_DIR/TEST_REPORT_$TIMESTAMP.md"

mkdir -p "$OUTPUT_DIR"

log_info "Generating test report..."
log_info "Output: $REPORT_FILE"

# Gradle result directories
UNIT_RESULTS="$PROJECT_ROOT/app/build/test-results/testDebugUnitTest"
INSTRUMENTED_RESULTS="$PROJECT_ROOT/app/build/outputs/androidTest-results/connected/debug"

# =============================================================================
# Helper: Parse JUnit XML
# =============================================================================
parse_junit_xml() {
    local xml_dir="$1"
    local tests=0
    local passed=0
    local failed=0
    local skipped=0
    local duration=0
    
    if [ ! -d "$xml_dir" ]; then
        echo "0 0 0 0 0"
        return
    fi
    
    # Find all XML files
    for xml_file in "$xml_dir"/*.xml; do
        [ -f "$xml_file" ] || continue
        
        # Extract attributes from testsuite element using grep/sed
        local suite_tests=$(grep -oP 'tests="\K[0-9]+' "$xml_file" 2>/dev/null | head -1 || echo "0")
        local suite_failures=$(grep -oP 'failures="\K[0-9]+' "$xml_file" 2>/dev/null | head -1 || echo "0")
        local suite_errors=$(grep -oP 'errors="\K[0-9]+' "$xml_file" 2>/dev/null | head -1 || echo "0")
        local suite_skipped=$(grep -oP 'skipped="\K[0-9]+' "$xml_file" 2>/dev/null | head -1 || echo "0")
        local suite_time=$(grep -oP 'time="\K[0-9.]+' "$xml_file" 2>/dev/null | head -1 || echo "0")
        
        tests=$((tests + suite_tests))
        failed=$((failed + suite_failures + suite_errors))
        skipped=$((skipped + suite_skipped))
        duration=$(echo "$duration + $suite_time" | bc 2>/dev/null || echo "$duration")
    done
    
    passed=$((tests - failed - skipped))
    [ $passed -lt 0 ] && passed=0
    
    echo "$tests $passed $failed $skipped $duration"
}

# =============================================================================
# Helper: Extract failed tests from XML
# =============================================================================
extract_failures() {
    local xml_dir="$1"
    
    if [ ! -d "$xml_dir" ]; then
        return
    fi
    
    for xml_file in "$xml_dir"/*.xml; do
        [ -f "$xml_file" ] || continue
        
        # Extract testcase names with failures
        grep -B1 '<failure' "$xml_file" 2>/dev/null | grep 'testcase' | \
            sed 's/.*name="\([^"]*\)".*/\1/' || true
    done
}

# =============================================================================
# Collect Results
# =============================================================================
log_info "Collecting test results..."

# L1 Unit Tests
read L1_TESTS L1_PASSED L1_FAILED L1_SKIPPED L1_DURATION <<< $(parse_junit_xml "$UNIT_RESULTS")
L1_EXECUTED=false
[ "$L1_TESTS" -gt 0 ] && L1_EXECUTED=true

# L2 Integration Tests
read L2_TESTS L2_PASSED L2_FAILED L2_SKIPPED L2_DURATION <<< $(parse_junit_xml "$INSTRUMENTED_RESULTS")
L2_EXECUTED=false
[ "$L2_TESTS" -gt 0 ] && L2_EXECUTED=true

# L3 SW Qualification - future
L3_EXECUTED=false
L3_TESTS=0
L3_PASSED=0
L3_FAILED=0
L3_SKIPPED=0
L3_DURATION=0

# L4 System Qualification - manual only
L4_EXECUTED=false

# =============================================================================
# Calculate totals
# =============================================================================
TOTAL_TESTS=$((L1_TESTS + L2_TESTS + L3_TESTS))
TOTAL_PASSED=$((L1_PASSED + L2_PASSED + L3_PASSED))
TOTAL_FAILED=$((L1_FAILED + L2_FAILED + L3_FAILED))
TOTAL_SKIPPED=$((L1_SKIPPED + L2_SKIPPED + L3_SKIPPED))
TOTAL_DURATION=$(echo "$L1_DURATION + $L2_DURATION + $L3_DURATION" | bc 2>/dev/null || echo "0")

# Determine overall status
if [ "$TOTAL_FAILED" -gt 0 ]; then
    OVERALL_STATUS="⚠️ PARTIAL PASS"
elif [ "$TOTAL_TESTS" -gt 0 ]; then
    OVERALL_STATUS="✅ PASS"
else
    OVERALL_STATUS="⏭️ NO TESTS EXECUTED"
fi

# =============================================================================
# Generate Report
# =============================================================================
log_info "Writing report..."

cat > "$REPORT_FILE" << EOF
# Test Report

**Date:** $(date "+%Y-%m-%d %H:%M:%S")  
**Executed by:** Automated / Agent  
**Duration:** ${TOTAL_DURATION}s

---

## Summary

| Level | ASPICE | Status | Tests | Passed | Failed | Skipped |
|-------|--------|--------|-------|--------|--------|---------|
EOF

# L1 Row
if [ "$L1_EXECUTED" = true ]; then
    if [ "$L1_FAILED" -gt 0 ]; then
        L1_STATUS="⚠️ PARTIAL"
    else
        L1_STATUS="✅ PASS"
    fi
    echo "| L1_SWE4_unit | SWE.4 | $L1_STATUS | $L1_TESTS | $L1_PASSED | $L1_FAILED | $L1_SKIPPED |" >> "$REPORT_FILE"
else
    echo "| L1_SWE4_unit | SWE.4 | ⏭️ NOT EXECUTED | - | - | - | - |" >> "$REPORT_FILE"
fi

# L2 Row
if [ "$L2_EXECUTED" = true ]; then
    if [ "$L2_FAILED" -gt 0 ]; then
        L2_STATUS="⚠️ PARTIAL"
    else
        L2_STATUS="✅ PASS"
    fi
    echo "| L2_SWE5_integration | SWE.5 | $L2_STATUS | $L2_TESTS | $L2_PASSED | $L2_FAILED | $L2_SKIPPED |" >> "$REPORT_FILE"
else
    echo "| L2_SWE5_integration | SWE.5 | ⏭️ NOT EXECUTED | - | - | - | - |" >> "$REPORT_FILE"
fi

# L3 Row
if [ "$L3_EXECUTED" = true ]; then
    if [ "$L3_FAILED" -gt 0 ]; then
        L3_STATUS="⚠️ PARTIAL"
    else
        L3_STATUS="✅ PASS"
    fi
    echo "| L3_SWE6_qualification | SWE.6 | $L3_STATUS | $L3_TESTS | $L3_PASSED | $L3_FAILED | $L3_SKIPPED |" >> "$REPORT_FILE"
else
    echo "| L3_SWE6_qualification | SWE.6 | ⏭️ NOT EXECUTED | - | - | - | - |" >> "$REPORT_FILE"
fi

# L4 Row (always manual)
echo "| L4_SYS5_acceptance | SYS.5 | ⏭️ NOT EXECUTED | - | - | - | - |" >> "$REPORT_FILE"

# Overall row
cat >> "$REPORT_FILE" << EOF

**Overall:** $OVERALL_STATUS ($TOTAL_PASSED/$TOTAL_TESTS tests passed)

---

## L1_SWE4_unit — Unit Tests

EOF

if [ "$L1_EXECUTED" = true ]; then
    cat >> "$REPORT_FILE" << EOF
**Status:** $([ "$L1_FAILED" -gt 0 ] && echo "⚠️ PARTIAL" || echo "✅ PASS")  
**Tests:** $L1_TESTS | **Passed:** $L1_PASSED | **Failed:** $L1_FAILED | **Skipped:** $L1_SKIPPED  
**Duration:** ${L1_DURATION}s  
**Location:** \`app/src/test/\`

EOF
    if [ "$L1_FAILED" -gt 0 ]; then
        echo "### Failed Tests" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        extract_failures "$UNIT_RESULTS" | while read test_name; do
            echo "- \`$test_name\`" >> "$REPORT_FILE"
        done
        echo "" >> "$REPORT_FILE"
    else
        echo "All tests passed." >> "$REPORT_FILE"
    fi
else
    cat >> "$REPORT_FILE" << EOF
**Status:** ⏭️ NOT EXECUTED  
**Reason:** Unit tests were not run in this execution.

To run: \`./gradlew testDebugUnitTest\`
EOF
fi

cat >> "$REPORT_FILE" << EOF

---

## L2_SWE5_integration — Integration Tests

EOF

if [ "$L2_EXECUTED" = true ]; then
    cat >> "$REPORT_FILE" << EOF
**Status:** $([ "$L2_FAILED" -gt 0 ] && echo "⚠️ PARTIAL" || echo "✅ PASS")  
**Tests:** $L2_TESTS | **Passed:** $L2_PASSED | **Failed:** $L2_FAILED | **Skipped:** $L2_SKIPPED  
**Duration:** ${L2_DURATION}s  
**Location:** \`app/src/androidTest/\`

EOF
    if [ "$L2_FAILED" -gt 0 ]; then
        echo "### Failed Tests" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        extract_failures "$INSTRUMENTED_RESULTS" | while read test_name; do
            echo "- \`$test_name\`" >> "$REPORT_FILE"
        done
        echo "" >> "$REPORT_FILE"
    else
        echo "All tests passed." >> "$REPORT_FILE"
    fi
else
    cat >> "$REPORT_FILE" << EOF
**Status:** ⏭️ NOT EXECUTED  
**Reason:** No device or emulator available, or tests were not run.

To run: \`./05_tests/infra/scripts/run-instrumented.sh --start-emulator\`
EOF
fi

cat >> "$REPORT_FILE" << EOF

---

## L3_SWE6_qualification — SW Qualification Tests

**Status:** ⏭️ NOT EXECUTED  
**Reason:** E2E qualification tests not yet implemented.

Future implementation will include:
- Complete recording workflows with simulated GPS data
- Lap detection accuracy validation
- Session review and export tests

---

## L4_SYS5_acceptance — System Qualification Tests

**Status:** ⏭️ NOT EXECUTED  
**Reason:** Human execution required at track.

See: \`05_tests/L4_SYS5_acceptance/\` for test checklists.

---

## Environment

| Property | Value |
|----------|-------|
| Date | $(date "+%Y-%m-%d %H:%M:%S") |
| Host | $(hostname) |
| Android SDK | ${ANDROID_SDK_ROOT:-"Not set"} |
| Java | $(java -version 2>&1 | head -1 || echo "Unknown") |
| Gradle | $(cd "$PROJECT_ROOT" && ./gradlew --version 2>/dev/null | grep "Gradle " | head -1 || echo "Unknown") |

---

*Report generated by generate-report.sh*
EOF

# =============================================================================
# Summary
# =============================================================================
echo ""
log_info "=========================================="
log_info "Test report generated!"
log_info "=========================================="
echo ""
echo "  File: $REPORT_FILE"
echo ""
echo "  Summary:"
echo "    L1_SWE4_unit:        $([ "$L1_EXECUTED" = true ] && echo "$L1_PASSED/$L1_TESTS passed" || echo "Not executed")"
echo "    L2_SWE5_integration: $([ "$L2_EXECUTED" = true ] && echo "$L2_PASSED/$L2_TESTS passed" || echo "Not executed")"
echo "    L3_SWE6_qualification: Not executed (future)"
echo "    L4_SYS5_acceptance: Not executed (manual)"
echo ""

exit 0
