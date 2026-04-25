# download-helper

Jahia OSGi module that lets admins trigger server-side file downloads (HTTPS or FTP) from the admin UI. Files land in `/tmp/jahia-download-helper`; email notifications are sent at each stage. Admin UI at `/jahia/administration/downloadHelper`.

## Key Facts

- **artifactId**: `download-helper` | **version**: `2.0.4-SNAPSHOT`
- **Java package**: `org.jahia.modules.downloadhelper`
- **jahia-depends**: `serverSettings,graphql-dxm-provider,default`
- **No Blueprint/Spring** — pure OSGi DS

## Architecture

| Class | Role |
|-------|------|
| `DownloadHelperService` | Core service: `download(protocol, url, login, password, filename, ccEmail, user)`; supports `https` and `ftp` only; checks free space before downloading |
| `DownloadHelperQueryExtension` | GraphQL queries |
| `DownloadHelperMutationExtension` | GraphQL mutations |
| `GqlServerInfo` | Query return type: `{isProcessingServer, availableSpace, downloadPath, isMailActivated}` |
| `GqlDownloadedFile` | List item: `{name, size, lastModified}` |
| `Email` | Constants for email subjects and body templates |

`DOWNLOAD_FOLDER_PATH = "/tmp/jahia-download-helper"` — hardcoded, created on `@Activate`.

## GraphQL API

| Operation | Name | Notes |
|-----------|------|-------|
| Query | `downloadHelperInfo` → `GqlServerInfo` | Available space formatted as human-readable (KiB/MiB/GiB) |
| Query | `downloadHelperFiles` → `[GqlDownloadedFile]` | Sorted by last-modified descending |
| Mutation | `downloadHelperTrigger(protocol!, url!, filename!, login, password, email)` → Boolean | Launches async thread; returns immediately |
| Mutation | `downloadHelperDeleteFile(filename!)` → Boolean | Path-traversal–protected via canonical path check |

All operations require `adminSystemInfos` permission.

## Email Notifications

Three states trigger an email to `mailService.defaultRecipient()` (+ optional `ccEmail`):

1. Download started (`DOWNLOAD_ASKED_SUBJECT`)
2. Download completed (`DOWNLOAD_COMPLETED_SUBJECT`)
3. Download failed (insufficient space, folder creation error, HTTP error)

`MailService` is injected as `@Reference`. Notifications are skipped when mail is not configured.

## Build

```bash
mvn clean install          # Full build
yarn build                 # Frontend only
yarn lint                  # ESLint
```

- Frontend entry: `src/javascript/index.js` → component under `src/javascript/DownloadHelper/`
- CSS modules use `downloadHelper_` prefix (e.g. `downloadHelper_container`)
- Admin route target: `administration-server-systemHealth:10`

## Tests (Cypress Docker)

```bash
cd tests
cp .env.example .env
yarn install
./ci.build.sh && ./ci.startup.sh
```

- Tests: `tests/cypress/e2e/01-downloadHelper.cy.ts`
- Tests cover: info panel, file listing, trigger download, delete file, UI form validation
- `assets/provisioning.yml` installs `graphql-dxm-provider` + `serverSettings`

## Gotchas

- Download runs in a raw `new Thread()` — no thread pool, no cancellation; long downloads tie up a JVM thread
- `downloadHelperDeleteFile` uses canonical path check to prevent path-traversal: if `canonicalPath` does not start with `canonicalFolder + File.separator`, the delete is rejected with a warning
- `hasEnoughSpace`: if `contentLength <= 0` (chunked transfer encoding), the check is skipped — download proceeds regardless
- `deleteFile` returns `false` (not an error) for non-existent files
- CSS Modules: match in Cypress with `[class*="downloadHelper_..."]`
