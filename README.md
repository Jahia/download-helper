# download-helper

Jahia OSGi module that lets admins trigger server-side file downloads (HTTPS or FTP) from the admin UI. Files land in `/tmp/jahia-download-helper`; email notifications are sent at each stage. Admin UI at `/jahia/administration/downloadHelper`.

## Key Facts

- **artifactId**: `download-helper` | **version**: `2.0.6-SNAPSHOT`
- **Java package**: `org.jahia.modules.downloadhelper`
- **jahia-depends**: `serverSettings,graphql-dxm-provider,default`
- **GraphQL API** — admin UI backed by GraphQL mutations/queries gated by `adminDownloadHelper` permission
- **No Blueprint/Spring** — pure OSGi DS

## Features

- Download files from HTTPS or FTP URLs
- Supports basic authentication (login + password)
- Optional CC email notifications on start, completion, and failure
- Disk space validation before download
- SSRF defense (rejects loopback, link-local, cloud metadata IPs)
- Path-traversal protection for file deletion
- Admin UI with real-time file listing

## Installation

- In Jahia, go to "Administration → Server settings → System components → Modules"
- Upload the JAR **download-helper-2.0.6-SNAPSHOT.jar**
- Check that the module is started

## Usage

1. Configure mail server settings (optional) to receive notifications
2. Go to "Administration → Server settings → Download helper"
3. Enter download details (URL, protocol, filename, credentials, CC email)
4. Click Submit to trigger download
5. Monitor completion via the UI or email

## GraphQL API

| Operation | Permission |
|-----------|------------|
| `downloadHelperInfo` query (server info) | `adminDownloadHelper` |
| `downloadHelperFiles` query (file list) | `adminDownloadHelper` |
| `downloadHelperTrigger` mutation (start download) | `adminDownloadHelper` |
| `downloadHelperDeleteFile` mutation (delete file) | `adminDownloadHelper` |

## Build

```bash
mvn clean install          # Full build
yarn build                 # Frontend only
yarn lint                  # ESLint
```

## Tests

```bash
cd tests
cp .env.example .env
yarn install
./ci.build.sh && ./ci.startup.sh
```

Tests are in `tests/cypress/e2e/01-downloadHelper.cy.ts` and cover info panel, file listing, trigger download, delete file, and form validation.

## Security

This module implements defense-in-depth against SSRF attacks, log injection, chunked-transfer DoS, and thread-safety issues. See `/AGENTS.md` for detailed security invariants under "Security Hardening" and "Security invariants".

