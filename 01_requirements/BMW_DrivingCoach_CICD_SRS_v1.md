# BMW Driving Coach — CI/CD System Requirements Specification
**Document ID:** BMW-DC-CICD-001  
**Version:** 1.1  
**Status:** Draft — Pending implementation  
**Platform:** GitHub Actions (self-hosted or GitHub-hosted runners)  
**Related documents:** BMW-DC-SRS-001 (System Requirements Specification)

**Changelog**

| Version | Date | Change |
|---|---|---|
| 1.0 | 2026-06-04 | Initial draft |
| 1.1 | 2026-06-04 | Replace long-lived Azure credentials with OIDC; add Terraform IaC apply stage; defer Firebase E2E auth (documented); mandate latest action versions with explicit pins |

---

## Table of contents

1. [Architecture decisions](#1-architecture-decisions)
2. [Workflow overview](#2-workflow-overview)
3. [Secrets and environment variables](#3-secrets-and-environment-variables)
4. [WF-01 — PR checks](#4-wf-01--pr-checks)
   - 4.1 [Triggers](#41-triggers)
   - 4.2 [Stage: Android lint and static analysis](#42-stage-android-lint-and-static-analysis)
   - 4.3 [Stage: Backend lint and type-check](#43-stage-backend-lint-and-type-check)
   - 4.4 [Stage: Android unit tests](#44-stage-android-unit-tests)
   - 4.5 [Stage: Backend unit tests](#45-stage-backend-unit-tests)
   - 4.6 [Stage: Infrastructure security scan (Checkov)](#46-stage-infrastructure-security-scan-checkov)
   - 4.7 [Stage: Google Checks APK compliance scan](#47-stage-google-checks-apk-compliance-scan)
   - 4.8 [Stage: Android instrumented tests (Appium)](#48-stage-android-instrumented-tests-appium)
   - 4.9 [Gate logic](#49-gate-logic)
5. [WF-02 — Deploy](#5-wf-02--deploy)
   - 5.1 [Triggers](#51-triggers)
   - 5.2 [Stage: Build](#52-stage-build)
   - 5.3 [Stage: Terraform plan and apply](#53-stage-terraform-plan-and-apply)
   - 5.4 [Dev environment deployment](#54-dev-environment-deployment)
   - 5.5 [Production environment deployment](#55-production-environment-deployment)
   - 5.6 [Release artifact packaging](#56-release-artifact-packaging)
6. [Runner requirements](#6-runner-requirements)
7. [Action version registry](#7-action-version-registry)
8. [Non-functional requirements](#8-non-functional-requirements)

---

## 1. Architecture decisions

| ID | Decision | Choice |
|---|---|---|
| CD-AD-01 | CI/CD platform | GitHub Actions |
| CD-AD-02 | Workflow count | Two workflows: `pr-checks.yml` and `deploy.yml` |
| CD-AD-03 | Deployment environments | Two environments in `deploy.yml` — `dev` and `production`, controlled by a single stage flag |
| CD-AD-04 | APK compliance scanning | Google Checks (checks.google.com/code-compliance) via `google-checks/checks-app-scan-github-action` |
| CD-AD-05 | IaC security scanning | Checkov — scans Terraform, Bicep, ARM templates, GitHub Actions workflows, Dockerfiles |
| CD-AD-06 | Android static analysis | Android Lint (built-in) + ktlint for Kotlin style enforcement |
| CD-AD-07 | Backend static analysis | ESLint with TypeScript plugin + tsc --noEmit type check |
| CD-AD-08 | Coverage enforcement | JaCoCo for Android (≥ 95% line + branch); Jest built-in for backend (≥ 95% lines, branches, functions, statements) |
| CD-AD-09 | E2E test framework | Appium 2.x with UIAutomator2 driver, running against Android emulator on the CI runner |
| CD-AD-10 | APK signing | Keystore stored as base64 GitHub secret; decoded at build time; never persisted to disk after use |
| CD-AD-11 | Artifact retention | Debug APKs: 7 days. Release APKs: 90 days. Test reports: 30 days |
| CD-AD-12 | Production deploy gate | Requires `deploy.yml` dev stage success AND a human approval via GitHub environment protection rule |
| CD-AD-13 | Azure authentication | GitHub Actions OIDC — no long-lived credentials stored as secrets. Jobs exchange a short-lived OIDC token for an Azure access token via `azure/login@v3` |
| CD-AD-14 | Azure identity type | Microsoft Entra app registration with federated identity credential (service principal). User-assigned managed identity is the preferred alternative once Azure infrastructure is fully provisioned |
| CD-AD-15 | IaC tooling | Terraform; plan runs on every deploy workflow invocation; apply gated behind environment approval |
| CD-AD-16 | Node.js runtime | Node.js 24 — Node 20 is deprecated on GitHub Actions runners (removed September 16, 2026) |

---

## 2. Workflow overview

```
push to any branch
        │
        ▼
┌──────────────────────────────────────────────────────┐
│  WF-01  pr-checks.yml                                │
│  ──────────────────────────────────────────────────  │
│  lint-android   lint-backend   checkov-scan          │ ← parallel
│  unit-android   unit-backend   google-checks-apk     │ ← parallel
│                 appium-e2e (needs all above)          │
└──────────────────────────┬───────────────────────────┘
                           │ all jobs green
                           ▼
        push to deployment/dev  OR  WF-01 success
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│  WF-02  deploy.yml                                           │
│  ────────────────────────────────────────────────────────── │
│                                                              │
│  build-android ──────────────────────┐                       │
│  build-backend ──────────────────────┤                       │ ← parallel builds
│  terraform-plan (OIDC → Azure) ──────┘                       │
│                       │                                      │
│                       ▼                                      │
│             terraform-apply-dev ──(dev env gate)             │
│             deploy-dev          ──(dev env gate)             │ ← parallel after terraform
│                       │                                      │
│         (tag v*.*.* OR manual prod trigger)                  │
│                       ▼                                      │
│           terraform-apply-prod ──(prod approval)             │
│           deploy-production    ──(prod approval)             │ ← parallel after terraform
│                       │                                      │
│                       ▼                                      │
│               release-artifacts                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. Secrets and environment variables

All secrets are stored in GitHub Actions secrets at the repository level. Environment-scoped secrets override repository-level secrets for `dev` and `production` environments.

> **Azure authentication note:** Azure credentials are _not_ stored as a JSON secret (`AZURE_CREDENTIALS`). Authentication uses GitHub Actions OIDC (CD-AD-13). Only three non-sensitive identifiers are stored as secrets; the access token is minted at runtime and is never persisted.

### Repository-level secrets

| Secret name | Description |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded release keystore file |
| `ANDROID_KEY_ALIAS` | Key alias inside the keystore |
| `ANDROID_KEY_PASSWORD` | Key password |
| `ANDROID_STORE_PASSWORD` | Keystore store password |
| `GOOGLE_CHECKS_API_KEY` | API key for Google Checks code compliance service |
| `GOOGLE_CHECKS_ACCOUNT_ID` | Google Checks account identifier |
| `FIREBASE_GOOGLE_SERVICES_JSON` | Base64-encoded `google-services.json`; required by the Firebase SDK at build time (see note below) |
| `AZURE_CLIENT_ID` | Microsoft Entra app registration client ID — used by OIDC login, not a credential |
| `AZURE_TENANT_ID` | Azure Active Directory tenant ID |
| `AZURE_SUBSCRIPTION_ID` | Azure subscription ID |

> **`FIREBASE_GOOGLE_SERVICES_JSON`:** This is required for the Gradle build to compile — the Firebase Authentication SDK reads it at compile time. It is _not_ used to authenticate against Firebase at runtime in CI; it is purely a build-time config file. The file contains no user credentials.

### Environment-scoped secrets (`dev` and `production`)

| Secret name | Description |
|---|---|
| `DATABASE_URL` | PostgreSQL connection string for the target environment |
| `ANTHROPIC_API_KEY` | API key for AI coaching feature |
| `JWT_SECRET` | JWT signing secret for the backend |

### OIDC setup prerequisites (one-time, performed outside GitHub Actions)

| Step | Action |
|---|---|
| OIDC-SETUP-01 | Create a Microsoft Entra app registration (or user-assigned managed identity) in the Azure portal or via `az ad app create`. |
| OIDC-SETUP-02 | On the app registration, add two **federated identity credentials** — one for the `dev` GitHub environment and one for `production` — specifying the repository and environment name as the subject. |
| OIDC-SETUP-03 | Assign the app registration the minimum required roles on the target subscription or resource group: `Contributor` for App Service deployments; `Owner` or a custom role for Terraform state backend access. |
| OIDC-SETUP-04 | Store the three non-sensitive identifiers (`AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`) as repository secrets. |
| OIDC-SETUP-05 | The federated identity credential subject must match the GitHub OIDC token's `sub` claim exactly. For environment-scoped runs the format is `repo:<org>/<repo>:environment:<env-name>`. |

### Requirements

| ID | Requirement |
|---|---|
| SEC-01 | No secret value shall appear in any workflow log. All secrets must be accessed via `${{ secrets.NAME }}` — never hardcoded or echoed. |
| SEC-02 | The decoded keystore file and `google-services.json` shall be written to a temporary path and deleted (or left to runner teardown) immediately after the build step that requires them. |
| SEC-03 | The `production` GitHub environment shall have a required reviewer protection rule configured so that at least one designated approver must approve deployments before they run. |
| SEC-04 | The `dev` GitHub environment shall have a wait-timer of zero and no required reviewer, allowing automated promotion from the build stage. |
| SEC-05 | All jobs that call `azure/login@v3` shall include `permissions: id-token: write` at the job level. Jobs that do not need Azure access shall omit this permission. |
| SEC-06 | The Terraform state backend (Azure Storage) shall be accessed using the same OIDC identity. The storage account shall have public access disabled and use Azure AD authentication only (no storage account key). |

---

## 4. WF-01 — PR checks

**File:** `.github/workflows/pr-checks.yml`

### 4.1 Triggers

| ID | Requirement |
|---|---|
| PR-TR-01 | The workflow shall trigger on `push` to any branch. |
| PR-TR-02 | The workflow shall trigger on `pull_request` targeting any branch. |
| PR-TR-03 | Pushes to branches matching `deployment/**` shall be excluded from this workflow's triggers; those branches feed directly into `WF-02`. |
| PR-TR-04 | The workflow shall be cancellable — concurrent runs on the same branch shall use `concurrency: { group: pr-checks-${{ github.ref }}, cancel-in-progress: true }`. |

### 4.2 Stage: Android lint and static analysis

**Job name:** `lint-android`

| ID | Requirement |
|---|---|
| LA-01 | The job shall run `./gradlew lint` and fail the workflow if any lint errors with severity `error` are reported. |
| LA-02 | The job shall run ktlint via `./gradlew ktlintCheck`. A non-zero exit code shall fail the workflow. |
| LA-03 | The lint HTML report (`app/build/reports/lint-results-debug.html`) shall be uploaded as a workflow artifact named `lint-android-report` on both success and failure. |
| LA-04 | The job shall cache the Gradle wrapper and caches directory (`.gradle/caches`, `.gradle/wrapper`) keyed on the hash of `**/*.gradle.kts` and `gradle/libs.versions.toml`. |

### 4.3 Stage: Backend lint and type-check

**Job name:** `lint-backend`

| ID | Requirement |
|---|---|
| LB-01 | The job shall run `npm ci` inside the `backend/` directory and then `npm run lint`. A non-zero exit code shall fail the workflow. |
| LB-02 | The job shall run `npx tsc --noEmit` to verify the TypeScript compilation produces no errors. A non-zero exit code shall fail the workflow. |
| LB-03 | The job shall cache `backend/node_modules` keyed on the hash of `backend/package-lock.json`. |

### 4.4 Stage: Android unit tests

**Job name:** `unit-android`

| ID | Requirement |
|---|---|
| UA-01 | The job shall run `./gradlew testDebugUnitTest jacocoTestReport`. |
| UA-02 | JaCoCo shall be configured to enforce a minimum of **95% line coverage** and **95% branch coverage** across the `com.bmw.drivingcoach` package. The build shall fail if either threshold is not met. |
| UA-03 | The JaCoCo HTML report (`app/build/reports/jacoco/`) and the JUnit XML results (`app/build/test-results/`) shall be uploaded as artifacts named `unit-android-report` and `unit-android-junit` respectively. |
| UA-04 | The GitHub Actions test-reporter action (or equivalent) shall parse the JUnit XML and publish test results inline in the pull request check. |
| UA-05 | The job shall use the same Gradle cache defined in LA-04. |

### 4.5 Stage: Backend unit tests

**Job name:** `unit-backend`

| ID | Requirement |
|---|---|
| UB-01 | The job shall run `npm test` (which executes `jest --coverage`) inside `backend/`. |
| UB-02 | The Jest coverage thresholds in `jest.config.js` shall be set to **95%** for lines, branches, functions, and statements. The build shall fail if any threshold is not met. |
| UB-03 | The coverage report (`backend/coverage/`) and the JUnit-format XML (generated via `jest-junit` reporter) shall be uploaded as artifacts named `unit-backend-report`. |
| UB-04 | The job shall use the same `node_modules` cache defined in LB-03. |

### 4.6 Stage: Infrastructure security scan (Checkov)

**Job name:** `checkov-scan`

| ID | Requirement |
|---|---|
| CK-01 | The job shall run the latest `bridgecrew/checkov-action` GitHub Action against the entire repository. |
| CK-02 | The scan shall cover: GitHub Actions workflows (`.github/workflows/`), any Terraform, Bicep, or ARM template files, any Dockerfiles, and `docker-compose` files. |
| CK-03 | Checkov shall be configured with `--framework all` to future-proof the scan as infrastructure code is added. |
| CK-04 | The scan results shall be output in SARIF format and uploaded to GitHub's Code Scanning (Security tab) via `github/codeql-action/upload-sarif`. |
| CK-05 | The workflow shall fail if Checkov reports any `HIGH` or `CRITICAL` severity findings that are not suppressed by an inline `checkov:skip` comment with a documented justification. |
| CK-06 | A `checkov.yaml` configuration file shall be maintained at the repository root to define skip rules, allowing teams to suppress false positives with tracked justifications rather than ad-hoc inline comments. |

### 4.7 Stage: Google Checks APK compliance scan

**Job name:** `google-checks-apk`

**Overview:** This stage builds a debug APK and submits it to the Google Checks code compliance service for a thorough static analysis of privacy, security, and policy compliance issues. This is the most comprehensive automated APK scan available and covers data safety, permissions misuse, sensitive data leakage, and SDK compliance.

| ID | Requirement |
|---|---|
| GC-01 | The job shall build a debug APK via `./gradlew assembleDebug`. |
| GC-02 | The `google-services.json` file shall be decoded from `FIREBASE_GOOGLE_SERVICES_JSON` secret and placed at `app/google-services.json` before the Gradle build. |
| GC-03 | The APK shall be submitted to the Google Checks API using the official `google-checks/google-checks-ci-cd-action` GitHub Action (or equivalent CLI invocation). |
| GC-04 | The action shall authenticate using `GOOGLE_CHECKS_API_KEY` and `GOOGLE_CHECKS_ACCOUNT_ID` secrets. |
| GC-05 | The scan shall be configured to run the full compliance suite, including but not limited to: privacy policy compliance, data safety declarations, permissions usage, sensitive data handling, and SDK usage analysis. |
| GC-06 | The job shall poll for scan completion with a timeout of 30 minutes. If the scan does not complete within this window the job shall fail with a descriptive error message. |
| GC-07 | The workflow shall fail if Google Checks reports any finding with severity `PRIORITY` or `POTENTIAL`. Informational findings shall be reported but shall not block the workflow. |
| GC-08 | The full scan report shall be uploaded as a workflow artifact named `google-checks-report` with 30-day retention. |
| GC-09 | This job shall run in parallel with `unit-android` to avoid blocking the pipeline on APK build time. It shall not depend on `unit-android` or `unit-backend`. |

### 4.8 Stage: Android instrumented tests (Appium)

**Job name:** `appium-e2e`

**Overview:** This stage launches an Android emulator on the CI runner and uses Appium to execute UI-level tests covering the critical user flows of the application.

> **Firebase E2E authentication — deferred.**  
> Flows E2E-F-01 (registration) and E2E-F-02 (login) require a live Firebase Authentication call, which in turn requires a dedicated CI Firebase test account (`FIREBASE_TEST_EMAIL`, `FIREBASE_TEST_PASSWORD`) and a Firebase project configured to allow test sign-ins.  
> This is deferred because: (a) provisioning a stable Firebase test project and test user is an out-of-band manual step that is not yet scheduled; (b) the auth UI is covered by unit tests at the ViewModel layer; (c) adding a real Firebase dependency increases flake risk on emulator runs.  
> **When unblocked:** Create a dedicated Firebase project for CI, provision a test account with no real data, add `FIREBASE_TEST_EMAIL` and `FIREBASE_TEST_PASSWORD` as repository secrets, and remove this deferral notice. Until then, E2E-F-01 and E2E-F-02 are replaced by a stubbed auth flow (E2E-08-DEFERRED below).

| ID | Requirement |
|---|---|
| E2E-01 | The job shall depend on `lint-android`, `lint-backend`, `unit-android`, and `unit-backend` — it shall only run if all four pass. |
| E2E-02 | The runner shall use `reactivecircus/android-emulator-runner@v2` (currently v2.36.0) to start an Android emulator with API level 30, x86_64 ABI, and `google_apis` system image. |
| E2E-03 | The emulator shall be configured with the following options: `ram-size 4096M`, `disk-size 8G`, hardware acceleration enabled (`-accel on`), and no-window mode (`-no-window`). |
| E2E-04 | A debug APK shall be built and installed on the emulator via `adb install`. The `google-services.json` shall be injected as described in GC-02. |
| E2E-05 | An Appium 2.x server with the `uiautomator2` driver shall be started before tests run. |
| E2E-06 | The following user flows shall be covered by Appium tests. Additional flows shall be added per sprint as new features are merged: |

| Flow ID | User flow | Status | Minimum assertions |
|---|---|---|---|
| E2E-F-01 | Registration — new user registers with email and password | **DEFERRED** — see Firebase note above | Reaches Home screen; Firebase UID is non-null |
| E2E-F-02 | Login — existing test user signs in | **DEFERRED** — see Firebase note above | Home screen is displayed; session token is present |
| E2E-F-03 | Onboarding permissions — location and sensor permissions are granted | Active | Permission screens complete without crash |
| E2E-F-04 | Track setup — user sets a start/finish line on the map | Active | Map marker is visible; line is persisted across app restart |
| E2E-F-05 | Session recording start/stop — user starts and stops a recording session | Active | Session appears in history list; JSONL telemetry file is non-empty |
| E2E-F-06 | Session upload — telemetry is uploaded when Wi-Fi is available | Active | Upload status transitions to `UPLOADED` within 60 seconds |

| ID | Requirement |
|---|---|
| E2E-07 | The Appium test suite shall be written in a dedicated `e2e/` directory at the repository root using JavaScript/TypeScript with `webdriverio` as the Appium client. |
| E2E-08-DEFERRED | *(Deferred)* A Firebase test account shall be maintained as repository secrets and used for E2E-F-01 and E2E-F-02. Until provisioned, those flows shall be skipped and the job shall not fail on their absence. A GitHub issue shall be opened tracking this gap when the E2E suite is first implemented. |
| E2E-09 | Test results shall be output in JUnit XML format and uploaded as artifact `appium-e2e-report`. Video recordings of failing tests shall be captured using the emulator's screen-record capability and uploaded alongside the report. |
| E2E-10 | The job shall have a timeout of 45 minutes. If the emulator fails to boot within 10 minutes the job shall abort with an actionable error message. |

### 4.9 Gate logic

| ID | Requirement |
|---|---|
| GT-01 | A branch protection rule shall require all `pr-checks.yml` jobs to pass before a pull request can be merged to `main`. |
| GT-02 | The `deploy.yml` workflow's dev stage shall only proceed if `pr-checks.yml` completed with conclusion `success` on the same commit SHA, enforced via `workflow_run` trigger. |
| GT-03 | Individual jobs within `pr-checks.yml` that can run in parallel (`lint-android`, `lint-backend`, `unit-android`, `unit-backend`, `google-checks-apk`, `checkov-scan`) shall do so — they shall have no `needs` dependencies on each other. |
| GT-04 | `appium-e2e` is the only job that depends on others (see E2E-01); it acts as the final gate for the PR checks workflow. |

---

## 5. WF-02 — Deploy

**File:** `.github/workflows/deploy.yml`

### 5.1 Triggers

| ID | Requirement |
|---|---|
| DP-TR-01 | The workflow shall trigger via `workflow_run` when `pr-checks.yml` completes with conclusion `success` on a branch other than `main` or `deployment/**`. |
| DP-TR-02 | The workflow shall trigger on `push` to branches matching `deployment/dev` — bypassing the `pr-checks.yml` gate for hotfix deployments to dev. Teams using this bypass must create a post-deployment tracking issue. |
| DP-TR-03 | The workflow shall trigger on `push` of tags matching `v[0-9]+.[0-9]+.[0-9]+` to initiate a production release. |
| DP-TR-04 | The workflow shall support `workflow_dispatch` (manual trigger) with inputs: `environment` (choice: `dev` \| `production`) and `reason` (free-text string, required). The reason shall be logged at the start of the run. |
| DP-TR-05 | Concurrent runs on the same environment shall be serialized using `concurrency: { group: deploy-${{ inputs.environment || 'dev' }}, cancel-in-progress: false }` — runs shall queue, not cancel. |

### 5.2 Stage: Build

The build stage runs once and produces artifacts consumed by both the dev and production deployment stages.

**Jobs:** `build-android` and `build-backend` (run in parallel)

#### build-android

| ID | Requirement |
|---|---|
| BA-01 | The job shall build a **release APK** via `./gradlew assembleRelease`. |
| BA-02 | Before building, `google-services.json` shall be decoded from `FIREBASE_GOOGLE_SERVICES_JSON` and placed at `app/google-services.json`. |
| BA-03 | APK signing shall be performed via Gradle's `signingConfigs` block, with keystore path, alias, and passwords injected from secrets (CD-AD-10). The keystore shall be decoded from `ANDROID_KEYSTORE_BASE64` to a temp file immediately before the Gradle invocation. |
| BA-04 | The signed release APK (`app/build/outputs/apk/release/app-release.apk`) shall be uploaded as artifact `android-release-apk` with 90-day retention. |
| BA-05 | The job shall also produce and upload a mapping file (`app/build/outputs/mapping/release/mapping.txt`) as artifact `android-proguard-mapping` for crash de-obfuscation. This step is a no-op until ProGuard/R8 is enabled but the job step shall exist as a placeholder. |

#### build-backend

| ID | Requirement |
|---|---|
| BB-01 | The job shall run `npm ci && npm run build` inside `backend/` to produce compiled JavaScript output in `backend/dist/`. |
| BB-02 | The compiled `dist/` directory and `package.json` / `package-lock.json` shall be archived as artifact `backend-dist` for use by deployment jobs. |
| BB-03 | The job shall fail if `tsc` reports any compilation errors. |

### 5.3 Stage: Terraform plan and apply

**Overview:** Terraform manages all Azure infrastructure (App Service, PostgreSQL, Blob Storage, networking). The plan job runs on every deploy workflow invocation so infrastructure drift is visible even on dev deployments. Apply is gated behind the environment approval to prevent accidental production changes.

#### terraform-plan

**Job name:** `terraform-plan`  
**Depends on:** `build-android`, `build-backend` (runs in parallel with them — no dependency; listed here for logical grouping)  
**GitHub environment:** none (read-only; no approval required)

| ID | Requirement |
|---|---|
| TF-01 | The job shall use `hashicorp/setup-terraform@v4` to install the Terraform CLI. The Terraform version shall be pinned in a `.terraform-version` file at the repository root (or via `terraform_version` input) and kept up to date. |
| TF-02 | The job shall authenticate to Azure using OIDC via `azure/login@v3` with `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, and `AZURE_SUBSCRIPTION_ID`. The job shall declare `permissions: id-token: write`. |
| TF-03 | The Terraform state backend shall be an Azure Storage account container. Backend configuration (storage account name, container name, key) shall be passed via environment variables at `terraform init` time — never hardcoded in `.tf` files. |
| TF-04 | The job shall run `terraform init -backend-config=...` followed by `terraform validate` and `terraform plan -out=tfplan`. |
| TF-05 | The plan output shall be formatted as a comment on the pull request (if triggered by a PR) using `github-script` or the `terraform-actions/github-pr-comment` pattern. |
| TF-06 | The `tfplan` binary file shall be uploaded as artifact `terraform-plan-${{ github.sha }}` for consumption by the apply jobs. |
| TF-07 | The Terraform working directory shall be `infrastructure/` at the repository root. This directory shall be created as a placeholder until infrastructure code is written, containing at minimum a `versions.tf` declaring required providers. |

#### terraform-apply-dev

**Job name:** `terraform-apply-dev`  
**Depends on:** `terraform-plan`  
**GitHub environment:** `dev`

| ID | Requirement |
|---|---|
| TF-08 | The job shall download the `terraform-plan-${{ github.sha }}` artifact and run `terraform apply tfplan` non-interactively. |
| TF-09 | Authentication shall use the same OIDC pattern (TF-02) scoped to the `dev` GitHub environment. |
| TF-10 | If Terraform apply fails, the job shall fail the workflow and the downstream `deploy-dev` job shall not run. |

#### terraform-apply-prod

**Job name:** `terraform-apply-prod`  
**Depends on:** `deploy-dev`  
**GitHub environment:** `production` (requires human approval — same gate as deploy-production)  
**Condition:** Same as `deploy-production` (tag or manual dispatch).

| ID | Requirement |
|---|---|
| TF-11 | Same as TF-08/TF-09 but targeting the production Azure environment. |
| TF-12 | Terraform apply for production shall always run _before_ `deploy-production` to ensure infrastructure is in the desired state before the application is deployed. |

### 5.4 Dev environment deployment

**Job name:** `deploy-dev`  
**Depends on:** `terraform-apply-dev`  
**GitHub environment:** `dev`

| ID | Requirement |
|---|---|
| DD-01 | The job shall authenticate to Azure using OIDC via `azure/login@v3` (OIDC — no `AZURE_CREDENTIALS` secret). The job shall declare `permissions: id-token: write`. |
| DD-02 | **Placeholder:** Until the Azure infrastructure is provisioned, this step shall be a shell command that logs `[PLACEHOLDER] Backend deployment to dev — infrastructure TBD` and exits with code 0. The step shall be annotated with a `TODO(infra):` comment identifying the Azure App Service target. |
| DD-03 | The backend deployment step shall run database migrations via `npm run db:migrate` against `DATABASE_URL` from the `dev` environment secrets before starting the application. |
| DD-04 | After backend deployment, a health-check HTTP GET shall be issued to `${{ vars.BACKEND_DEV_URL }}/health`. The job shall retry up to 5 times with 15-second intervals before failing. |
| DD-05 | The release APK artifact from `build-android` shall be re-uploaded as `android-apk-dev-${{ github.sha }}` to provide a traceable per-commit dev build. |
| DD-06 | On completion, the job shall post a deployment summary to the GitHub deployment API marking the `dev` environment URL and commit SHA. |

### 5.5 Production environment deployment

**Job name:** `deploy-production`  
**Depends on:** `terraform-apply-prod`  
**GitHub environment:** `production` (requires human approval — see SEC-03)  
**Condition:** Only runs when the triggering event is a version tag push (`v*.*.*`) or a `workflow_dispatch` with `environment: production`.

| ID | Requirement |
|---|---|
| DP-01 | The job shall not run automatically after `deploy-dev`; it shall only run when the trigger condition above is satisfied AND the GitHub environment approval gate has been approved. |
| DP-02 | The job shall authenticate to Azure using OIDC via `azure/login@v3` scoped to the `production` GitHub environment. No long-lived credentials shall be used. |
| DP-03 | **Placeholder:** Until infrastructure is provisioned, this step shall log `[PLACEHOLDER] Backend deployment to production — infrastructure TBD` and exit 0. |
| DP-04 | The job shall run database migrations against the production `DATABASE_URL` before starting the application. |
| DP-05 | After backend deployment, a health-check shall be issued to `${{ vars.BACKEND_PROD_URL }}/health` with the same retry logic as DD-04. |
| DP-06 | The job shall post a deployment record to the GitHub deployment API marking the `production` environment. |

### 5.6 Release artifact packaging

**Job name:** `release-artifacts`  
**Depends on:** `deploy-production`  
**Condition:** Same as `deploy-production`.

| ID | Requirement |
|---|---|
| RA-01 | The job shall download the `android-release-apk` artifact produced by `build-android`. |
| RA-02 | The job shall create a GitHub Release (via `gh release create`) for the triggering version tag, attaching the signed APK and the ProGuard mapping file. |
| RA-03 | The release notes shall be auto-generated from the git log between the current tag and the previous version tag (`git log <prev>...<current> --oneline`). |
| RA-04 | The GitHub Release shall be marked as `latest` for tags without a pre-release identifier (e.g. `v1.2.3`), and as `pre-release` for tags with a suffix (e.g. `v1.2.3-rc.1`). |
| RA-05 | The APK filename attached to the release shall follow the naming convention `bmw-driving-coach-<version>-release.apk` (e.g. `bmw-driving-coach-v1.0.0-release.apk`). |
| RA-06 | The backend `dist` artifact shall also be attached to the GitHub Release as `backend-dist-<version>.tar.gz` for traceability. |

---

## 6. Runner requirements

| ID | Requirement |
|---|---|
| RN-01 | `lint-android`, `unit-android`, `google-checks-apk`, `build-android`, and `appium-e2e` jobs shall run on `ubuntu-latest` with at minimum 4 CPU cores and 16 GB RAM to support the Android emulator and Gradle daemon. |
| RN-02 | `lint-backend`, `unit-backend`, `build-backend`, and Terraform jobs shall run on `ubuntu-latest` (default GitHub-hosted runner is sufficient). |
| RN-03 | Appium e2e tests (`appium-e2e`) require hardware acceleration. If GitHub-hosted runners do not provide KVM, a self-hosted runner with nested virtualisation enabled shall be used for this job only. |
| RN-04 | All jobs shall specify `timeout-minutes` at the job level to prevent runaway jobs consuming billed minutes. Maximum values: lint jobs 15 min; unit test jobs 30 min; APK build/scan jobs 45 min; appium-e2e 60 min; deploy/terraform jobs 30 min. |

---

## 7. Action version registry

> **Policy:** All GitHub Actions shall be pinned to the latest stable major version tag at the time of implementation. Before implementing any workflow step, the implementer shall verify the current latest version against the action's GitHub releases page. Pinning to `@master` or `@latest` is prohibited — it breaks reproducibility and bypasses security review of action updates. This table shall be updated on every quarterly dependency review.
>
> **Deprecation rule:** If a deprecation notice exists for an action or runtime version, migration is mandatory before the workflow is merged. The deprecations listed below were verified on 2026-06-04.

| Action | Pin | Notes |
|---|---|---|
| `actions/checkout` | `@v6` | Latest as of June 2026 |
| `actions/setup-java` | `@v5` | Use `distribution: temurin` — AdoptOpenJDK distribution is deprecated |
| `actions/setup-node` | `@v6` | Always pin `node-version: '24'` — Node 20 deprecated Sept 2025, removed from runners Sept 16 2026 |
| `actions/upload-artifact` | `@v4` | v3 deprecated January 30 2025 — do not use v3 |
| `actions/download-artifact` | `@v4` | v3 deprecated January 30 2025 — do not use v3 |
| `actions/cache` | `@v5` | Requires Actions Runner ≥ 2.327.1 |
| `gradle/actions/setup-gradle` | `@v6` | Replaces deprecated `gradle/gradle-build-action` |
| `reactivecircus/android-emulator-runner` | `@v2` (v2.36.0) | Actively maintained; verify latest patch before implementing |
| `bridgecrew/checkov-action` | `@master` | Exception to the no-`@master` rule — Checkov releases new checks continuously and pinning a minor version misses critical new rules; pin to `@master` is the upstream recommendation |
| `google-checks/checks-app-scan-github-action` | `@v1.0.3` | Latest as of June 2026; use `@latest` as fallback if 1.0.3 is superseded |
| `azure/login` | `@v3` | v2 superseded; v3 adds Node 24 runtime |
| `hashicorp/setup-terraform` | `@v4` | Latest stable |
| `github/codeql-action/upload-sarif` | `@v3` | For SARIF upload from Checkov |
| `rhysd/actionlint` (via wrapper) | Use `eifinger/actionlint-action@v3` or equivalent | No official action from rhysd; verify wrapper currency before use |

---

## 8. Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-01 | The total elapsed time for `WF-01` (pr-checks) from trigger to final gate shall not exceed **30 minutes** under normal conditions. If median runtime exceeds this threshold, the pipeline shall be reviewed for parallelisation improvements. |
| NFR-02 | The `deploy.yml` build stage shall not exceed **20 minutes**. |
| NFR-03 | All workflow YAML files shall pass `actionlint` as part of the `checkov-scan` job. Workflow syntax errors caught by `actionlint` shall be treated as blocking failures. |
| NFR-04 | Workflow files shall follow DRY principles — shared setup steps (Gradle cache, Node cache, `google-services.json` decode, OIDC login) shall be extracted into composite actions in `.github/actions/`. |
| NFR-05 | Every job shall define `permissions` at the job level using the principle of least privilege. Jobs that only read code shall use `contents: read`. Jobs uploading to Code Scanning shall add `security-events: write`. Jobs using OIDC shall add `id-token: write`. |
| NFR-06 | Artifact names shall be deterministic and include the git SHA or run ID to allow cross-referencing across workflow runs. |
| NFR-07 | The pipeline shall be documented in a `CONTRIBUTING.md` section explaining how to interpret build failures, how to add new Appium test flows, how to suppress Checkov findings responsibly, and how to rotate the Android keystore. |
| NFR-08 | Action versions in the registry (Section 7) shall be reviewed and updated at minimum once per quarter. The review shall check for deprecation notices, Node.js runtime changes, and security advisories. |
