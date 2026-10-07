#!/usr/bin/env bash
# =============================================================================
# package-release.sh — build a local release and ship its evidence with it
# =============================================================================
# Three kinds of release, chosen with --target (or interactively):
#
#   dev   releases/v<version>-<slug>/
#           DrivingCoach-v<version>-<slug>.apk   debug build, for your own phone
#           TEST_REPORT.html                     the tests run against it
#           RELEASE_NOTES.md                     commits since the last release
#
#   play  releases/v<version>-play/
#           Trillian-v<version>.aab              SIGNED bundle for Google Play
#           TEST_REPORT.html                     the tests run against it
#           RELEASE_NOTES.md                     commits since the last release
#           PLAY_SUBMISSION.md                   what the Console still needs
#
#   both  releases/v<version>-<slug>/            one release, both artifacts
#           DrivingCoach-v<version>-<slug>.apk   debug build, for your own phone
#           Trillian-v<version>.aab              SIGNED bundle for Google Play
#           TEST_REPORT.html / RELEASE_NOTES.md / PLAY_SUBMISSION.md
#
# The dev path builds a *debug* APK signed with the debug key. It can never be
# uploaded to Play, which is why the Play path is a separate pipeline rather than
# a flag on the same one.
#
# `both` is not a relaxation of either. A directory that holds a signed bundle is
# held to the bundle's standard, so `both` runs the entire Play preflight and
# inherits every one of its refusals — the APK simply rides along, built from the
# same commit, verified by the same report.
#
# The point of the directory is that an artifact and the evidence for it cannot
# be separated. A build with no report next to it is a build nobody has checked.
# The Play path goes further and refuses outright: an unverified build can sit on
# your own phone, but it should not reach strangers.
#
# Usage:
#   package-release.sh [--target dev|play|both] [--slug NAME] [--report FILE]
#                      [--no-build] [--yes]
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
TARGET=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --target)   TARGET="$2"; shift 2 ;;
        --slug)     SLUG="$2"; shift 2 ;;
        --report)   REPORT="$2"; shift 2 ;;
        --no-build) DO_BUILD=false; shift ;;
        --yes|-y)   ASSUME_YES=true; shift ;;
        --help)     sed -n '2,39p' "$0"; exit 0 ;;
        *)          log_error "Unknown option: $1"; exit 2 ;;
    esac
done

cd "$PROJECT_ROOT"

# ---------------------------------------------------------------------------
# Which kind of release?
# ---------------------------------------------------------------------------
# Asked before anything is built, because the two targets build different things
# from different build types with different keys.
if [ -z "$TARGET" ]; then
    if [ -t 0 ] && [ "$ASSUME_YES" != true ]; then
        echo ""
        echo "What kind of release?"
        echo ""
        echo "  1) Dev build     debug APK + test report, for your phone   (as before)"
        echo "  2) Play release  signed AAB + submission checklist         (Google Play)"
        echo "  3) Both          APK and AAB in one directory              (full Play gates)"
        echo ""
        read -r -p "Choose [1/2/3]: " choice
        case "$choice" in
            1) TARGET="dev" ;;
            2) TARGET="play" ;;
            3) TARGET="both" ;;
            *) log_error "Not a valid choice."; exit 2 ;;
        esac
    else
        # Non-interactive defaults to dev: publishing to the world should never
        # be something a script does because nobody was there to say otherwise.
        TARGET="dev"
    fi
fi

case "$TARGET" in
    dev|play|both) ;;
    *) log_error "--target must be 'dev', 'play' or 'both' (got: $TARGET)"; exit 2 ;;
esac

# What the target actually means, stated once.
#
# These three questions used to be asked by comparing "$TARGET" to the string
# "play" in seven separate places, which worked only while there were exactly two
# targets: every one of those comparisons silently meant "not dev". Naming the
# intents separates them, so a third target can want an APK *and* an AAB *and*
# the strict gates without any of the seven having to guess which it meant.
#
#   WANT_APK    build and ship the debug APK
#   WANT_AAB    build, verify the signature of, and ship the release bundle
#   PLAY_GATES  run every refusal that protects a public upload
#
# PLAY_GATES is deliberately not the same question as WANT_AAB even though they
# currently move together: a directory containing a signed bundle is held to the
# bundle's standard regardless of what else is in it.
case "$TARGET" in
    dev)  WANT_APK=true;  WANT_AAB=false; PLAY_GATES=false ;;
    play) WANT_APK=false; WANT_AAB=true;  PLAY_GATES=true  ;;
    both) WANT_APK=true;  WANT_AAB=true;  PLAY_GATES=true  ;;
esac

# ---------------------------------------------------------------------------
# Version and slug
# ---------------------------------------------------------------------------
VERSION="$(grep -oP 'val\s+appVersionName\s*=\s*"\K[^"]+' app/build.gradle.kts || true)"
if [ -z "$VERSION" ]; then
    log_error "Could not read appVersionName from app/build.gradle.kts"
    exit 1
fi

if [ -z "$SLUG" ]; then
    if [ "$TARGET" = "play" ]; then
        # A Play release is identified by its version, not by whatever branch it
        # happened to be cut from.
        SLUG="play"
    else
    # A branch named FT_Add_Advanced_racing_coach becomes advanced-racing-coach.
    BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo release)"
    SLUG="$(echo "$BRANCH" | sed -E 's/^(FT|FIX|FEAT)_//I; s/_/-/g; s/[^a-zA-Z0-9-]//g' \
            | tr '[:upper:]' '[:lower:]' | cut -c1-40)"
    [ -z "$SLUG" ] && SLUG="release"
    fi
fi

RELEASE_NAME="v${VERSION}-${SLUG}"
RELEASE_DIR="$RELEASES_DIR/$RELEASE_NAME"

# Play rejects a versionCode it has already seen, so this is needed by the
# preflight and again by the submission checklist. Derived here, once, with the
# same arithmetic app/build.gradle.kts uses — BuildVersionTest asserts that the
# compiled APK agrees with it.
VERSION_CODE="$(( $(echo "$VERSION" | cut -d. -f1) * 100 + $(echo "$VERSION" | cut -d. -f2) ))"

# The bundle is named for the version alone: a Play release is identified by what
# it is, not by the branch it was cut from. The APK keeps the slug because a
# phone may well hold several at once.
AAB_NAME="Trillian-v${VERSION}.aab"
APK_NAME="DrivingCoach-${RELEASE_NAME}.apk"

echo ""
log_step "Release: $RELEASE_NAME  (target: $TARGET)"
echo "  Version:   $VERSION ($VERSION_CODE)"
echo "  Directory: releases/$RELEASE_NAME/"
[ "$WANT_APK" = true ] && echo "  APK:       $APK_NAME"
[ "$WANT_AAB" = true ] && echo "  AAB:       $AAB_NAME"
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
# ---------------------------------------------------------------------------
# Play preflight — every reason to refuse, before anything is built
# ---------------------------------------------------------------------------
# These run first so a refusal costs seconds rather than a full release build,
# and so the tree is left exactly as it was found.
if [ "$PLAY_GATES" = true ]; then
    log_step "Play preflight"

    # 1. Signing. Without a key the bundle is unsigned and Play rejects it.
    KEYSTORE_PROPS="$PROJECT_ROOT/keystore.properties"
    if [ ! -f "$KEYSTORE_PROPS" ]; then
        log_error "No keystore.properties — release signing is not configured."
        log_error "  Play rejects unsigned uploads. See docs/RELEASE.md."
        exit 1
    fi
    KS_PATH="$(grep -oP '^storeFile=\K.*' "$KEYSTORE_PROPS" | head -1)"
    if [ -z "$KS_PATH" ] || [ ! -f "$KS_PATH" ]; then
        log_error "keystore.properties points at a keystore that is not there:"
        log_error "  ${KS_PATH:-<storeFile unset>}"
        exit 1
    fi
    log_info "Signing key: present"

    # 2. A clean tree. You must be able to say exactly what you shipped, and an
    #    uncommitted edit makes the recorded commit a lie.
    if [ -n "$(git status --porcelain 2>/dev/null)" ]; then
        log_error "Working tree is not clean."
        log_error "  A Play release records a commit hash as its provenance. With"
        log_error "  uncommitted changes that hash does not describe what you built."
        git status --short | head -10 | sed 's/^/    /'
        exit 1
    fi
    log_info "Working tree: clean"

    # 3. versionCode must not repeat. Play rejects a duplicate outright, and
    #    finding that out at upload time wastes the whole build.
    #
    #    What counts as a previous Play release is "a release directory that
    #    contains a bundle", not "a directory named -play". The name was a proxy
    #    for the contents that stopped being accurate the moment a target could
    #    put an AAB somewhere else: a v3.04-some-slug release holding a bundle
    #    would have been invisible to this scan, and the collision it caused
    #    would surface at upload, which is exactly what this gate exists to
    #    prevent. Ask about the contents instead.
    PREV_PLAY=""
    for cand in "$RELEASES_DIR"/v*/; do
        [ -d "$cand" ] || continue
        [ "$(basename "$cand")" = "$RELEASE_NAME" ] && continue
        compgen -G "$cand*.aab" >/dev/null 2>&1 && PREV_PLAY="$PREV_PLAY $cand"
    done
    for prev in $PREV_PLAY; do
        # v3.04-coaching-sectors-and-map and v3.0-play both reduce to their
        # version: everything from the first hyphen after the digits onward is a
        # label, not part of the number.
        prev_v="$(basename "$prev" | sed -E 's/^v([0-9]+\.[0-9]+).*$/\1/')"
        case "$prev_v" in
            [0-9]*.[0-9]*) ;;
            *) log_warn "Skipping unparseable release directory: $(basename "$prev")"; continue ;;
        esac
        prev_code="$(( $(echo "$prev_v" | cut -d. -f1) * 100 + $(echo "$prev_v" | cut -d. -f2) ))"
        if [ "$VERSION_CODE" -le "$prev_code" ]; then
            log_error "versionCode $VERSION_CODE is not above already-packaged v$prev_v ($prev_code)."
            log_error "  Play only accepts a strictly increasing versionCode."
            log_error "  Already packaged as a bundle in: releases/$(basename "$prev")/"
            log_error "  Raise appVersionName in app/build.gradle.kts."
            exit 1
        fi
    done
    log_info "versionCode: $VERSION_CODE (clear of previous bundled releases)"

    # 4. Release-blocking lint. lintVitalRelease is the subset Google considers
    #    fatal; it is cheap next to the cost of a rejected submission.
    log_info "Running lintVitalRelease…"
    if ! ./gradlew lintVitalRelease --quiet; then
        log_error "lintVitalRelease failed — fix before submitting to Play."
        exit 1
    fi
    log_info "lintVitalRelease: passed"

    # 5. Data Safety guarantee. The Play listing declares that this app collects
    #    no user data. It records precise location continuously, so that claim
    #    rests entirely on nothing being transmitted — and the strongest form of
    #    that is a release build with no network permission at all (NF-20).
    #
    #    This cannot be asserted from a unit or instrumentation test: those run
    #    against the debug variant, which deliberately does hold INTERNET so
    #    MockWebServer works. So it is checked here, against the actual merged
    #    manifest that is about to be packaged.
    log_info "Checking release manifest for network permissions…"
    ./gradlew processReleaseMainManifest --quiet || {
        log_error "Could not build the release manifest."
        exit 1
    }
    RELEASE_MANIFEST="app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"
    if [ ! -f "$RELEASE_MANIFEST" ]; then
        log_error "Release manifest not found at $RELEASE_MANIFEST — cannot verify the Data Safety claim."
        exit 1
    fi
    for FORBIDDEN in "android.permission.INTERNET" "android.permission.ACTIVITY_RECOGNITION"; do
        if grep -q "\"$FORBIDDEN\"" "$RELEASE_MANIFEST"; then
            log_error "Release manifest declares $FORBIDDEN."
            log_error "The Play listing says this app collects no user data. Either remove"
            log_error "the permission, or update the Data Safety declaration and this gate."
            exit 1
        fi
    done
    log_info "Release manifest: no network permission (Data Safety claim holds)"
fi

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
if [ "$WANT_AAB" = true ]; then
    if [ "$DO_BUILD" = true ]; then
        log_step "Building signed release bundle"
        ./gradlew bundleRelease --quiet || { log_error "Build failed"; exit 1; }
    fi
    AAB_SOURCE="$(ls -t app/build/outputs/bundle/release/*.aab 2>/dev/null | head -1 || true)"
    if [ -z "$AAB_SOURCE" ] || [ ! -f "$AAB_SOURCE" ]; then
        log_error "No AAB in app/build/outputs/bundle/release/ (drop --no-build to build one)"
        exit 1
    fi

    # An unsigned bundle is indistinguishable from a signed one by size or name,
    # so check rather than assume. This is the last point at which a silent
    # signing misconfiguration can still be caught locally.
    if ! jarsigner -verify "$AAB_SOURCE" >/dev/null 2>&1; then
        log_error "The bundle is NOT signed. Play will reject it."
        exit 1
    fi
    log_info "Bundle signature: verified"
fi

if [ "$WANT_APK" = true ]; then
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
fi

# ---------------------------------------------------------------------------
# Test report -- resolved and checked BEFORE anything is written
# ---------------------------------------------------------------------------
# A report that exists is not the same as a report that describes THIS build.
# Packaging copies the newest run it can find, and before this check that run
# could be days old: v2.96 was first packaged with a report from a v2.95 run,
# silently, because the copy succeeded. The promise at the top of this file --
# that an APK and its evidence cannot be separated -- was not being kept.
#
# This runs before the release directory is created or the APK copied, so a
# refusal leaves the tree exactly as it found it rather than a half-written
# release with the APK stripped out of it.
if [ -z "$REPORT" ]; then
    REPORT="$(ls -t "$PROJECT_ROOT"/05_tests/reports/RUN_*/TEST_REPORT.html 2>/dev/null | head -1 || true)"
fi

if [ -n "$REPORT" ] && [ -f "$REPORT" ]; then
    REPORT_VERSION="$(grep -oP '<b>Version:</b>\s*v\K[^<]+' "$REPORT" 2>/dev/null | head -1 || true)"
    REPORT_COMMIT="$(grep -oP '<b>Commit:</b>\s*\K[0-9a-f]+' "$REPORT" 2>/dev/null | head -1 || true)"
    HEAD_COMMIT="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"

    STALE=""
    [ "$REPORT_VERSION" != "$VERSION" ] && \
        STALE="report covers v${REPORT_VERSION:-unknown}, this build is v$VERSION"
    [ -z "$STALE" ] && [ "$REPORT_COMMIT" != "$HEAD_COMMIT" ] && \
        STALE="report covers commit ${REPORT_COMMIT:-unknown}, HEAD is $HEAD_COMMIT"

    if [ -n "$STALE" ]; then
        log_error "Refusing to package: $STALE"
        log_error "  Stale report: ${REPORT#$PROJECT_ROOT/}"
        log_error ""
        log_error "  Shipping this would put an APK next to evidence for a different"
        log_error "  build, which is worse than shipping no evidence at all."
        log_error ""
        log_error "  Run the tests against this build first:"
        log_error "    ./05_tests/infra/scripts/run-all-tests.sh --start-emulator --stop-emulator"
        exit 1
    fi
fi

# A dev build may ship without evidence, loudly. A Play release may not: once it
# is public, "we never checked" stops being a private problem.
if [ "$PLAY_GATES" = true ] && { [ -z "$REPORT" ] || [ ! -f "$REPORT" ]; }; then
    log_error "Refusing to package a Play release with no test report."
    log_error ""
    log_error "  Run the tests against this build first:"
    log_error "    ./05_tests/infra/scripts/run-all-tests.sh --start-emulator --stop-emulator"
    exit 1
fi

mkdir -p "$RELEASE_DIR"
if [ "$WANT_APK" = true ]; then
    cp "$APK_SOURCE" "$RELEASE_DIR/$APK_NAME"
    log_info "APK: $(du -h "$RELEASE_DIR/$APK_NAME" | cut -f1)"
fi
if [ "$WANT_AAB" = true ]; then
    cp "$AAB_SOURCE" "$RELEASE_DIR/$AAB_NAME"
    log_info "AAB: $(du -h "$RELEASE_DIR/$AAB_NAME" | cut -f1)"
fi

# ---------------------------------------------------------------------------
# Test report -- already resolved and verified above
# ---------------------------------------------------------------------------
if [ -n "$REPORT" ] && [ -f "$REPORT" ]; then
    cp "$REPORT" "$RELEASE_DIR/TEST_REPORT.html"
    log_info "Test report: TEST_REPORT.html (v$REPORT_VERSION, $REPORT_COMMIT)"
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

# Prefer a git tag; fall back to the commit the previous release recorded.
#
# This used to ask git which commit added the previous release directory, which
# can never work: releases/ is gitignored, so the path has no history and the
# lookup silently returned nothing. Every release therefore fell through to
# "the last 15 commits" regardless of what it contained -- v2.96 listed 15 when
# 6 were its own. The previous release's RELEASE_NOTES.md records its own commit
# and lives on disk, so it survives gitignore and is the reliable anchor.
if git rev-parse "v$VERSION" >/dev/null 2>&1; then
    PREV_TAG="v$VERSION^"
elif [ -n "$PREV_RELEASE" ] && [ -f "$PREV_RELEASE/RELEASE_NOTES.md" ]; then
    PREV_TAG="$(grep -oP '^\| Commit \| `\K[0-9a-f]+' "$PREV_RELEASE/RELEASE_NOTES.md" 2>/dev/null | head -1 || true)"
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
    [ "$WANT_APK" = true ] && echo "| APK | \`$APK_NAME\` |"
    [ "$WANT_AAB" = true ] && echo "| AAB | \`$AAB_NAME\` |"
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

# ---------------------------------------------------------------------------
# Play submission checklist
# ---------------------------------------------------------------------------
# The build is only half of a release. The other half is a set of Console
# declarations that have nothing to do with Gradle and are easy to get wrong
# under time pressure, so they are written down next to the artifact they
# belong to rather than remembered.
if [ "$WANT_AAB" = true ]; then
    log_step "Writing Play submission checklist"
    CERT_SHA="$(keytool -list -v -keystore "$KS_PATH" \
        -storepass "$(grep -oP '^storePassword=\K.*' "$KEYSTORE_PROPS" | head -1)" 2>/dev/null \
        | grep -m1 'SHA256:' | sed 's/.*SHA256: //')"

    cat > "$RELEASE_DIR/PLAY_SUBMISSION.md" << EOF
# Play submission — Trillian v$VERSION

| | |
|---|---|
| Package | \`io.github.emidiofaria.trillian\` |
| versionCode | $VERSION_CODE |
| versionName | $VERSION |
| Bundle | \`$AAB_NAME\` |
| Upload key SHA-256 | \`$CERT_SHA\` |
| Commit | \`$(git rev-parse --short HEAD 2>/dev/null || echo unknown)\` |

## Before uploading

- [ ] **Back up the upload keystore.** Losing it means this app can never be
      updated again. Enable Play App Signing so Google holds the app key and
      this one is only the upload key — that makes loss recoverable.
- [ ] Confirm the package name above is what you want. **It is permanent from
      the first upload onward and cannot be changed afterwards.**

## Console declarations this app needs

These follow from the permissions in the manifest, not from preference.

- [ ] **Privacy policy URL** — mandatory, because the app requests location.
      Publish \`docs/privacy-policy.md\` and paste the URL. See
      \`docs/RELEASE.md\` for the GitHub Pages steps.
- [ ] **Data safety form** — exact answers below.
- [ ] **Foreground service declaration** — required for
      \`FOREGROUND_SERVICE_LOCATION\`. Suggested text:
      *"Trillian is a track-day lap timer. When the driver starts a session it
      records GPS continuously to measure lap times, speed and track shape. A
      foreground service is required because recording must survive the screen
      turning off during a lap. A persistent notification is shown for the
      entire duration. Location is never sent off the device."*
- [ ] **Prominent disclosure** — satisfied in-app: the onboarding screen states
      what is recorded and that it stays on the device, before the runtime
      permission prompt is shown.
- [ ] **Content rating questionnaire** — no user content, no ads, no purchases,
      no data sharing.
- [ ] **Target audience** — not directed at children.
- [ ] **Ads** — declare **no ads**.

## Data safety — exact answers

The answer to the top-level question is **"No"**: this app does not collect or
share any user data.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data encrypted in transit? | n/a — nothing is transmitted |
| Do you provide a way for users to request data deletion? | n/a — data never leaves the device; uninstalling removes it |

**Why "No" is correct here.** Google's definition of *collection* is data
transmitted off the device. Trillian records precise location and inertial data,
but writes them only to app-private storage. There is no server, no account, no
analytics, no ad SDK and no crash reporting.

This is enforced, not merely intended (**NF-20**):

1. \`BuildConfig.UPLOAD_ENABLED\` is \`false\`, so no upload is ever enqueued —
   asserted by \`DataSafetyPolicyTest\`.
2. The release manifest declares **no \`INTERNET\` permission**, so the process
   cannot open a socket at all — asserted by a preflight gate in
   \`package-release.sh\` that reads the merged release manifest.

⚠️ If a future release adds a backend, this answer must change to **Yes** in the
same release that adds it, along with the privacy policy. Both automated gates
above will fail first, by design.

### If a reviewer asks about location anyway

Requesting \`ACCESS_FINE_LOCATION\` often triggers a question even when nothing
is collected. The answer: location is used solely to measure the driver's own
lap times, is written to app-private storage, is never transmitted, and the
release build has no network permission with which to transmit it.

## What helps this pass review

The app does **not** request \`ACCESS_BACKGROUND_LOCATION\`. Location is sampled
only by a foreground service the driver starts, with a persistent notification
visible the whole time (SRS NF-14). Background location is the single largest
cause of rejection for driving apps; say plainly that this app does not use it.

The app also does not request \`ACTIVITY_RECOGNITION\`. It was declared and
prompted for in earlier builds but never used by any code, which is exactly the
kind of unused sensitive permission that draws review scrutiny.

## Known state of this build

- Targets **API 36** (Android 16), the minimum Play accepts for new apps.
- R8/minification is **off** (see NF-12 in the SRS). Permitted, but the bundle
  is larger and not obfuscated.
- Test evidence for this exact commit is in \`TEST_REPORT.html\` beside this file.
EOF

    # A `both` release puts a debug APK in the same directory as the bundle. The
    # two look interchangeable in a file listing and are not: one is signed with
    # the debug key and would be rejected, or worse, published with a debug
    # signature. Say which is which, next to the one being uploaded.
    if [ "$WANT_APK" = true ]; then
        cat >> "$RELEASE_DIR/PLAY_SUBMISSION.md" << EOF
- This directory **also contains a debug APK** (\`$APK_NAME\`), built from the
  same commit for sideloading onto your own phone. It is signed with the debug
  key. It is **not** the file to upload — upload \`$AAB_NAME\`.
EOF
    fi
    log_info "Play checklist: PLAY_SUBMISSION.md"
fi

echo ""
log_step "Done — releases/$RELEASE_NAME/"
ls -1sh "$RELEASE_DIR" | tail -n +2 | sed 's/^/  /'
echo ""
