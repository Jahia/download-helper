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
| `DownloadHelperService` | Core service: `download(protocol, url, login, password, filename, ccEmail, user)`; supports `https` and `ftp` only; checks free space and applies SSRF host filtering before downloading |
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

## Security Hardening (commit 406ed1c)

- **SSRF defense-in-depth**: URLs whose host resolves to loopback, link-local, site-local, any-local, multicast addresses, or the cloud metadata IP `169.254.169.254` are rejected. Hostnames in `BLOCKED_HOSTS` (`metadata.google.internal`, `metadata.goog`, `metadata`) are also blocked. Applies to both HTTPS and FTP.
- **FTP password URL-encoded** (not just login) to prevent URL parse breakage / leakage via stack traces.
- **Log injection**: CR/LF stripped from user-supplied url/filename/user before SLF4J logging (`sanitizeForLog`), guarded by `isInfoEnabled/isErrorEnabled` (S2629).
- **Chunked-transfer DoS**: when `Content-Length <= 0`, download is now capped at currently-available disk space and aborted if exceeded (previously the size check was skipped entirely).
- **Thread safety**: `SimpleDateFormat` is instantiated per call via `formatNow()` rather than shared across raw download threads (also avoids ThreadLocal classloader leaks in OSGi).

## Security invariants (do not regress) — SECURITY-746

The SSRF and log-injection predicates live in `org.jahia.modules.downloadhelper.util.UrlSecurityUtils`
(pure, no DNS/OSGi, fully unit-tested). Do not duplicate them inline — extend the helper instead.

- **No automatic redirect following on the HTTPS download.** `downloadHttps` follows redirects manually
  in a loop bounded by `UrlSecurityUtils.maxRedirects()` (5). Every hop is re-validated with
  `assertSafeHost`, so a `302 → http://169.254.169.254/` (or any internal/site-local host) is rejected
  *before* the connect, not after the SSRF guard has already been passed.
- **Basic credentials never follow a cross-host redirect.** The `Authorization` header is only added
  when `UrlSecurityUtils.sameHost(currentUrl, initialUrl)` — admin-supplied credentials cannot leak
  to a redirect target.
- **Only `http`/`https` redirect schemes are honored.** `file:`, `ftp:`, `gopher:`, `jar:` etc. in a
  `Location` header are rejected (`UrlSecurityUtils.isAllowedHttpScheme`).
- **All user-controlled values are sanitized before logging.** Both the service and the mutation
  extension route through `UrlSecurityUtils.sanitizeForLog` — log-injection is closed in *every* code
  path that logs `url` / `filename` / `user`, including the async failure catch in the mutation.
- **Accepted residual: DNS-rebinding TOCTOU.** `assertSafeHost` resolves DNS, then the HTTP client
  re-resolves at connect time. Fully closing this requires IP pinning via a custom connection manager;
  given the `adminSystemInfos` permission gate it is documented as an accepted residual rather than
  fixed.

## Gotchas

- Download runs in a raw `new Thread()` — no thread pool, no cancellation; long downloads tie up a JVM thread
- `downloadHelperDeleteFile` uses canonical path check to prevent path-traversal: if `canonicalPath` does not start with `canonicalFolder + File.separator`, the delete is rejected with a warning
- `deleteFile` returns `false` (not an error) for non-existent files
- CSS Modules: match in Cypress with `[class*="downloadHelper_..."]`
