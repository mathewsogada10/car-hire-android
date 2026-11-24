# Car Hire Android

Kotlin / Jetpack Compose / Room daily owner application. Minimum Android API 24. Business writes, validation, reports, exports, backups and local data management do not require a network. Companion repositories: `..\car-hire-backend` and `..\car-hire-web`.

## Functionality

| Screen / area | Behavior |
| --- | --- |
| Navigation | Top-left hamburger opens a grouped, vertically scrollable menu listing every screen, including Home, customers, hires, data, audit and Settings. The active section is highlighted and named in the header. Select a destination to close the menu; Back, the close button or the outside area dismisses it without changing the current screen or draft. No horizontally hidden navigation tabs. |
| Home | Offline KES financial overview with date scope and registration-based vehicle search; select a matching registration to scope recognized charges, received cash, expenses, expected/cash profit, outstanding balances and deposit cash. Clear with All vehicles. Deleted vehicles remain selectable as archived for historical reports. |
| Fleet/customers | UUID CRUD, normalized unique registration, status/odometer/corrections, optional customer licence/emergency information and contact checks before booking. Search/filter/history evidence and protected tombstone deletion. |
| Hires | One hire register ordered by planned pickup (earliest first), with search, local-date filters and a Status selector using the editor's defined statuses: Draft, Booked, Active, Completed and Cancelled. All statuses clears that filter; filters combine. Planned/actual times use the device timezone with UTC storage, manual entry and calendar/24-hour time pickers. Includes lifecycle transitions, customer/vehicle selection, charges, zero-charge confirmation, and availability against hires/service windows with a configurable buffer. |
| Payments | Hire choices include vehicle registration and the local pickup date/time, searchable by registration; the selected label remains readable when the search changes. Separate payments/deposits/refunds/deposit applications, partial/final amounts, manual payment references, received-entry immutability and refund/deposit reconciliation. |
| Expenses | Categories/providers, expense and paid dates, planned/due/paid/cancelled state, notes and private PDF/photo receipts. |
| Maintenance/readings | Service windows, linked expense without duplicate accounting, date/km next-due fields, odometer/fuel readings and correction reasons. |
| Reminders/settings | Enable toggle, advance notice, required daily Nairobi time and backup interval; date/km reminder register, snooze/complete/dismiss history; WorkManager notification delivery, API 24 fallback and API 26+ channels, Android 13+ permission control. Notifications contain no customer details. |
| Appearance/settings access | Top-right three-dot **More options > Settings** menu. Light uses pale-blue surfaces and blue accents; Dark uses navy surfaces and lighter blue accents. System default follows device light/dark mode, retaining the app's blue palette rather than device accent colors. Material controls, cards, drawers, dialogs and system bars follow the theme. Appearance persists locally and is not synced or exported with business data. Settings also identifies the device timezone used for timestamps. |
| Condition/fines | Basic PRE/POST inspection notes, odometer/fuel details, fines/damages and customer/business responsibility. |
| Attachments | SAF file selection, private content-addressed PDF/PNG/JPEG files, MIME signature/size/count/hash checks, save-a-copy and visible pending-upload state. |
| Data | SAF portable ZIPs with manifest/CSV/JSONL/checksums; reopened-file verification and export evidence; encrypted full logical Room/outbox/evidence/settings/attachment backup; authenticated replace restore after an encrypted safety backup. |
| Purge | Verified and deliberately acknowledged export, date scope, PIN reauthentication, fresh preview and typed confirmation. Retain masters/settings/reminders/evidence/referenced records/unsettled deposits; erase eligible local payloads, keep minimal tombstones and prevent silent pull resurrection. Attachment-bearing records remain protected when attachment export evidence is absent. Local purge does not imply erasure of the server copy or external exports. |
| Optional sync | Atomic Room record + outbox write, ordered durable operations, HTTPS-only profile, replay-safe push and cursor pull, retryable network failures, permanent-error visibility, explicit server/local revision conflict resolution with reason, independently checkpointed private file transfer. |
| Security/health | Six-to-twelve-digit rate-limited PIN, biometric convenience with PIN fallback, screen-capture protection, in-memory process-lifetime unlock, Keystore-encrypted credentials, last backup/sync/free-space/error/conflict information and local hash-only audit. |

## Navigation and record summaries

System Back first dismisses the drawer/dialog/keyboard, then closes the editor or returns through visited sections. Filters, scroll position and unsaved editor values are restored when returning, including activity recreation. Selecting Home resets the navigation history; only Back from Home leaves the app.

Hire cards show registration, agreed charge, local planned pickup/return and `Days:X`. Payment cards show registration, amount and payment date. Hire choices show only the local start/end, registration and charge. Expense cards show paid date, falling back to business date when absent. Operational status is a single value; sync state belongs in **Sync & data health**, not a second status on the card.

Hires, Payments and Expenses show a **Filtered total** computed from exactly the matching, non-deleted records. Hire totals use agreed charges; payments and expenses use entry amounts. Payment totals include refunds and failed/pending entries as displayed, so they are not net cash or balance due. The total is not stored in the database.

The hire editor shows a read-only **Number of days** from planned elapsed time, rounding a partial 24-hour day up; this does not change the agreed charge or revenue recognition. Blank actual pickup/return fields default to their corresponding planned values, without replacing an explicit actual time. Saving a payment also fills missing actual times on its linked hire in the same Room/outbox transaction, without changing its status. The server applies the same atomic rule for web payments.

## Build and install

Requirements: JDK 21 (source/bytecode target 17), Android SDK platform/build tools 35, accepted Android SDK licenses. Set `ANDROID_HOME` to your SDK. The first wrapper run needs network access.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
# Device/emulator attached:
.\gradlew.bat connectedDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Open **Car Hire**, create a PIN, then add vehicles and customers before their dependent records. Ten common expense categories are seeded automatically. The app operates without a backend profile. Optional sync configuration is under **Sync & data health** and requires an HTTPS URL, owner username and password. Never enable production cleartext traffic to connect to a development server. Server certificates must be trusted by Android.

Unlock once per running application process. Switching to another app, returning, or recreating/reopening an activity in the same process does not ask for the PIN again. Force-stopping the app, a device restart, or Android terminating its process requires unlocking on the next launch. Authorization is kept only in memory, not persisted. Explicit PIN reauthentication for destructive purge remains separate.

On Home, type all or part of a registration in **Search vehicle registration**, then tap a matching vehicle to apply the report filter. Search ignores case and spaces; it does not search UUIDs, makes or models. Results show the registration, make/model and a short ID for disambiguation. Up to five matches are shown with a refinement hint if more match. **Report vehicle** always names the applied filter: typing an unmatched query does not silently change it. **All vehicles** clears both the filter and search. Date filters apply together with the selected vehicle; report calculations continue to use its UUID internally.

All editable date fields, including report/list filters and the purge cutoff, accept **YYYY-MM-DD** and have a calendar button. Date-time fields accept **YYYY-MM-DDTHH:mm** in the timezone shown beneath the field, or use the calendar button, **Next: time**, and **Use date and time**. Invalid dates remain visible with a field-specific error instead of being silently replaced. Clearing an actual timestamp restores its planned default. If a daylight-saving transition repeats a local time, include its UTC offset (for example `2026-11-01T01:30-04:00`); nonexistent local times are rejected. Local display times omit the redundant GMT suffix; the editor/settings still identify the entry timezone.

Timestamp presentation follows the device timezone, including hire references/cards, editor values, audit/conflict details, export history and sync/backup status. Device timezone changes refresh the display; valid date-time drafts are converted without changing their instant. An invalid draft remains explicitly labelled with its previous timezone until corrected. Stored/synced/exported timestamps remain UTC. Date-only business dates do not shift, and Nairobi-based financial recognition and configured daily reminder scheduling retain their business-timezone semantics.

Open the **top-left hamburger menu** to browse all sections; scroll vertically for the remaining entries. Open **Settings** there or use **top-right ellipsis (More options) > Settings** for appearance and business configuration. Theme selection saves automatically. For reminder delivery, choose **Configure business settings**, enable reminders, supply advance days/time/backup interval and save. On Android 13+, explicitly grant notification permission. Create reminder records with a due date and/or vehicle mileage. WorkManager is approximate, respects device power restrictions and is not an exact-alarm service. Update vehicle/reading mileage to keep km reminders meaningful.

## Storage, sync and recovery

Financial chart checkboxes independently show/hide received payments, paid expenses and net profit. All lines start checked; any pair, any single line or none can be selected. The axis uses only visible lines. Selections survive filter changes, scrolling and activity recreation. Hiding every line shows a prompt without hiding the exact monthly amounts.

**Menu > Financial report** shows monthly lines for received payments less refunds, paid expenses, and net profit (cash basis: payments minus expenses), with exact monthly amounts below the graph. Choose any date range, including a financial year spanning two calendar years, and optionally a vehicle. Empty months show zero, first/last months use only selected dates, and losses appear below zero. Expenses use paid date, falling back to business date. Deposits, deposit applications/refunds, pending/failed payments, unpaid/cancelled expenses and deleted records are excluded. The report works offline from Room, retains its filters during navigation, and supports up to 1200 months. Purged/unsynced local history can differ from the backend. No schema migration is required.

Room is the UI's source of truth. Network results are committed to Room before they become visible. An operation is only acknowledged locally after a transaction commits. Interrupted pushes retain the original operation ID and payload. Parent writes precede child writes. Conflicts preserve the server version in local evidence; keeping a local version revalidates the booking/financial rules and creates a new revision. Received financial entries require compensation, not silent rewriting.

Room schema version 1 is exported under `app\schemas`. There is no pre-existing released database to migrate; future schema changes must add explicit migrations and tests. No destructive migration fallback is configured. The database is app-private, not SQLCipher-encrypted; encrypted recovery copies are created explicitly, and OS/device encryption remains relevant.

Encrypted backup format `CHBK1` uses a random salt, PBKDF2-HMAC-SHA1 (210,000 iterations, for API 24 support), a 256-bit AES-GCM key, random nonce and authenticated header. The recovery passphrase must be at least 12 characters. Backups include business records, outbox, evidence, metadata and private attachment content. They do not export the Android Keystore/PIN or usable backend passwords. Keep your recovery passphrase safe: it is not stored. No plaintext temporary backup is written. Restore validates decryption/schema/records/attachment hashes/free space before replacement and requires choosing an encrypted safety-backup destination. Replace is supported; merge restore is not. Cancellation/wrong passphrase/future schema must not replace the database.

Portable schema `1.0.0` is distinct from full backup. CSV is RFC4180 UTF-8 with `\N` nulls and integer `*_minor` money. `data/records.jsonl` is the required lossless typed-envelope extension used by the companion backend importer. All business files, manifest, hashes and source IDs are preserved. A verified file is not automatically safe to purge: acknowledge its safe storage explicitly using `I VERIFIED AND SAFELY STORED THIS EXPORT`. No automatic post-export deletion occurs. Local reports exclude purged history; use the verified export/backend/restore for that history.

## Code and tests

### Code navigation

Production Kotlin lives under `app\src\main\java\ke\carhire`:

| Folder / entrypoint | Start here for |
| --- | --- |
| `MainActivity.kt`, `CarHireApp.kt` | Manifest entrypoints, secure modal host/lifecycle and application wiring. |
| `ui\workspace` | Workspace state, screen routing, grouped hamburger navigation drawer, current-section header, top-right overflow menu and record deletion. Filters stay owned by the workspace when opening an editor. |
| `ui\theme`, `ui\screens\SettingsScreen.kt` | Shared light/dark Material palettes and the appearance/business-settings entry screen. Theme updates do not recreate the activity or bypass the PIN lock. |
| `ui\lock` | PIN/biometric unlock UI backed by the application-owned, in-memory `security\AppSession.kt`; no pause/stop relocking. The workspace stays mounted beneath the initial modal. |
| `ui\screens` | Home reports, record search/filtering, audit history, data management and sync/conflict screens. |
| `ui\editor` | Record editor, reference label/search index, individual field controls, date-time picker and `Forms.kt`/`Field.kt` metadata. |
| `ui\components` | Shared manual/calendar date fields, calendar dialog, observed device timezone, error text, record cards/presentation and attachment picker/export controls. |
| `data\local` | Room entities, DAO and database definition retain package `ke.carhire` to preserve Room class identities and the historical schema path. `AppearancePreferences` separately stores a non-sensitive device-only theme in app-private SharedPreferences; writes run off the main thread and failures are surfaced. |
| `data\repository` | Atomic record/outbox writes, pull application, conflict resolution and audit evidence. |
| `data\files` | `DataFiles` workflow facade; `BackupFiles` recovery; `PortableExports` ZIP/evidence; `LocalPurge` eligibility/execution; `Documents` verified SAF I/O; attachment storage and backup/preview models. |
| `domain\rules`, `domain\model` | Cohesive offline validation, financial/reminder calculations and the stable settings record ID. |
| `security`, `sync`, `util` | Cryptography/PIN/Keystore vault; sync client/shared exclusion gate; JSON, identifier and timezone-aware date/time parsing/presentation helpers. |
| `SyncWorker.kt`, `ReminderWorker.kt` | Background sync and notification entrypoints. Their qualified names remain `ke.carhire.SyncWorker` and `ke.carhire.ReminderWorker` for already-persisted WorkManager requests. |

The structural layout does not change `ke.carhire` application ID/namespace, `car-hire.db`, Room version 1 or identity hash `3c07f999b22fa73986b4b72afa2eb390`. The historical schema remains `app\schemas\ke.carhire.CarDatabase\1.json`. PIN/preferences/Keystore names, unique work names and backup/export/sync wire formats are unchanged; no migration or destructive fallback is introduced. Recovery/export/purge retain their existing transaction boundaries, verification and shared sync exclusion.

```powershell
.\gradlew.bat ktfmtFormat
.\gradlew.bat ktfmtCheck testDebugUnitTest lintDebug connectedDebugAndroidTest
```

Unit tests exercise financial reconciliation (including 50,000 synthetic records), overlap boundaries, customer contact validation, reminder configuration, normalized registration, integer money, backup authentication, device-timezone/UTC conversion, daylight-saving gaps and repeated hours, strict date entry, searchable hire references, and light/dark text contrast. Date-time conversion preserves existing seconds and date-only business values. Reports group allocations by hire rather than scanning the full payment/damage registers for each hire, and calculation runs off the UI thread. Instrumentation tests exercise actual Room transactions/outbox, offline portable export, encrypted backup/restore, successful-save-only backup age, wrong-passphrase preservation, process-lifetime unlocking across backgrounding/activity recreation/relaunch, manual/calendar hire and payment entry, registration/local-time hire choices, and an open editor's timezone change without altering UTC persistence. Device test reports are under `app\build\reports\androidTests\connected`; unit/lint reports are under `app\build\reports`. The backup-age clock changes only after the SAF destination is written, reopened and hash-verified. App switching leaves the unlocked workspace and current form available for the lifetime of the process.

## Remaining specification/release work

This repository is not a claim that the complete functional specification has passed release acceptance. Full condition-item checklists, recurring expenses/reminder recurrence generation, PDF/shareable statements, utilization/ageing analytics, device/token revocation, migration tests beyond the initial schema, large-dataset/lifecycle/accessibility qualification, complete web/Android filter parity and all offline-sync/restore fault-injection scenarios remain unfinished or unverified. Android portable merge import is not implemented: import through the backend and sync, or replace from an encrypted backup. Keep server/attachment backups and test recovery before using the app for irreplaceable business records.
