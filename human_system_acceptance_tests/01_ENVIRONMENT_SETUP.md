# Environment Setup Checklist

> **Purpose:** Prepare all required tools and dependencies before building the BMW Driving Coach system.

---

## Prerequisites Checklist

### Hardware Requirements

| Item | Requirement | Check |
|------|-------------|-------|
| Development Machine | Windows 10+, macOS 11+, or Ubuntu 20.04+ | ☐ |
| RAM | Minimum 8 GB, recommended 16 GB | ☐ |
| Disk Space | 30 GB free (Android SDK + Gradle cache) | ☐ |
| Test Device | Android 8.0+ (API 26+), GPS enabled | ☐ |
| Test Device | USB debugging enabled | ☐ |
| Network | Internet connection for Azure/Firebase | ☐ |

### Software Requirements

#### 1. Java Development Kit (JDK)

| Step | Action | Verification | Check |
|------|--------|--------------|-------|
| 1.1 | Install JDK 17 (required for Android Gradle Plugin 8.x) | `java -version` shows 17.x | ☐ |
| 1.2 | Set `JAVA_HOME` environment variable | `echo $JAVA_HOME` shows JDK path | ☐ |

**Installation commands:**
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-17-jdk

# macOS (Homebrew)
brew install openjdk@17
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc

# Windows (winget)
winget install Microsoft.OpenJDK.17
```

---

#### 2. Node.js and npm

| Step | Action | Verification | Check |
|------|--------|--------------|-------|
| 2.1 | Install Node.js 20.x LTS | `node --version` shows v20.x | ☐ |
| 2.2 | Verify npm | `npm --version` shows 10.x | ☐ |

**Installation commands:**
```bash
# Using nvm (recommended)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
source ~/.bashrc
nvm install 20
nvm use 20

# Ubuntu (nodesource)
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs

# macOS (Homebrew)
brew install node@20
```

---

#### 3. Android SDK

| Step | Action | Verification | Check |
|------|--------|--------------|-------|
| 3.1 | Install Android Studio OR command-line tools | SDK tools available | ☐ |
| 3.2 | Install SDK Platform 35 (Android 15) | Listed in SDK Manager | ☐ |
| 3.3 | Install SDK Platform 26 (Android 8.0) | Listed in SDK Manager | ☐ |
| 3.4 | Install Build Tools 35.0.0 | Listed in SDK Manager | ☐ |
| 3.5 | Set `ANDROID_HOME` environment variable | `echo $ANDROID_HOME` shows SDK path | ☐ |
| 3.6 | Add platform-tools to PATH | `adb --version` works | ☐ |

**Environment setup:**
```bash
# Add to ~/.bashrc or ~/.zshrc
export ANDROID_HOME=$HOME/Android/Sdk  # or ~/Library/Android/sdk on macOS
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
```

**Command-line SDK installation:**
```bash
# Accept licenses
yes | sdkmanager --licenses

# Install required packages
sdkmanager "platform-tools"
sdkmanager "platforms;android-35"
sdkmanager "platforms;android-26"
sdkmanager "build-tools;35.0.0"
```

---

#### 4. PostgreSQL

| Step | Action | Verification | Check |
|------|--------|--------------|-------|
| 4.1 | Install PostgreSQL 15+ | `psql --version` shows 15.x | ☐ |
| 4.2 | Start PostgreSQL service | Service running | ☐ |
| 4.3 | Create database `bmw_driving_coach` | Database accessible | ☐ |
| 4.4 | Note connection string | URL format verified | ☐ |

**Installation commands:**
```bash
# Ubuntu
sudo apt install postgresql postgresql-contrib
sudo systemctl start postgresql
sudo systemctl enable postgresql

# macOS (Homebrew)
brew install postgresql@15
brew services start postgresql@15

# Create database
sudo -u postgres psql -c "CREATE DATABASE bmw_driving_coach;"
sudo -u postgres psql -c "CREATE USER bmw_user WITH PASSWORD 'your_password';"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE bmw_driving_coach TO bmw_user;"
```

**Connection string format:**
```
DATABASE_URL=postgresql://bmw_user:your_password@localhost:5432/bmw_driving_coach
```

---

#### 5. Azure CLI (for production deployment)

| Step | Action | Verification | Check |
|------|--------|--------------|-------|
| 5.1 | Install Azure CLI | `az --version` works | ☐ |
| 5.2 | Login to Azure | `az login` successful | ☐ |
| 5.3 | Set subscription | `az account show` correct | ☐ |

**Installation commands:**
```bash
# Ubuntu
curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash

# macOS
brew install azure-cli

# Windows
winget install Microsoft.AzureCLI
```

---

#### 6. Firebase CLI

| Step | Action | Verification | Check |
|------|--------|--------------|-------|
| 6.1 | Install Firebase CLI | `firebase --version` works | ☐ |
| 6.2 | Login to Firebase | `firebase login` successful | ☐ |

**Installation:**
```bash
npm install -g firebase-tools
firebase login
```

---

#### 7. Git

| Step | Action | Verification | Check |
|------|--------|--------------|-------|
| 7.1 | Install Git | `git --version` works | ☐ |
| 7.2 | Clone repository | Repo accessible | ☐ |

---

## Cloud Services Setup

### Firebase Project

| Step | Action | Artifact | Check |
|------|--------|----------|-------|
| F1 | Create Firebase project | Project ID noted | ☐ |
| F2 | Enable Email/Password authentication | Auth method active | ☐ |
| F3 | Download `google-services.json` | File saved locally | ☐ |
| F4 | Generate service account key | JSON file downloaded | ☐ |
| F5 | Base64-encode service account key | Encoded string saved | ☐ |

**Service account key encoding:**
```bash
cat service-account-key.json | base64 -w 0 > firebase-admin-key-base64.txt
```

---

### Azure Resources (Production)

| Step | Action | Resource Name | Check |
|------|--------|---------------|-------|
| A1 | Create Resource Group | `rg-bmw-driving-coach` | ☐ |
| A2 | Create App Service Plan | `asp-bmw-driving-coach` | ☐ |
| A3 | Create App Service (Node.js 20) | `app-bmw-driving-coach` | ☐ |
| A4 | Create Storage Account | `stbmwdrivingcoach` | ☐ |
| A5 | Create Blob Container `telemetry` | Private access | ☐ |
| A6 | Create PostgreSQL Flexible Server | `psql-bmw-driving-coach` | ☐ |
| A7 | Create database on PostgreSQL | `bmw_driving_coach` | ☐ |

---

### Anthropic API

| Step | Action | Artifact | Check |
|------|--------|----------|-------|
| AN1 | Create Anthropic account | Account active | ☐ |
| AN2 | Generate API key | Key starts with `sk-ant-` | ☐ |
| AN3 | Store key securely | In password manager | ☐ |

---

## Environment Validation

Run these commands to verify your environment is ready:

```bash
# Check all tools
echo "=== Environment Check ==="
echo "Java: $(java -version 2>&1 | head -1)"
echo "Node: $(node --version)"
echo "npm: $(npm --version)"
echo "Git: $(git --version)"
echo "PostgreSQL: $(psql --version)"
echo "Android SDK: $ANDROID_HOME"
echo "adb: $(adb --version | head -1)"

# Clone repo if needed
git clone <repository-url> bmw-driving-coach
cd bmw-driving-coach

# Verify project structure
ls -la app/ backend/
```

---

## Summary Checklist

| Category | Items | Completed |
|----------|-------|-----------|
| JDK 17 | 2 items | ☐ |
| Node.js 20 | 2 items | ☐ |
| Android SDK | 6 items | ☐ |
| PostgreSQL | 4 items | ☐ |
| Azure CLI | 3 items | ☐ |
| Firebase CLI | 2 items | ☐ |
| Firebase Project | 5 items | ☐ |
| Azure Resources | 7 items | ☐ |
| Anthropic API | 3 items | ☐ |

**Total: 34 items**

---

*Document ID: SAT-ENV-001 | Version: 1.0 | Date: 2026-05-06*
