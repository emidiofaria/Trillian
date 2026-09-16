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

## Known state

R8/minification is **off** (`isMinifyEnabled = false`), so **NF-12 is not met** —
see the remark in the SRS. This is permitted by Play; the bundle is simply larger
and unobfuscated. Enabling R8 requires keep rules for Gson, Room and Hilt and a
full test pass against a minified build. It is planned work, not a flag flip, and
deliberately not something to attempt on a release day.
