# Spec Delta

## Purpose

Lets users discover publicly available remote assets, inspect their versions and licenses, and download packages from a bottom Asset Library panel.

## ADDED Requirements

### Requirement: Bottom Asset Library panel

The editor SHALL provide an Abyssus Asset Library tool window docked at the bottom by default. It SHALL offer search, asset-type filtering, paginated thumbnail results and selected-asset details. Users SHALL be able to move or hide it using normal tool-window controls.

#### Scenario: Open the library
- **WHEN** the user first opens the Asset Library
- **THEN** it appears at the bottom with catalog controls and a loading state

#### Scenario: Inspect an asset
- **WHEN** the user selects a catalog result
- **THEN** details show its name, type, description, author, license, previews and available versions; a missing thumbnail shows a placeholder

### Requirement: Public catalog contract

The new remote API SHALL be defined in `openapi.yml` and require no credentials. It SHALL provide `GET /v1/assets`, `GET /v1/assets/{assetId}` and `GET /v1/assets/{assetId}/versions/{version}`. Listing SHALL support search, one type filter and opaque cursor pagination. Version descriptors SHALL identify an immutable ZIP, SHA-256 checksum, byte size, native format version and included dependencies.

#### Scenario: Browse anonymously
- **WHEN** a client requests assets without authentication
- **THEN** the API returns catalog summaries and a next cursor or null, without a sign-in requirement

#### Scenario: Page within a filter
- **WHEN** a client submits the next cursor with the same search and type filter
- **THEN** the next page continues the stable catalog ordering without duplicate results

#### Scenario: Invalid or unavailable request
- **WHEN** a request uses an invalid cursor, names an absent asset/version, is rate limited or encounters service failure
- **THEN** it receives the documented 400, 404, 429 or 503 error structure respectively, with Retry-After for rate limiting

### Requirement: Configurable remote service

The panel SHALL let users configure the API base URL in IDE project settings, outside `.abss` files. Until configured, it SHALL show setup guidance and make no network request. Production endpoints and download/preview URLs SHALL use HTTPS; loopback HTTP SHALL be allowed for local development.

#### Scenario: No service configured
- **WHEN** the panel opens without a configured URL
- **THEN** it shows endpoint setup guidance and does not contact a placeholder server

### Requirement: Responsive catalog state

Browsing SHALL keep the editor responsive, show loading, empty and retryable failure states, and discard results from superseded requests. Closing the project SHALL cancel pending work. Browsing alone SHALL NOT modify project assets.

#### Scenario: Search changes during a request
- **WHEN** the user changes search text while the previous query is pending
- **THEN** only results for the latest query are displayed

#### Scenario: Offline catalog
- **WHEN** a catalog request fails due to an unavailable service
- **THEN** the panel shows an actionable failure and Retry without changing any project file

### Requirement: Download selected versions

Users SHALL be able to download a selected version to a chosen ZIP file without importing it, or download and import it. The panel SHALL show progress and cancellation. It SHALL verify the descriptor's byte size and SHA-256 before accepting a download; failed or cancelled downloads SHALL leave no partial destination file.

#### Scenario: Download only
- **WHEN** a user downloads a selected version to an unused output path
- **THEN** the complete verified ZIP is saved there and the current project is unchanged

#### Scenario: Failed verification
- **WHEN** downloaded bytes differ from the advertised size or checksum
- **THEN** the download fails with a reason and no completed destination ZIP or project asset is created

#### Scenario: Cancel download
- **WHEN** a user cancels an active download
- **THEN** transfer stops, temporary files are cleaned up and Retry remains available
