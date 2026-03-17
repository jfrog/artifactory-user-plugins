# Artifactory downloadSelectedArtifactsAsZip User Plugin

Bundle artifacts scattered across repos/versions into a single ZIP. Clients POST a JSON body with a `files` array; the plugin resolves every pattern, streams the archive back (or returns JSON errors), and records misses in headers. The API is intentionally designed to integrate smoothly with **IBM DevOps Deploy (UrbanCode Deploy)** so automated deployment processes can fetch curated bundles as a single step.

## Features

1. **Cross-repo bundling** – Mix and match repository keys, folders, and versions in one request.
2. **Path normalization** – ZIP entries start at `MVS/` or `HFS/`, mirroring mainframe datasets while hiding repo/version prefixes.
3. **Partial success reporting** – `206 Partial Content` + `X-Bundle-Missing` header lists unresolved patterns.
4. **In-memory ZIP delivery** – Builds the archive in-memory and returns it via the plugin bindings.

## Installation

### Plugin paths

| Artifactory version | Plugins directory |
| --- | --- |
| 7.x (default layout) | `$JFROG_HOME/artifactory/var/etc/artifactory/plugins/` |
| 6.x | `$ARTIFACTORY_HOME/etc/plugins/` |

Place **only** `downloadSelectedArtifactsAsZip.groovy` in the plugins directory (no sub-folder required at runtime).

---

### 1 – Bare-metal / VM (Linux)

```bash
cp downloadSelectedArtifactsAsZip.groovy \
   $JFROG_HOME/artifactory/var/etc/artifactory/plugins/

curl -u admin:password -X POST \
     "https://<host>/artifactory/api/plugins/reload"
```

---

### 2 – Docker (standalone container)

```bash
docker cp downloadSelectedArtifactsAsZip.groovy \
    <container_name_or_id>:/opt/jfrog/artifactory/var/etc/artifactory/plugins/

docker exec <container_name_or_id> \
    curl -s -u admin:password -X POST \
    "http://localhost:8082/artifactory/api/plugins/reload"
```

---

### 3 – Docker Compose

```bash
docker compose cp downloadSelectedArtifactsAsZip.groovy \
    artifactory:/opt/jfrog/artifactory/var/etc/artifactory/plugins/

docker compose exec artifactory \
    curl -s -u admin:password -X POST \
    "http://localhost:8082/artifactory/api/plugins/reload"
```

---

### 4 – Kubernetes (Helm chart deployment)

Replace `jfrog-platform` with your namespace and `jfrog-platform-artifactory-0` with your pod name as needed.

```bash
kubectl cp downloadSelectedArtifactsAsZip.groovy \
    jfrog-platform/jfrog-platform-artifactory-0:/opt/jfrog/artifactory/var/etc/artifactory/plugins/ \
    -c artifactory

kubectl exec -n jfrog-platform jfrog-platform-artifactory-0 -c artifactory -- \
    curl -s -u admin:password -X POST \
    "http://localhost:8082/artifactory/api/plugins/reload"
```

To find your pod name and namespace:
```bash
kubectl get pods -A | grep artifactory
```

---

### 5 – Artifactory as a Windows service

```powershell
Copy-Item downloadSelectedArtifactsAsZip.groovy `
    -Destination "$env:JFROG_HOME\artifactory\var\etc\artifactory\plugins\"

Invoke-RestMethod -Method POST `
    -Uri "https://<host>/artifactory/api/plugins/reload" `
    -Credential (Get-Credential)
```

---

### Verify the plugin loaded

```bash
curl -u admin:password \
     "https://<host>/artifactory/api/plugins/execute/downloadSelectedArtifactsAsZipInfo"
```

Expected response:
```json
{
  "plugin": "downloadSelectedArtifactsAsZip",
  "version": "1.0.0",
  "endpoint": "POST /artifactory/api/plugins/execute/downloadSelectedArtifactsAsZip",
  "info": "GET  /artifactory/api/plugins/execute/downloadSelectedArtifactsAsZipInfo",
  "body": "{ \"files\": [ { \"pattern\": \"<repoKey>/<path>\" }, ... ] }"
}
```

## Usage

- **POST** `/artifactory/api/plugins/execute/downloadSelectedArtifactsAsZip`
- **Body**
  ```json
  {
    "files": [
      { "pattern": "zos-repo/lv1-release-1/MVS/JCL/JMON" },
      { "pattern": "zos-repo/lv1-release-1/HFS/USS/bharat.text" }
    ]
  }
  ```
- **Response codes**
  | Scenario | Status | Body |
  | --- | --- | --- |
  | All artifacts found | `200 OK` | ZIP binary |
  | Some missing | `206 Partial Content` | ZIP binary (`X-Bundle-Missing` header) |
  | None found | `404 Not Found` | JSON `{ error, missing }` |
  | Malformed body | `400 Bad Request` | JSON `{ error }` |

### Example curl
```bash
curl -u admin:password \
     -X POST \
     -H "Content-Type: application/json" \
     -d '{
           "files": [
             { "pattern": "zos-repo/lv1-release-1/MVS/JCL/JMON" },
             { "pattern": "zos-repo/lv1-release-1/HFS/USS/bharat.text" }
           ]
         }' \
     --output bundle.zip \
     "https://<host>/artifactory/api/plugins/execute/downloadSelectedArtifactsAsZip"
```

### Example PowerShell
```powershell
Invoke-BundleDownload.ps1 `
    -BaseUrl  "https://<host>/artifactory" `
    -Username "admin" `
    -Password "password" `
    -Patterns "zos-repo/lv1-release-1/MVS/JCL/JMON","zos-repo/lv1-release-1/HFS/USS/bharat.text" `
    -OutputFile "bundle.zip"
```
