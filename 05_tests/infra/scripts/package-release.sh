#!/usr/bin/env bash
# =============================================================================
# package-release.sh — build a local release and ship its evidence with it
# =============================================================================
# Produces:
#
#   releases/v<version>-<slug>/
#     DrivingCoach-v<version>-<slug>.apk   the build
#     TEST_REPORT.html                     the tests that were run against it
#     RELEASE_NOTES.md                     the commits since the last release
#
# The point of the directory is that an APK and the evidence for it cannot be
# separated. A build with no report next to it is a build nobody has checked.
#
# Usage:
#   package-release.sh [--slug NAME] [--report FILE] [--no-build] [--yes]
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
RELEASES_DIR="$PROJECT_ROOT/releases"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}==>${NC} $1"; }

SLUG=""
REPORT=""
DO_BUILD=true
ASSUME_YES=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --slug)     SLUG="$2"; shift 2 ;;
        --report)   REPORT="$2"; shift 2 ;;
        --no-build) DO_BUILD=false; shift ;;
        --yes|-y)   ASSUME_YES=true; shift ;;
        --help)     sed -n '2,20p' "$0"; exit 0 ;;
        *)          log_error "Unknown option: $1"; exit 2 ;;
    esac
done

cd "$PROJECT_ROOT"

# ---------------------------------------------------------------------------
# Version and slug
# ---------------------------------------------------------------------------
VERSION="$(grep -oP 'val\s+appVersionName\s*=\s*"\K[^"]+' app/build.gradle.kts || true)"
if [ -z "$VERSION" ]; then
    log_error "Could not read appVersionName from app/build.gradle.kts"
    exit 1
fi

if [ -z "$SLUG" ]; then
    # A branch named FT_Add_Advanced_racing_coach becomes advanced-racing-coach.
    BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo release)"
    SLUG="$(echo "$BRANCH" | sed -E 's/^(FT|FIX|FEAT)_//I; s/_/-/g; s/[^a-zA-Z0-9-]//g' \
            | tr '[:upper:]' '[:lower:]' | cut -c1-40)"
    [ -z "$SLUG" ] && SLUG="release"
fi

RELEASE_NAME="v${VERSION}-${SLUG}"
RELEASE_DIR="$RELEASES_DIR/$RELEASE_NAME"
APK_NAME="DrivingCoach-${RELEASE_NAME}.apk"

echo ""
log_step "Release: $RELEASE_NAME"
echo "  Version:   $VERSION"
echo "  Directory: releases/$RELEASE_NAME/"
echo "  APK:       $APK_NAME"
echo ""

if [ -d "$RELEASE_DIR" ] && [ "$ASSUME_YES" != true ]; then
    log_warn "releases/$RELEASE_NAME/ already exists and will be overwritten."
    if [ -t 0 ]; then
        read -r -p "Continue? [y/N] " reply
        case "$reply" in [yY]|[yY][eE][sS]) ;; *) log_info "Cancelled."; exit 0 ;; esac
    else
        log_error "Refusing to overwrite non-interactively without --yes"
        exit 1
    fi
fi

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
if [ "$DO_BUILD" = true ]; then
    log_step "Building debug APK"
    ./gradlew assembleDebug --quiet || { log_error "Build failed"; exit 1; }
fi
# The build names the APK itself, so find it rather than assume it.
APK_SOURCE="$(ls -t app/build/outputs/apk/debug/*.apk 2>/dev/null | head -1 || true)"
if [ -z "$APK_SOURCE" ] || [ ! -f "$APK_SOURCE" ]; then
    log_error "No APK in app/build/outputs/apk/debug/ (drop --no-build to build one)"
    exit 1
fi

mkdir -p "$RELEASE_DIR"
cp "$APK_SOURCE" "$RELEASE_DIR/$APK_NAME"
log_info "APK: $(du -h "$RELEASE_DIR/$APK_NAME" | cut -f1)"

# ---------------------------------------------------------------------------
# Test report
# ---------------------------------------------------------------------------
if [ -z "$REPORT" ]; then
    REPORT="$(ls -t "$PROJECT_ROOT"/05_tests/reports/RUN_*/TEST_REPORT.html 2>/dev/null | head -1 || true)"
fi
if [ -n "$REPORT" ] && [ -f "$REPORT" ]; then
    cp "$REPORT" "$RELEASE_DIR/TEST_REPORT.html"
    log_info "Test report: TEST_REPORT.html"
    RUN_DIR="$(dirname "$REPORT")"
    [ -f "$RUN_DIR/TEST_REPORT.md" ] && cp "$RUN_DIR/TEST_REPORT.md" "$RELEASE_DIR/TEST_REPORT.md"
else
    # Never ship an APK that silently has no evidence: say so, loudly, in the
    # release directory itself.
    log_warn "No test report found — recording that fact in the release"
    cat > "$RELEASE_DIR/TEST_REPORT_MISSING.txt" << 'EOF'
No test report accompanies this build.

This APK was packaged without a test run. Nothing here states which tests
passed against it. Treat it as unverified until a report is generated with:

    ./05_tests/infra/scripts/run-all-tests.sh --start-emulator --stop-emulator
EOF
fi

# ---------------------------------------------------------------------------
# Release notes
# ---------------------------------------------------------------------------
log_step "Writing release notes"
PREV_TAG=""
PREV_RELEASE="$(ls -d "$RELEASES_DIR"/v*/ 2>/dev/null | grep -v "/$RELEASE_NAME/$" | tail -1 || true)"

# Prefer a git tag; fall back to the commit that added the previous release.
if git rev-parse "v$VERSION" >/dev/null 2>&1; then
    PREV_TAG="v$VERSION^"
elif [ -n "$PREV_RELEASE" ]; then
    PREV_TAG="$(git log -1 --format=%H -- "$PREV_RELEASE" 2>/dev/null || true)"
fi
# With no anchor at all (the first release), fall back to the recent history
# rather than replaying the whole project.

{
    echo "# Driving Coach $RELEASE_NAME"
    echo ""
    echo "| | |"
    echo "|---|---|"
    echo "| Version | $VERSION |"
    echo "| Built | $(date '+%Y-%m-%d %H:%M') |"
    echo "| Commit | \`$(git rev-parse --short HEAD 2>/dev/null || echo unknown)\` |"
    echo "| Branch | $(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown) |"
    echo "| APK | \`$APK_NAME\` |"
    echo ""
    echo "## What changed"
    echo ""
    if [ -n "$PREV_TAG" ] && git rev-parse "$PREV_TAG" >/dev/null 2>&1; then
        git log --no-merges --format='- %s' "$PREV_TAG..HEAD" 2>/dev/null | head -40
    else
        git log --no-merges --format='- %s' -15
    fi
    echo ""
    echo "## Verification"
    echo ""
    if [ -f "$RELEASE_DIR/TEST_REPORT.html" ]; then
        echo "See \`TEST_REPORT.html\` in this directory for what was tested, what passed,"
        echo "and which requirements are still uncovered. It is generated from the test"
        echo "results themselves, not written by hand."
    else
        echo "**No test report accompanies this build.** See \`TEST_REPORT_MISSING.txt\`."
    fi
    echo ""
    echo "Manual acceptance tests (L4) are listed at the end of the test report and"
    echo "still require a human at a circuit. They are not covered by this build's"
    echo "automated results."
} > "$RELEASE_DIR/RELEASE_NOTES.md"
log_info "Release notes: RELEASE_NOTES.md"

echo ""
log_step "Done — releases/$RELEASE_NAME/"
ls -1sh "$RELEASE_DIR" | tail -n +2 | sed 's/^/  /'
echo ""
