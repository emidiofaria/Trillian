# Backend Deployment Guide

> **Purpose:** Step-by-step instructions to deploy the Driving Coach backend locally for testing and to Azure for production.

---

## Part A: Local Deployment

### Prerequisites Checklist

| Item | Verification | Check |
|------|--------------|-------|
| Node.js 20.x installed | `node --version` shows v20.x | ☐ |
| PostgreSQL running | `pg_isready` returns "accepting connections" | ☐ |
| Database created | `driving_coach` exists | ☐ |
| Repository cloned | `backend/` directory exists | ☐ |

---

### Step 1: Install Dependencies

```bash
cd backend
npm install
```

| Verification | Expected | Check |
|--------------|----------|-------|
| No errors | "added X packages" message | ☐ |
| node_modules exists | Directory created | ☐ |

---

### Step 2: Configure Environment Variables

Create `.env` file in `backend/` directory:

```bash
cp .env.example .env
```

Edit `.env` with your values:

```env
# Server
PORT=3000
NODE_ENV=development

# Database
DATABASE_URL=postgresql://dc_user:your_password@localhost:5432/driving_coach

# Firebase Admin (base64-encoded service account JSON)
FIREBASE_SERVICE_ACCOUNT_BASE64=<paste base64 string here>

# Anthropic API
ANTHROPIC_API_KEY=sk-ant-api03-...

# Azure Storage (local: use file system, production: use Azure Blob)
STORAGE_TYPE=local
UPLOAD_DIR=./uploads

# For Azure Blob Storage (production only)
# STORAGE_TYPE=azure
# AZURE_STORAGE_CONNECTION_STRING=DefaultEndpointsProtocol=https;AccountName=...
# AZURE_STORAGE_CONTAINER=telemetry
```

| Configuration Item | Set | Check |
|--------------------|-----|-------|
| DATABASE_URL | Correct credentials | ☐ |
| FIREBASE_SERVICE_ACCOUNT_BASE64 | Base64 string pasted | ☐ |
| ANTHROPIC_API_KEY | Valid key starting with `sk-ant-` | ☐ |

---

### Step 3: Run Database Migrations

```bash
npm run db:migrate
```

Or manually:
```bash
psql $DATABASE_URL -f src/db/schema.sql
```

| Verification | Expected | Check |
|--------------|----------|-------|
| No errors | Tables created | ☐ |
| Tables exist | `sessions`, `laps`, `coaching_insights` | ☐ |

**Verify tables:**
```bash
psql $DATABASE_URL -c "\dt"
```

Expected output:
```
              List of relations
 Schema |       Name        | Type  |  Owner
--------+-------------------+-------+----------
 public | coaching_insights | table | dc_user
 public | laps              | table | dc_user
 public | sessions          | table | dc_user
```

---

### Step 4: Create Upload Directory

```bash
mkdir -p uploads
```

---

### Step 5: Run Backend Server

**Development mode (with auto-reload):**
```bash
npm run dev
```

**Production mode:**
```bash
npm run build
npm start
```

| Verification | Expected | Check |
|--------------|----------|-------|
| Server starts | "Server running on port 3000" | ☐ |
| No startup errors | Clean console output | ☐ |

---

### Step 6: Verify Backend Health

**Health check:**
```bash
curl http://localhost:3000/health
```

Expected response:
```json
{"status":"healthy","timestamp":"2026-05-06T15:00:00.000Z"}
```

| Endpoint | Expected Status | Check |
|----------|-----------------|-------|
| GET /health | 200 OK | ☐ |
| GET /sessions (no auth) | 401 Unauthorized | ☐ |

**Test unauthorized access:**
```bash
curl http://localhost:3000/sessions
```

Expected: `{"error":"Unauthorized"}`

---

### Step 7: Run Backend Tests

```bash
npm run test:unit
```

| Verification | Expected | Check |
|--------------|----------|-------|
| All tests pass | "X passed, 0 failed" | ☐ |
| Coverage > 70% | Line coverage percentage | ☐ |

---

## Part B: Azure Production Deployment

### Prerequisites

| Item | Verification | Check |
|------|--------------|-------|
| Azure CLI installed | `az --version` works | ☐ |
| Logged into Azure | `az account show` shows correct subscription | ☐ |
| Resource Group exists | `rg-driving-coach` | ☐ |
| App Service created | `app-driving-coach` | ☐ |
| PostgreSQL Flexible Server created | Connection string available | ☐ |
| Storage Account created | Connection string available | ☐ |

---

### Step 1: Configure App Service Settings

```bash
# Set resource group and app name
RG_NAME="rg-driving-coach"
APP_NAME="app-driving-coach"

# Configure environment variables
az webapp config appsettings set \
  --resource-group $RG_NAME \
  --name $APP_NAME \
  --settings \
    NODE_ENV=production \
    DATABASE_URL="postgresql://user:pass@psql-dc.postgres.database.azure.com:5432/driving_coach?sslmode=require" \
    FIREBASE_SERVICE_ACCOUNT_BASE64="<base64 encoded JSON>" \
    ANTHROPIC_API_KEY="sk-ant-..." \
    STORAGE_TYPE="azure" \
    AZURE_STORAGE_CONNECTION_STRING="DefaultEndpointsProtocol=https;..." \
    AZURE_STORAGE_CONTAINER="telemetry"
```

| Setting | Configured | Check |
|---------|------------|-------|
| DATABASE_URL | Azure PostgreSQL connection string | ☐ |
| FIREBASE_SERVICE_ACCOUNT_BASE64 | Service account key | ☐ |
| ANTHROPIC_API_KEY | Production API key | ☐ |
| AZURE_STORAGE_CONNECTION_STRING | Storage account connection string | ☐ |

---

### Step 2: Configure Startup Command

```bash
az webapp config set \
  --resource-group $RG_NAME \
  --name $APP_NAME \
  --startup-file "npm run start"
```

---

### Step 3: Run Database Migrations on Azure PostgreSQL

```bash
# Connect to Azure PostgreSQL
PGPASSWORD=<password> psql \
  -h psql-driving-coach.postgres.database.azure.com \
  -U dc_admin \
  -d driving_coach \
  -f src/db/schema.sql
```

| Verification | Expected | Check |
|--------------|----------|-------|
| No errors | Tables created | ☐ |
| Connection successful | psql prompt appears | ☐ |

---

### Step 4: Deploy Application

**Option A: ZIP Deploy**
```bash
# Build the app
npm run build

# Create deployment package
zip -r deploy.zip dist/ node_modules/ package.json package-lock.json

# Deploy
az webapp deployment source config-zip \
  --resource-group $RG_NAME \
  --name $APP_NAME \
  --src deploy.zip
```

**Option B: GitHub Actions (recommended)**

Create `.github/workflows/deploy-backend.yml`:
```yaml
name: Deploy Backend to Azure
on:
  push:
    branches: [main]
    paths: ['backend/**']

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
      - run: |
          cd backend
          npm ci
          npm run build
      - uses: azure/webapps-deploy@v3
        with:
          app-name: app-driving-coach
          publish-profile: ${{ secrets.AZURE_WEBAPP_PUBLISH_PROFILE }}
          package: ./backend
```

---

### Step 5: Verify Production Deployment

```bash
# Get app URL
APP_URL=$(az webapp show \
  --resource-group $RG_NAME \
  --name $APP_NAME \
  --query "defaultHostName" -o tsv)

# Health check
curl https://$APP_URL/health
```

| Endpoint | Expected | Check |
|----------|----------|-------|
| GET /health | 200, `{"status":"healthy"}` | ☐ |
| HTTPS enforced | Redirects HTTP to HTTPS | ☐ |

---

### Step 6: Configure CORS (if needed)

```bash
az webapp cors add \
  --resource-group $RG_NAME \
  --name $APP_NAME \
  --allowed-origins "*"
```

---

## Part C: Backend Verification Checklist

### Functional Verification

| Test | Command | Expected Result | Check |
|------|---------|-----------------|-------|
| Health check | `curl /health` | 200 OK, JSON response | ☐ |
| Auth required | `curl /sessions` (no token) | 401 Unauthorized | ☐ |
| Invalid token | `curl -H "Authorization: Bearer invalid" /sessions` | 401 Unauthorized | ☐ |

### Database Verification

| Check | Query | Expected | Check |
|-------|-------|----------|-------|
| Sessions table | `SELECT COUNT(*) FROM sessions;` | 0 (or existing count) | ☐ |
| Laps table | `SELECT COUNT(*) FROM laps;` | 0 (or existing count) | ☐ |
| Coaching table | `SELECT COUNT(*) FROM coaching_insights;` | 0 (or existing count) | ☐ |

### Performance Verification

| Metric | Target | Check |
|--------|--------|-------|
| Health check latency | < 100ms | ☐ |
| Server startup time | < 10s | ☐ |
| Memory usage (idle) | < 256 MB | ☐ |

---

## Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| "Cannot connect to database" | DATABASE_URL incorrect | Verify credentials, host, port |
| "Firebase Admin SDK error" | Invalid service account | Re-encode base64, check JSON validity |
| "ANTHROPIC_API_KEY not set" | Missing env var | Add to .env or App Settings |
| "ECONNREFUSED" | Service not running | Start PostgreSQL, check port |
| "Permission denied" | File system | Check uploads/ directory permissions |

### Log Inspection

**Local:**
```bash
npm run dev 2>&1 | tee backend.log
```

**Azure:**
```bash
az webapp log tail \
  --resource-group $RG_NAME \
  --name $APP_NAME
```

---

## Summary Checklist

| Phase | Items | Completed |
|-------|-------|-----------|
| Local: Dependencies | 2 items | ☐ |
| Local: Configuration | 4 items | ☐ |
| Local: Database | 2 items | ☐ |
| Local: Server | 4 items | ☐ |
| Local: Tests | 2 items | ☐ |
| Azure: Settings | 4 items | ☐ |
| Azure: Migration | 2 items | ☐ |
| Azure: Deployment | 3 items | ☐ |
| Verification | 10 items | ☐ |

**Total: 33 items**

---

*Document ID: SAT-BACKEND-001 | Version: 1.0 | Date: 2026-05-06*
