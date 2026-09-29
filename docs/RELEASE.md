# Releasing Trillian

There are two kinds of release, and they are not variations of each other. One
builds a **debug** APK signed with the debug key for your own phone; the other
builds a **signed** Android App Bundle for Google Play. They use different build
types, different keys, and different rules about what may be shipped.

Both are produced by the same script, which asks which one you want:

```bash
./05_tests/infra/scripts/package-release.sh
```

or non-interactively:

```bash
./05_tests/infra/scripts/package-release.sh --target dev
./05_tests/infra/scripts/package-release.sh --target play
```

Without `--target` and without a terminal, it defaults to `dev`. Publishing to
the world should never be something a script does because nobody was there to
say otherwise.

---

## Dev release

```
releases/v<version>-<slug>/
  DrivingCoach-v<version>-<slug>.apk
  TEST_REPORT.html
  RELEASE_NOTES.md
```

Builds `assembleDebug`. Install with `adb install -r`. This APK can never be
uploaded to Play: it is debuggable and signed with the debug key.

If no test report matches the build, the release is still created, but a
`TEST_REPORT_MISSING.txt` is written into it saying so. A build on your own
phone may be unverified as long as it is honestly labelled.

---

## Play release

```
releases/v<version>-play/
  Trillian-v<version>.aab
  TEST_REPORT.html
  RELEASE_NOTES.md
  PLAY_SUBMISSION.md
```

Builds `bundleRelease`, signed with the upload key.

### It will refuse to run if any of these is true

| Refusal | Why |
|---|---|
| No signing configured | Play rejects unsigned uploads |
| Working tree not clean | The recorded commit would not describe what you built |
| `versionCode` not above a previously packaged Play release | Play rejects duplicates outright |
| `lintVitalRelease` fails | This is the lint subset Google treats as fatal |
| No test report, or one from a different version or commit | An unverified build may sit on your phone; it should not reach strangers |

These run **before** the build, so a refusal costs seconds rather than a full
release build, and leaves the tree exactly as it found it.

### Built-in circuits gate the channel

A release carries the circuits in `assets/tracks/tracks.json`, and a circuit is a
set of claims about a real place. The claims are graded, and the grade decides how
far the build may travel. This is a judgement the script cannot make for you.

| Tier | Evidence held | Channel |
|------|---------------|---------|
| **A** | Start/finish line, heading, lap distance, lap envelope | Internal testing only |
| **B** | + walked centreline | Internal testing only |
| **C** | + an independent recorded session that predates the catalogue | Internal testing only |
| **D** | + an L4 acceptance run driven at the circuit on the shipped build | Eligible for wider release |

**Tiers A–C are all internal-only, and C is not nearly D.** Two datasets that agree
are still not a measurement taken at racing speed. Corroboration between a walk and
a recording is meaningful only because the recording predates the catalogue and so
cannot have been fitted to it — and even then it is evidence, not validation. Only
a lap driven at the circuit moves a track to D.

The rule for a release is the **lowest** tier among its built-in circuits, not the
highest. One tier-C circuit holds the whole build to the internal channel.

Each circuit's tier, the evidence behind it, and what remains outstanding are recorded
per circuit in
[`01_requirements/ANNEX_A_circuit_evidence.md`](../01_requirements/ANNEX_A_circuit_evidence.md),
§A.2. That register is the authority; it is tied to the shipped catalogue by TL-18, so a
circuit cannot be added without its tier being declared. Read it before choosing a channel
— this file tells you what the tiers mean, not which tier you are at.

---

## Signing

The upload key lives **outside the repository**:

```
~/keystores/trillian-upload.jks
```

Credentials are in `keystore.properties` at the project root, which is
gitignored (`.gitignore` line 22). The keystore is kept outside the tree so that
no `git add -A`, however careless, can ever stage it.

`app/build.gradle.kts` reads that file if it is present. **If it is absent the
build still works** — release simply comes out unsigned, and the Play path
refuses. A missing key degrades the build; it must never break it. A fresh clone
by someone who is not the publisher builds fine.

### Back this up

> **If you lose the upload keystore and are not enrolled in Play App Signing,
> this app can never be updated again.** Not by you, not by Google. The only
> remedy is publishing a new listing under a new package name and abandoning
> every install.

Back up both of these, somewhere that is not this machine:

- `~/keystores/trillian-upload.jks`
- `keystore.properties` (or just the passwords, in a password manager)

**Enrol in Play App Signing.** Google then holds the actual app signing key and
the keystore above becomes only the *upload* key — which Google can reset if you
lose it. It converts an unrecoverable mistake into an inconvenient one.

---

## Identity

| | |
|---|---|
| Package (`applicationId`) | `io.github.emidiofaria.trillian` |
| Kotlin `namespace` | `com.drivingcoach` |
| Display label | Trillian - Driving coach |

The `applicationId` is the app's permanent identity on Play. **From the first
upload it can never be changed** — a different one is a different app, which
existing installs will not upgrade to.

The Kotlin `namespace` is deliberately left as `com.drivingcoach`. Play never
sees it, it affects only the generated `R` class and package declarations, and
renaming it would touch every source file for no external benefit. The two are
allowed to differ and here they do, on purpose.

---

## Versioning

`appVersionName` in `app/build.gradle.kts` is the single source of truth.
`versionCode` is derived as `major*100 + minor` (2.96 → 296), which keeps codes
monotonic across the whole history.

Play requires a strictly increasing `versionCode` on every upload. Raise
`appVersionName` before packaging another Play release; the script checks this
before building and refuses rather than letting you discover it at upload time.

---

## Privacy policy

Play requires a publicly reachable privacy policy URL for any app that requests
location. The policy text lives in `docs/privacy-policy.md` and is versioned with
the code, so it can never drift from what the app actually does.

To publish it with GitHub Pages:

1. **Settings → Pages** on `emidiofaria/Trillian`
2. Source: *Deploy from a branch*; branch `main`, folder `/docs`
3. Save, then wait a minute for the first build
4. The URL is
   `https://emidiofaria.github.io/Trillian/privacy-policy`
5. **Open it in a browser before pasting it into the Console.** Play rejects
   policy URLs that 404, and a private repo will not serve Pages on a free plan.

If the repo is private and you would rather not make it public, a Gist or any
static host works equally well — paste the same Markdown. The requirement is
only that the URL is public, stable, and describes this app.

⚠️ The policy states that nothing is transmitted. That must stay true. If a
backend is ever added, update the policy **and** the Data Safety form in the same
release — see NF-20.

---

## Data safety declaration

Trillian declares **"does not collect or share any user data"**.

That is accurate: the app records precise location and inertial data, but writes
them only to app-private storage. Google defines *collection* as data sent off
the device, and nothing is.

Two independent mechanisms keep the declaration true (**NF-20**), so that it
cannot quietly become false through an unrelated change:

| Guard | Enforced by |
|---|---|
| `BuildConfig.UPLOAD_ENABLED = false` — no upload is ever enqueued | `DataSafetyPolicyTest` (L1) |
| Release manifest declares no `INTERNET` permission | preflight gate in `package-release.sh --target play` |

The second is the stronger one: without the permission the process cannot open a
socket, so telemetry stays local even if upload code were re-enabled by mistake.
It is checked against the real merged release manifest rather than a unit test,
because the debug variant deliberately *does* hold `INTERNET` so that
MockWebServer-backed instrumentation tests can run.

This costs nothing in accuracy. Positioning is done by Google Play services in
its own process under its own permissions; the app makes no network request for
location, and SRS section 8 requires operation in flight mode regardless.

**When a backend is added**, all of the following change in the same commit:
restore `INTERNET` to the main manifest, set `UPLOAD_ENABLED`, update both gates,
rewrite `docs/privacy-policy.md`, and change the Data Safety answers to declare
location collection. Both gates fail first, by design.

---

## Known state

Google Play requires new apps to target **API 36** (Android 16). `targetSdk` and
`compileSdk` are both 36, which required Gradle 8.11.1 and AGP 8.9.1 — AGP 8.5
does not support compiling against 36. L2 evidence for a Play release must come
from the `Trillian_API36` emulator (**NF-19**).

R8/minification is **off** (`isMinifyEnabled = false`), so **NF-12 is not met** —
see the remark in the SRS. This is permitted by Play; the bundle is simply larger
and unobfuscated. Enabling R8 requires keep rules for Gson, Room and Hilt and a
full test pass against a minified build. It is planned work, not a flag flip, and
deliberately not something to attempt on a release day.
