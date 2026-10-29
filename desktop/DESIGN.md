# Reaktiv Control — architecture contract

Internal developer contract for turning the existing Kotlin / Compose Desktop app (`desktop/`) into
**Reaktiv Control — Forensic Laboratory Management System** (the colleague's technical specification,
sections 1–20). Every module is written against this document. User-facing text is **Uzbek (Latin)**,
code comments follow the repo style (short, Uzbek), identifiers are English.

Technology (fixed — we extend the existing Kotlin app instead of the spec's Python stack, by the owner's decision):

| Spec item | Our implementation |
|---|---|
| Python 3 + PySide6 | Kotlin 2.1 (JVM 17) + Compose Multiplatform 1.7.3 (Material3) |
| SQLite | `org.xerial:sqlite-jdbc` (one file DB, bundled natives, offline) |
| SQLAlchemy / SQL layer | small typed SQL layer (`db/` package), plain JDBC prepared statements |
| openpyxl | `org.dhatim:fastexcel` (write) + `org.dhatim:fastexcel-reader` (read) |
| ReportLab | `com.github.librepdf:openpdf:2.0.5` (Java 17 build) + bundled DejaVu Sans TTF (Uzbek Latin glyphs) |
| Matplotlib / Charts | own Compose `Canvas` charts |
| PyInstaller EXE | existing jpackage pipeline: MSI + portable folder with `ReaktivControl.exe` + .deb (GitHub Actions) |

Everything works offline. Nothing is sent anywhere.

---------------------------------------------------------------------------------------------------

## 1. Files and folders

```
desktop/src/main/kotlin/uz/reaktivombor/desktop/
  Main.kt                      entry point: data dir, lock, logging, DB open, auto-backup, legacy import, window
  Sheet.kt, SheetStore.kt      LEGACY (old spreadsheet JSON) — kept only to read old data for migration
  core/
    Units.kt                   MeasureUnit, Dimension, Qty (fixed-point quantities)
    Expiry.kt                  ExpiryState + daysLeft (spec §9 thresholds)
    Text.kt                    cleanName / nameKey helpers (re-use legacy searchKey/cleanName)
    Errors.kt                  UserError (message shown to the user) + specific subclasses
    DataDir.kt                 DataDir.default() + dbFile/backupsDir/logsDir/lockFile (§2)
    SafeFiles.kt               SafeFiles.write(target) { out -> } — temp file + atomic move, Uzbek UserError if locked
    Xlsx.kt                    Xlsx.write(out) { workbook -> } — fastexcel writer re-packed as a plain zip (§7)
    Logging.kt                 AppLogging.setup(dataDir), installUncaughtHandler(show), GENERIC_ERROR (§8)
  db/
    Database.kt                connection, transactions, reopen, helpers
    Schema.kt                  migrations (PRAGMA user_version), triggers, indexes, seeds
  service/
    Services.kt                `Services` facade wiring everything for UI (one instance per app)
    Directory.kt               departments + districts
    Reagents.kt                reagents + batches (create/edit/archive/delete)
    Stock.kt                   receipts, issues (multi-line), returns, write-offs, cancel, correct, FEFO
    Queries.kt                 read models for tables, dashboard, notifications, ledgers
    Audit.kt                   audit log writer + reader
    Settings.kt                app_settings typed access (+ table column layouts)
    Backup.kt                  backup / restore / auto-backup / validation
    LegacyImport.kt            old JSON (reaktivlar-jadval.json, reaktivlar.json) → new DB
    Internal.kt                shared internal helpers (date/text checks, batch info, as-of availability, movements)
    ExcelImport.kt             batches from an .xlsx template (template + preview + import) — `Services.excelImport`
  report/
    Reports.kt                 ReportKind, ReportFilter, ReportData, ReportBuilder (12 report types)
    ExcelExport.kt             ReportData → .xlsx (fastexcel)
    PdfExport.kt               ReportData → .pdf (OpenPDF, DejaVu Sans)
  ui/
    App.kt                     AppState (services, current page, refresh tick, messages, dialogs) + root composable
    Shell.kt                   left navigation rail (13 items) + top bar (title, notifications bell, date)
    theme/Theme.kt             colors, typography, status colors
    components/                DataTable, DateField, DateRangeField, ComboField, SelectField, QtyField,
                               StatCard, Charts, StatusChip, Dialogs, FormLayout, Banner
    pages/                     one file per page (see §6)
  selftest/SelfTest.kt         `--self-test <file>` scripted end-to-end check (used by Windows CI)
desktop/src/main/resources/fonts/DejaVuSans.ttf, DejaVuSans-Bold.ttf, LICENSE-DejaVu.txt
```

The old spreadsheet UI (`App.kt`, `Grid.kt`, `SheetState.kt` in the root package and their tests) is removed
once the new UI is wired; `Sheet.kt` / `SheetStore.kt` (+ `SheetTest`, `SheetStoreTest`) stay for migration.
The shared Android domain sources (`android/.../domain`) stay on the source path and must keep compiling;
do NOT modify them (the Android app uses them).

## 2. Data location

`DataDir.default()`:
- Windows: `%APPDATA%\ReaktivControl` (Roaming profile — never inside the install folder, which is `%LOCALAPPDATA%\ReaktivControl`)
- macOS: `~/Library/Application Support/ReaktivControl`
- Linux: `${XDG_DATA_HOME:-~/.local/share}/reaktiv-control`
- Override: system property `reaktiv.dataDir` (tests, screenshots, self-test).

Contents: `reaktiv-control.db`, `backups/` (auto + pre-restore), `logs/reaktiv-control.N.log`, `.lock`.
Old data (read-only, never modified): `SheetStore.defaultFile()` = `%APPDATA%\Reaktivlar\reaktivlar-jadval.json`
and its legacy sibling `reaktivlar.json` (+ `.bak.json`).

## 3. Quantities and units (core/Units.kt)

```kotlin
enum class Dimension { VOLUME, MASS, COUNT }
enum class MeasureUnit(val code: String, val title: String, val dimension: Dimension, val factor: Long) {
    L("l", "litr", VOLUME, 1_000_000), ML("ml", "millilitr", VOLUME, 1_000),
    KG("kg", "kilogramm", MASS, 1_000_000), G("g", "gramm", MASS, 1_000),
    PIECE("dona", "dona", COUNT, 1_000);
    companion object { fun of(code: String): MeasureUnit? /* accepts aliases: litr, мл, gr, гр, шт, ta … (see legacy normalizeUnit) */ }
}
```
- Every quantity in the DB is a **Long in base units**: µl (volume), mg (mass), milli-piece (count).
  `base = enteredValue × unit.factor`. Exact integer arithmetic — no floating point anywhere in stock math.
- `Qty.parse(text, unit): Long` — accepts `1 250,5`, `1250.5`, NBSP/thin spaces; rejects empty, non-numbers,
  exponent notation, negatives, zero (for operations), more than **3 decimal places** in the entered unit,
  values above 999 999 999 of the entered unit. Throws `UserError` with an Uzbek message.
- `Qty.format(base, unit): String` → `"1 250,5"` (grouping with thin/normal space, decimal comma, up to 3 decimals,
  trailing zeros stripped; up to 6 decimals when converting e.g. 0.0005 l is never needed because display unit
  choice below). `Qty.formatWithUnit(base, unit)` → `"1 250,5 ml"`.
- Conversion only inside one dimension (`l`↔`ml`, `kg`↔`g`). Different dimensions are **never** added together.
- Group display unit (for totals of one reagent name + dimension): the largest-factor unit used by any batch in
  the group (`l` beats `ml`), e.g. 5 l + 500 ml = `5,5 l`.
- Issue/receipt/return lines may be entered in any unit of the batch's dimension (e.g. 200 ml from a litre batch).
  The row stores `entered_qty` (text as typed, normalized) and `entered_unit` for display, and `qty` (base) for math.

## 4. Expiry (core/Expiry.kt) — spec §9, exact days

`daysLeft = expiresOn − today` (ChronoUnit.DAYS). Only the expiry date is used (production / receipt dates are info).

| days left | state | colour | status text |
|---|---|---|---|
| > 90 | GREEN | green | `Yaroqli` |
| 31 … 90 | YELLOW | yellow | `1–3 oy qoldi` |
| 0 … 30 | RED | red | `1 oy yoki kam qoldi` |
| < 0 | EXPIRED | dark red / grey-red | `Muddati o‘tgan` |
| no expiry date (old data only) | UNKNOWN | grey | `Muddati kiritilmagan` |

`enum class ExpiryState(val label: String)`; `fun expiryState(expiresOn: LocalDate?, today: LocalDate): ExpiryState`;
`fun daysLeft(expiresOn: LocalDate?, today: LocalDate): Long?`. One function used everywhere (tables, dashboard,
notifications, reports, Excel/PDF colours). Expired batches are never deleted or hidden automatically and are never
"recommended"; issuing from them needs an explicit confirmation (see §5.4).

## 5. Database (SQLite) — db/Schema.kt, version 1

Connection: one `java.sql.Connection` per app, all access serialized through `Database` (ReentrantLock).
PRAGMAs on open: `foreign_keys=ON`, `journal_mode=DELETE`, `synchronous=FULL`, `busy_timeout=5000`.
Dates: TEXT `YYYY-MM-DD`; timestamps: TEXT local `YYYY-MM-DDTHH:MM:SS`. Booleans: INTEGER 0/1.

```kotlin
class Database(path: Path) : AutoCloseable {
    val path: Path
    fun <T> read(block: (Connection) -> T): T            // lock, no transaction
    fun <T> write(block: (Connection) -> T): T           // lock + BEGIN IMMEDIATE … COMMIT / ROLLBACK on any throwable;
                                                          // re-entrant: nested write() joins the outer transaction
    fun reopen(newPath: Path = path)                      // used by restore; services keep the same Database object
    override fun close()
}
// helpers (extension functions on Connection): query(sql, vararg args) { rs -> row }, queryOne, update(sql, args): Int,
// insert(sql, args): Long (last_insert_rowid()), args bind String/Long/Int/Boolean/LocalDate/null.
```

Tables (all have `id INTEGER PRIMARY KEY`):

- `app_settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)` — seeds: `app`=`reaktiv-control`, `org_name`=``,
  `actor_name`=``, `auto_backup_keep`=`14`.
- `departments(name, name_key UNIQUE, active, sort_order, created_at, updated_at)` — seeds in this order:
  Gistologiya, Biologiya, Sud-kimyo, Morfologiya.
- `districts(name, name_key UNIQUE, active, sort_order, created_at, updated_at)` — no seeds (users add them; typing a
  new name in an issue form creates it).
- `reagents(name, name_key UNIQUE, formula, min_stock INTEGER NULL ≥0 (base), min_stock_unit TEXT NULL, note,
  archived, created_at, updated_at)` — one row per reagent NAME.
- `batches(reagent_id FK, series TEXT NOT NULL DEFAULT '' (''= not given, old data), manufacturer, produced_on,
  received_on, expires_on (NULL only for old data), unit CHECK in units, department_id FK NULL, storage_location,
  responsible, note, archived, created_at, updated_at, CHECK(expires_on IS NULL OR produced_on IS NULL OR expires_on >= produced_on))`
  - `UNIQUE INDEX ux_batch_identity ON batches(reagent_id, series, IFNULL(expires_on,''), unit) WHERE series <> ''`
    → same name + series + expiry + unit is the same batch; a new receipt for it must be an extra receipt of that batch.
  - indexes: `(reagent_id, expires_on)`, `(expires_on)`, `(department_id)`.
- `receipts(batch_id FK, received_on, qty >0, entered_qty, entered_unit, received_by, storage_location, doc_no, note,
  is_initial, created_at, cancelled_at, cancel_reason, corrects_id FK receipts NULL)`
- `issues(issued_on, doc_no, issued_by, note, created_at)` — header of one issuing operation (any number of lines).
- `issue_items(issue_id FK, batch_id FK, recipient_type CHECK IN ('DEPARTMENT','DISTRICT','WRITE_OFF'),
  department_id FK NULL, district_id FK NULL, organization, receiver, qty >0, entered_qty, entered_unit,
  fefo_override, expired_override, note, created_at, cancelled_at, cancel_reason, corrects_id FK issue_items NULL,
  CHECK(recipient columns match recipient_type))`
- `returns(issue_item_id FK, returned_on, qty >0, entered_qty, entered_unit, reason, received_by, created_at,
  cancelled_at, cancel_reason)`
- `stock_movements(batch_id FK, kind CHECK IN ('RECEIPT','ISSUE','RETURN','ADJUSTMENT','REVERSAL'), qty INTEGER NOT NULL
  CHECK(qty<>0) (signed, base), moved_on, receipt_id, issue_item_id, return_id, reverses_id UNIQUE FK, note, created_at)`
  - **the only source of stock truth**: batch remainder = `SUM(qty)` of its movements. Nothing else stores a remainder.
  - partial unique indexes: one RECEIPT movement per receipt, one ISSUE movement per issue item, one RETURN per return
    → an operation can never be counted twice; `reverses_id UNIQUE` → it can never be reversed twice.
  - triggers: `BEFORE UPDATE` / `BEFORE DELETE` → `RAISE(ABORT,'append-only')` (ledger is append-only);
    `AFTER INSERT` → `RAISE(ABORT,'NEGATIVE_STOCK')` when the batch sum becomes < 0 (DB-level guarantee).
- `audit_logs(at, action, entity, entity_id, reagent_id, batch_id, qty_text, details, actor)` — append-only (triggers),
  indexes on `at`, `reagent_id`.

Cancelling an operation = set `cancelled_at/cancel_reason` on the operation row + insert a REVERSAL movement with
`reverses_id` = original movement and `qty = −original`. Correcting = cancel + new operation with `corrects_id`
in the same transaction. Old rows are never deleted. Write-off ("Hisobdan chiqarish", e.g. expired stock) = issue item
with `recipient_type='WRITE_OFF'`.

Migrations: `PRAGMA user_version`; `Schema.migrate(conn)` applies steps 1..N in one transaction each. A DB file whose
`user_version` is greater than the app knows is refused with a clear message (opened by a newer version).

## 5.4 Services API (service/*) — called from the UI only through `Services`

All writes run inside `db.write {}` (one transaction per user action), write audit rows in the same transaction,
validate first and throw `UserError(message)` (Uzbek, user-facing) for anything the user can fix. Unexpected exceptions
are logged and shown as a generic error.

```kotlin
class Services(val db: Database, val dataDir: Path, val clock: Clock = Clock.systemDefaultZone()) {
    val settings: Settings; val audit: Audit; val directory: Directory; val reagents: Reagents
    val stock: Stock; val queries: Queries; val backup: Backup; val legacy: LegacyImport; val excelImport: ExcelImport
    fun today(): LocalDate
}
```

**Directory** (`Department`/`District` = `data class Place(id, name, active, sortOrder)`):
`departments(includeInactive)`, `districts(includeInactive)`, `addDepartment(name)`, `addDistrict(name)`
(existing name by key → returns the existing one, re-activating it), `rename(kind,id,name)` (rejects a name another
row already has), `setActive(kind,id,active)`, `resolveDistrict(name)` (find or create — used by issue forms).
Name rules: trimmed, inner whitespace collapsed (`cleanName`), contains a letter, ≤ 60 chars; key = `searchKey(cleanName)`.

**Reagents**:
- `searchNames(prefix, limit)` → existing reagent names (for autocomplete).
- `createBatch(input: NewBatch, confirmDuplicate=false): Long` — find-or-create reagent by name key (updates formula /
  min stock if given), create batch + initial receipt (+ RECEIPT movement) atomically.
  `NewBatch(name, formula?, manufacturer?, series, producedOn?, receivedOn, initialQtyText, unit, expiresOn,
  departmentId?, storageLocation?, responsible?, minStockText?, minStockUnit?, note?, receivedBy?)`.
  Validation: name required ≤255; series required for new batches ≤100; receivedOn required, not in the future;
  expiresOn required; producedOn optional, not in the future, ≤ receivedOn and ≤ expiresOn; qty via `Qty.parse`;
  dates between 1990-01-01 and 2199-12-31. Same identity (name+series+expiry+unit) already exists →
  `DuplicateBatchError(existingBatchId)` (UI offers "add as extra receipt to the existing batch").
- `updateReagent(id, ReagentEdit(name, formula, minStockText, minStockUnit, note))` (rename merges are refused if the
  new name belongs to another reagent).
- `updateBatch(id, BatchEdit(series, manufacturer, producedOn, receivedOn, expiresOn, departmentId, storageLocation,
  responsible, note))` — descriptive fields only; unit and quantities change only through operations.
- `archiveReagent/unarchiveReagent`, `archiveBatch/unarchiveBatch`.
- `deleteReagent(id)` / `deleteBatch(id)` — allowed only when there is no history (no movements / no batches);
  otherwise `UserError("… tarixi bor — o‘chirib bo‘lmaydi, arxivlang")`.

**Stock**:
- `addReceipt(batchId, ReceiptInput(date, qtyText, unit, receivedBy?, storageLocation?, docNo?, note?), confirmDuplicate=false): Long`
- `issue(IssueInput(date, docNo?, issuedBy?, note?, lines: List<IssueLine>), confirm: IssueConfirm = none): Long`
  `IssueLine(batchId, qtyText, unit, recipientType, departmentId?, districtName?/districtId?, organization?, receiver?, note?)`.
  One transaction for all lines. Each line → one `issue_items` row + one ISSUE movement. Lines on the same batch are
  summed before the stock check. Insufficient stock → `InsufficientStockError(batch, available, requested)` and
  **nothing** is saved. Issue date may not be in the future nor before the batch's received_on.
  FEFO: `fefo_override=1` when another non-archived, non-expired batch of the same reagent+dimension with stock > 0
  expires earlier (warning only). Expired batch → `ExpiredBatchError` unless `confirm.allowExpired` (then
  `expired_override=1` + audit note). Duplicate guard: identical line (batch, qty, recipient, date) created in the last
  120 s → `DuplicateOperationError` unless `confirm.allowDuplicate`.
- `fefoOrder(reagentId, dimension, today): List<BatchStock>` (non-expired first by expiry asc, then expired),
  `fefoRecommendation(reagentId, dimension, today): Long?`,
  `fefoSplit(reagentId, dimension, qtyBase, today): List<Pair<batchId, qtyBase>>` (non-expired only; error if not enough).
- `returnItem(issueItemId, ReturnInput(date, qtyText, unit, reason?, receivedBy?)): Long` — total active returns ≤ issued.
- `writeOff(batchId, date, qtyText, unit, reason)` = issue with one WRITE_OFF line.
- `cancelReceipt(id, reason)`, `cancelIssueItem(id, reason)` (refused while it has active returns),
  `cancelReturn(id, reason)` — reason required; refused if it would make stock negative (friendly message).
- `correctReceipt(id, ReceiptInput, reason)`, `correctIssueItem(id, IssueLine + date, reason)` — cancel + new.
- `remainder(batchId): Long`.

**Queries** (read models; all quantities as base Long + unit; UI formats):
- `batchRows(filter: BatchFilter, today): List<BatchRow>` —
  `BatchRow(batchId, reagentId, name, formula, series, manufacturer, departmentId, departmentName, storageLocation,
  producedOn, receivedOn, expiresOn, daysLeft, state, unit, receivedQty, issuedQty (to departments+districts minus
  returns), writtenOffQty, remainder, groupTotal, groupUnit, groupReceived, responsible, note, archived,
  reagentArchived, belowMin)`. `groupTotal` = sum of remainders of ALL non-archived batches with the same reagent and
  dimension (independent of row filters), shown in `groupUnit`.
  `BatchFilter(text, series, departmentId, districtId, producedFrom/To, receivedFrom/To, expiresFrom/To,
  states: Set<ExpiryState>, remainderMin/Max (in a unit), responsible, includeArchived, includeEmpty)`.
  Default ordering: reagent name (case-insensitive, Uzbek apostrophes folded), then expiry ascending (FEFO), then series.
- `stockTotals(filter, today): List<StockTotal>` — per reagent + dimension: batches, activeBatches, received, issued,
  writtenOff, remainder, minStock, belowMin, nearestExpiry, worstState, unit.
- `receipts(OpFilter)`, `issueItems(OpFilter)` (+ returnedQty, recipient name, issue header fields, cancelled flag),
  `returns(OpFilter)`; `OpFilter(from, to, text, series, departmentId, districtId, recipientType, includeCancelled)`.
- `departmentSummary(departmentId, from?, to?, today)`: batches owned by the department (received / issued /
  remainder / expiry) + items issued TO the department (net of returns) per reagent+dimension + history.
- `districtSummary(districtId?, from?, to?)`: per district totals, per reagent+dimension, dates, batches + expiry.
- `dashboard(today)`: reagentTypes, activeBatches, expiringBatches (RED+YELLOW with stock), expiredBatches (with stock),
  belowMinReagents, byDepartment (remainder in owned batches + issued-to totals), byDistrict (issued totals),
  stateCounts, recentReceipts(10), recentIssues(10).
- `notifications(today)`: expired-with-stock, red, yellow, below-min (each with a short Uzbek text and target page).
- `ledger(batchId)` / `reagentLedger(reagentId)`: movements with running balance (per batch), operation details.
- `auditLogs(AuditFilter(from, to, action?, text?), limit)`.

**Settings**: `orgName`, `actorName`, `autoBackupKeep`, `tableLayout(id): String?`, `setTableLayout(id, json)`,
`lastAutoBackup`. **Audit** actions (enum, Uzbek label): REAGENT_CREATED, REAGENT_UPDATED, BATCH_CREATED,
BATCH_UPDATED, RECEIPT, ISSUE_DEPARTMENT, ISSUE_DISTRICT, WRITE_OFF, RETURN, CANCELLED, CORRECTED, ARCHIVED,
UNARCHIVED, DELETED, DEPARTMENT_CHANGED, DISTRICT_CHANGED, BACKUP_CREATED, BACKUP_RESTORED, LEGACY_IMPORTED,
EXCEL_IMPORTED, SETTINGS_CHANGED.

**Backup**: `create(target: Path)` (`VACUUM INTO`, then validate the copy), `autoBackupIfDue(today)` (once a day into
`backups/auto-YYYY-MM-DD.db`, keep `autoBackupKeep` newest), `list()`, `validate(path)` (opens read-only:
`integrity_check`, `app` marker, `user_version ≤ current`), `restore(path)` (validate → automatic backup of the current DB
`backups/before-restore-<timestamp>.db` → close → copy over via temp + atomic move → reopen → migrate → audit).
Never leaves the app without a working DB: on failure the previous file is put back.

**LegacyImport**: `findLegacyFile(): Path?`, `importFile(path): LegacyResult(imported, skipped, notes)`.
Auto-run on startup only when the DB has no batches and `app_settings.legacy_import` is not set. Mapping per old row:
reagent by name; batch: series '' , produced_on, received_on = old «Kelgan vaqt» if any, expires_on, unit, department
from the old «Bo‘lim»; initial receipt = max(Kelgan miqdor, Ombordagi miqdor) dated old received date / row creation
date / today; if Ombordagi < that, an ADJUSTMENT movement "Eski jadvaldagi ombordagi miqdorga moslash"; old
«Berilgan miqdor» > 0 → issue item to that department. Rows without a name or with an unknown unit are skipped and
counted. The old file is never modified. Old department names are added to `departments`.

**ExcelImport**: `template(path)`, `preview(path): List<ImportRow(rowNumber, NewBatch?, errors)>`, `import(rows)`
(as built: §5.5).

## 5.5 Core as built — exact public API and clarifications (binding for UI / report / import code)

Everything above holds; this section pins down the details the core wave had to decide. Packages:
`uz.reaktivombor.desktop.core`, `.db`, `.service`.

- **Services**: `Services(db, dataDir, clock)` has `audit, settings, directory, reagents, stock, queries, backup, legacy,
  excelImport` and `today()`; `Services.open(dataDir = DataDir.default(), clock)` opens `DataDir.dbFile(dataDir)`.
- **Errors**: `open class UserError(message, fields: Map<String,String> = emptyMap())` (+ `UserError(message, field: String?)`).
  Form validation collects all field errors at once: `fields` keys = the input property names (`name`, `series`,
  `receivedOn`, `expiresOn`, `producedOn`, `unit`, `initialQtyText`, `minStockText`, `qtyText`, `date`, `reason`, …);
  `message` = all messages joined by `\n`. `DuplicateBatchError(existingBatchId, exact: Boolean, message)`:
  `exact=true` — same name+series+expiry+unit (never creatable; offer an extra receipt); `exact=false` — same name+series
  with another expiry/unit, saved when `createBatch(input, confirmDuplicate = true)`. Series compare case-insensitively.
  `InsufficientStockError(batchId, available, requested, unit, message)`, `ExpiredBatchError(batchId, message)`,
  `DuplicateOperationError(message)`.
- **Units**: `Dimension(title)`; `MeasureUnit.of(code)`, `of(dimension)`, `largest(units)`, `main(dimension)`, `siblings`.
  `Qty.parse(text, unit, label = "Miqdor", allowZero = false, field = null)`, `format`, `formatWithUnit`,
  `formatAuto(base, dimension)` (≥ 1 l → l, else ml), `formatTotals(Map<Dimension, Long>)`, `toDecimal`, `fromDecimal`.
  `format` is exact: up to 6 decimals for l/kg (never rounds), trailing zeros stripped, grouping with a normal space.
- **Expiry**: also `daysLeftLabel(days)` ("45", "Bugun tugaydi", "12 kun o‘tgan", "—"), `ExpiryState.severity`
  (EXPIRED 4 > RED 3 > YELLOW 2 > UNKNOWN 1 > GREEN 0), constants `GREEN_AFTER_DAYS = 90`, `RED_UNTIL_DAYS = 30`.
- **Text**: `nameKey(raw)`, `matchesQuery(query, vararg values)`, `String.charCount()`, `LocalDate.display()` (dd.MM.yyyy).
- **Database**: also `isOpen`, `offline { path -> }` (closed connection while the file is replaced; reopens after),
  `Database.connect(path, readOnly)`; helpers `exec`, `query`, `queryOne`, `queryLong`, `update`, `insert`, ResultSet
  `str/strOrNull/long/longOrNull/int/bool/date/dateTime/unit/unitOrNull`. A nested `write` is a SAVEPOINT (an inner
  failure rolls back only the inner part). The DB is migrated on every open; `user_version > Schema.VERSION` → UserError.
- **Schema additions**: operation rows (`receipts`, `issue_items`, `returns`) may only be updated once, to set
  `cancelled_at/cancel_reason` (trigger); `issues`, `issue_items`, `returns` can never be deleted; a REVERSAL must be the
  exact negative of a non-reversal movement of the same batch (trigger `BAD_REVERSAL`). RETURN movements carry both
  `return_id` and `issue_item_id`; a REVERSAL carries the ids of the movement it reverses. Internal table
  `ledger_unlock(batch_id)` is the only way to delete movements/receipts (used solely by `deleteBatch`, below).
- **Dates of reversals**: a REVERSAL is dated with the ORIGINAL operation's date (`created_at` = when cancelled), so
  period reports and running balances stay consistent after a cancel.
- **As-of availability**: an issue / cancel-of-receipt / cancel-of-return dated D is allowed only if the balance stays
  ≥ 0 on D and on every later date (`Stock.available(batchId, date)`; for today it equals the remainder). A backdated
  issue therefore cannot make an earlier ledger balance negative.
- **Directory**: `PlaceKind { DEPARTMENT, DISTRICT }`, `list(kind, includeInactive)`, `get(kind, id)`, `add(kind, name)`.
  Names are stored as typed (cleaned), not re-capitalised. Inactive departments/districts are refused in new issue lines;
  new names get `sort_order = max + 1`. Seeds: Gistologiya, Biologiya, Sud-kimyo, Morfologiya.
- **Reagents**: data classes `Reagent`, `Batch`, `NewBatch`, `ReagentEdit`, `BatchEdit` (all nullable dates/units so
  "missing" is a validation message); also `get(id)`, `batch(id)`, `list(includeArchived)`, `batchesOf(reagentId)`.
  `createBatch` un-archives an archived reagent of the same name. Archived batch / reagent: no receipts, no issues.
  **Delete rule ("no history")**: a batch can be deleted only when its only operation is its own initial receipt
  (no other receipt, issue, return, adjustment or cancellation) — i.e. a just-entered mistake such as a wrong unit;
  its initial receipt + movement are removed with it (via `ledger_unlock`) and a DELETED audit row keeps the details.
  A reagent can be deleted only when it has no batches. Everything else → "… tarixi bor — o‘chirib bo‘lmaydi, arxivlang."
- **Stock**: `RecipientType(label)`; `IssueConfirm.none`; `IssueLine(batchId, qtyText, unit, recipientType,
  departmentId?, districtId?, districtName?, organization?, receiver?, note?)` — for WRITE_OFF `note` is the (required)
  reason; `writeOff(batchId, date, qtyText, unit, reason, confirm = IssueConfirm.none)`;
  `correctIssueItem(id, line, date, reason, confirm = IssueConfirm.none): Long` (new item id, new header with the old
  doc/issuer/note); `correctReceipt(...)` returns the new receipt id; cancels return Unit; `available(batchId, date)`.
  Expired = `expires_on < issue date`; write-offs never need the expired confirmation and never set `fefo_override`.
  FEFO override ignores stock that the same operation already takes from the earlier batch. `fefoOrder` returns only
  non-archived batches with stock: non-expired by expiry, then no-expiry (old data), then expired. Duplicate guard
  (120 s, non-cancelled rows): issue line = batch + base qty + recipient + date; receipt = batch + base qty + date.
- **Queries**: models `BatchRow` (+ `dimension`), `BatchFilter` (+ `reagentId`; `includeEmpty` defaults to **true**;
  `remainderMin/Max` are base units applied only to rows of `remainderUnit`'s dimension), `StockTotal`,
  `OpFilter` (+ `batchId`, `reagentId`), `ReceiptRow`, `IssueItemRow` (+ `netQty`, `recipientName` = department /
  district name or "Hisobdan chiqarish"), `ReturnRow`, `ReagentQtyTotal(+net)`, `DepartmentSummary(department,
  ownedBatches, issuedTo, history)`, `DistrictSummary(districts: List<DistrictTotal>, perReagent:
  List<DistrictReagentTotal>, items)`, `Dashboard`, `DepartmentStat`, `DistrictStat` (mixed dimensions are kept as
  `Map<Dimension, Long>`, never summed), `Notification(kind, text, target: NotificationTarget{EXPIRY, STOCK}, reagentId,
  batchId?, daysLeft?, counted)`, `notificationCount(today)` (bell = expired + red + below-min), `LedgerRow`,
  `MovementKind(label)`. `reagentTypes` = reagents with at least one non-archived batch.
  `fullLedger(): List<LedgerRow>` — every batch's movements (date, id order) with per-batch running balance (reports).
- **Settings**: `var orgName / actorName / autoBackupKeep (1..365) / lastAutoBackup`, `get(key)`, `tableLayout(id)`,
  `setTableLayout(id, json?)` (null = reset), `resetTableLayouts()`. Audit `actor` = `actor_name` setting.
- **Backup**: `dir`, `create(target): Path`, `autoBackupIfDue(today): Path?` (null when today's already exists),
  `list(): List<BackupFile(path, kind: BackupKind{AUTO, PRE_RESTORE, MANUAL}, size, modifiedAt)>`,
  `validate(path): BackupCheck(userVersion, reagents, batches, movements)` (throws UserError),
  `restore(path): RestoreResult(preRestoreBackup, check)`; pre-restore file names never overwrite an existing file.
- **LegacyImport**: `LegacyResult(imported, skipped, notes, file)`, `findLegacyFile()`, `importFile(path)`,
  `autoImportIfNeeded(): LegacyResult?` (call on startup), `isDone()`. Location override `-Dreaktiv.legacyFile=…`.
  Idempotency: SHA-256 of the file text in `app_settings.legacy_import_hashes`; `legacy_import` = last import info.
  Old rows: missing expiry stays NULL (UNKNOWN), an issued amount without a department becomes an ADJUSTMENT, issued >
  stock is capped; all legacy operations are dated with the row's receipt date.
- **Logging**: `AppLogging.setup(dataDir)` installs a UTF-8 `FileHandler` (`logs/reaktiv-control.%g.log`, 1 MB × 5)
  on the root logger; services log only operation ids at INFO.
- **ExcelImport** (`Services.excelImport`, `ExcelImport(db, audit, directory, reagents, clock)`):
  - `template(path): Path` — sheet `ExcelImport.SHEET` = «Reaktivlar» with exactly `ExcelImport.HEADERS` in row 1
    (= `ExcelImport.Column` entries: Reaktiv nomi*, Kimyoviy formula, Ishlab chiqaruvchi, Seriya raqami*, Ishlab chiqarilgan
    sana, Kelgan sana*, Yaroqlilik muddati*, Kelgan miqdor*, O‘lchov birligi*, Bo‘lim, Saqlash joyi, Mas’ul xodim, Qabul
    qilgan xodim, Minimal qoldiq, Minimal qoldiq birligi, Izoh; * = required, navy header) + one example row (series
    `EXAMPLE_SERIES` = `NAMUNA-001`, never imported) + sheet `GUIDE_SHEET` = «Yo‘riqnoma» (rules + one line per column).
  - `preview(path): List<ImportRow>` — `ImportRow(rowNumber /* Excel row */, input: NewBatch? /* null = example row */,
    errors: List<String>, warnings: List<String> = [], departmentName: String? /* new department, created on import */)`,
    `valid = input != null && errors.isEmpty()`. Header row = first of the first 20 rows that has «Reaktiv nomi»; headers
    match ignoring case, apostrophe variants, spaces, `*`, `:` and `(…)`; a few aliases (Nomi, Seriya, Birlik, Miqdor …);
    unknown columns ignored; first sheet with the headers wins. Missing required column / unreadable or non-xlsx file →
    `UserError` naming them. Empty rows skipped. Dates: numeric cells = Excel serial dates (1900/1904), text via
    `DateText.parse` (dd.MM.yyyy etc.); numbers: numeric cells (Excel float noise rounded at 9 decimals) or text
    (`1 250,5`); unit via `MeasureUnit.of` (aliases); departments by name key — existing active → `departmentId`, inactive →
    error, new valid name → `departmentName` + warning. Validation = the real `Reagents.createBatch` run inside ONE write
    transaction that is always rolled back (nothing persists, no audit), so messages are identical to the form and
    duplicates are found: exact duplicate (name+series+expiry+unit) of a DB batch or of an earlier row in the same file →
    error; same name+series with another expiry/unit → warning (imported with `confirmDuplicate = true`).
  - `import(rows, source: Path? = null): ImportResult(created, skipped)` — all `valid` rows in one transaction (new
    departments created there); any failure → `UserError("N-qator: … Hech narsa import qilinmadi …")` and nothing is saved;
    no valid row → `UserError`. Audit `EXCEL_IMPORTED` ("Fayl: …; qo‘shildi: N ta partiya; o‘tkazib yuborildi: M ta qator").

## 6. UI (Compose Desktop, Material3)

Window: title `Reaktiv Control — Forensic Laboratory Management System`, min size 1280×720, default 1366×768 or
maximized on smaller screens; must be fully usable at **1366×768** (no clipped buttons; tables scroll).

Design system: navy `#0B2545` (rail, headers), primary blue `#1E5AA8`, background light grey `#F2F4F7`, surfaces white,
text `#1B2430`, muted `#5B6575`; status colours (row background / chip): GREEN `#E6F4EA`/`#1E7B34`, YELLOW
`#FFF4CC`/`#8A6100`, RED `#FDE2E1`/`#B42318`, EXPIRED `#F4D3D3`/`#7A1212` (+ bold "Muddati o‘tgan"), UNKNOWN grey.
Status is always shown as colour **and** text. Font sizes 13–14 body, 20–22 page titles. Uzbek buttons, consistent.

Shell: left navigation rail (navy, icon + label, 13 items in this order — each opens a real, working page):
1 Bosh sahifa (Dashboard) · 2 Barcha reaktivlar · 3 Reaktiv qo‘shish · 4 Kirim operatsiyalari ·
5 Sarf va berish · 6 Bo‘limlar bo‘yicha hisob · 7 Tumanlarga berilgan reaktivlar · 8 Yaroqlilik muddati nazorati ·
9 Umumiy zaxiralar · 10 Hisobotlar · 11 Operatsiyalar tarixi · 12 Ma’lumotlarni zaxiralash · 13 Sozlamalar.
Top bar: app name + subtitle, today's date, notifications bell with count badge (expired + red + below-min) opening a
list; clicking an item navigates. Global snackbar/banner for results and errors. `AppState.refresh()` bumps a tick
after every successful write so every page re-queries.

Shared components (ui/components): `DataTable` (column specs with id/title/width/alignment/sortable/cell renderer;
row background lambda; group rows with expand/collapse; header click sorting; drag-to-resize; column menu: show/hide,
move left/right, "Standart holatga qaytarish"; horizontal + vertical scroll; layout persisted through a callback as
JSON → `Settings.setTableLayout`), `DateField` (text `dd.MM.yyyy` + calendar popup with Uzbek month names, keyboard
input, validation message), `DateRangeField`, `ComboField` (autocomplete + free text, optional "+ yangi" item),
`SelectField`, `QtyField` (number + unit selector limited to a dimension), `StatCard`, `BarChart`, `DonutChart`,
`StatusChip`, `ConfirmDialog`, `ReasonDialog`, `ErrorDialog`, `FilterBar` (with "Filtrlarni tozalash").

Pages (ui/pages):
- **DashboardPage**: stat cards (reagent types, active batches, expiring, expired, below min), donut by expiry state,
  bar chart by department, bar chart by district, recent receipts / issues lists, warning banner if expired stock exists.
- **ReagentsPage** (Barcha reaktivlar): grouped table, columns in this order: №, Reaktiv nomi, Seriya raqami,
  Bo‘lim / saqlash joyi, Ishlab chiqarilgan sana, Kelgan sana, Yaroqlilik muddati, Qolgan kunlar, Holati,
  Kelgan miqdor, Jami berilgan miqdor, Joriy qoldiq, Umumiy qoldiq (shu nom), O‘lchov birligi, Mas’ul xodim, Amallar.
  Same names always adjacent; group header shows name, batch count, total remainder per dimension and per-state counts
  (a red batch inside a green group stays visible); expand/collapse per group + "Hammasini ochish/yopish"; whole row
  tinted by its own state; filters for every §12 parameter; row actions: Tahrirlash, Kirim, Berish, Tarix, Arxivlash /
  O‘chirish (with the history rule).
- **AddReagentPage**: the reagent card form (all §3 fields; name autocomplete from existing names), validation messages
  next to fields, Save → success banner + "yana qo‘shish"; "Exceldan import" section (template download, preview,
  import).
- **ReceiptsPage**: new receipt (new batch → link to AddReagent, or extra receipt for an existing batch with batch
  picker), receipts table with filters, Bekor qilish / Tuzatish actions (reason required).
- **IssuesPage** (Sarf va berish): header (date, hujjat/talabnoma raqami, beruvchi, izoh) + multiple lines (reagent →
  batch picker showing remainder/expiry/state with FEFO recommendation preselected, qty + unit, recipient type
  Bo‘lim/Tuman/Hisobdan chiqarish, recipient, tashkilot, qabul qiluvchi, izoh), "+ Qator", "FEFO bo‘yicha taqsimlash",
  live remaining-stock check per batch, FEFO / expired warnings, Save (one transaction). Below: issue history table with
  filters and Qaytarish / Bekor qilish / Tuzatish actions.
- **DepartmentsPage**: department list (add, rename, faol/nofaol), selected department: summary cards, owned batches
  table (received / issued / remainder / expiry), issued-to-department table, history, export buttons.
- **DistrictsPage**: district list (add, rename, faol/nofaol), filters (district, period, reagent), totals per district
  and per reagent, issues table (date, document, batch, expiry, qty, organization, receiver), Qaytarish action, export.
- **ExpiryPage**: tabs/filter chips Muddati o‘tgan / Qizil / Sariq / Yashil / Hammasi with counts, table sorted by days
  left, expired warning banner, actions Hisobdan chiqarish / Arxivlash.
- **StockPage** (Umumiy zaxiralar): per reagent + unit dimension totals table, below-min filter, expand to batches.
- **ReportsPage**: report type list (12), filter panel (period with calendar, name, series, department, district),
  preview table, "Excelga saqlash", "PDFga saqlash" (file dialog), opens the folder after saving.
- **HistoryPage**: audit log table (period, action type, search) + per-reagent / per-batch ledger with running balance.
- **BackupPage**: create backup (save dialog), restore (open dialog → confirm → auto-backup → restore → reload),
  automatic backups list (restore button), data/log folder paths with "Papkani ochish", import old JSON.
- **SettingsPage**: organisation name (report headers), default staff name, auto-backup count, expiry thresholds shown
  read-only (90 / 30 / 0 days), reset all table layouts, about (version, data folder, DB file size).

## 7. Reports (report/*) — as built

`enum class ReportKind(title, periodLabel: String?, usesSeries, usesDepartment, usesDistrict, needsName)` — 12 kinds,
titles exactly: ALL_REAGENTS «Barcha reaktivlar ro‘yxati», BATCHES «Reaktivlarning partiyalar bo‘yicha ro‘yxati»,
GROUP_TOTALS «Bir xil nomdagi reaktivlarning umumiy qoldig‘i», BY_DEPARTMENT «Bo‘limlar bo‘yicha reaktivlar qoldig‘i»,
DISTRICTS «Tumanlarga berilgan reaktivlar hisoboti», RECEIPTS «Kirim operatsiyalari», ISSUES «Sarf operatsiyalari»,
EXPIRING «Yaroqlilik muddati yaqinlashgan reaktivlar», EXPIRED «Muddati o‘tgan reaktivlar», BELOW_MIN «Minimal qoldiqdan
kamaygan reaktivlar», MOVEMENT «Davr uchun reaktivlar harakati», HISTORY «Reaktivning to‘liq operatsiyalar tarixi».
The flags tell the UI which filter fields to show; a filter a kind does not use is ignored (and not listed).

```kotlin
data class ReportFilter(from: LocalDate? = null, to: LocalDate? = null, name: String? = null, series: String? = null,
                        departmentId: Long? = null, districtId: Long? = null)
enum class ReportColumnType { TEXT, DATE, QTY, INT, STATUS }   // cell value: String / LocalDate / BigDecimal / Long / ExpiryState
data class ReportColumn(title: String, type: ReportColumnType = TEXT, width: Int = 12)   // width = Excel chars, PDF weight
data class ReportRow(cells: List<Any?>, state: ExpiryState? = null, summary: Boolean = false) // state → row tint; summary → bold grey
data class ReportData(kind, title, orgName, generatedAt: LocalDateTime, filterLines: List<String>, columns, rows,
                      footerLines: List<String>) { fun text(row, column): String; val generatedLine /* "Yaratilgan: dd.MM.yyyy HH:mm" */ }
class ReportBuilder(services: Services) { fun build(kind, filter = ReportFilter(), today = services.today()): ReportData }
object ReportFormat { cell(type, value), decimal(BigDecimal) /* "1 250,5" */, dateTime(LocalDateTime) }
object ReportColors { NAVY …; tint(state) /* row */; fill(state) /* status cell */; onFill(state) }
object ReportFiles { suggestedFileName(data, "xlsx") /* "Muddati o‘tgan reaktivlar 2026-10-29.xlsx" */; writeAtomically(path) { out -> } }
object ExcelExport { write(data, path): Path; write(data, out); sheetName(title); SHEET_NAME_MAX = 31 }
object PdfExport { write(data, path): Path; write(data, out) }
```

Rules (all kinds): quantities are `BigDecimal` in the row's own unit (`Qty.toDecimal`, never exponent notation) with the
unit in its own «Birlik» column — batch rows use the batch unit, operation rows (receipts, issues, districts) the entered
unit; totals are only per dimension in footer lines (`Qty.formatTotals`, e.g. "Jami qoldiq: 16,4 l · 750 g"), never a mixed
number. Name filter = case/apostrophe-insensitive substring of the reagent name; series likewise. Filter lines (Uzbek), in
this order: "<periodLabel>: 01.10.2026 — 31.10.2026" (or "… dan" / "… gacha"), "Reaktiv: …", "Seriya: …", "Bo‘lim: …",
"Tuman: …"; "Filtr: yo‘q" when none applies. `from > to` → `UserError` (field `from`); unknown department/district id →
`UserError`. Row `state` (tint) is set only for batch/stock rows; operation rows have no tint (PDF zebra instead).

| kind | period applies to (`periodLabel`) | rows |
|---|---|---|
| ALL_REAGENTS | received date («Kelgan sana») | every non-archived batch, ReagentsPage column order; group total as text "14 l" |
| BATCHES | received date | batches per reagent+dimension + a `summary` row "Jami: <name> (N ta partiya)" in the group's largest unit |
| GROUP_TOTALS | — (current state) | per reagent+dimension: batches, with stock, received, issued, written off, remainder, min stock, nearest expiry, worst state |
| BY_DEPARTMENT | issued-to items («Davr») | per department × reagent+dimension: owned batches' remainder + issued to it (issued/returned/net) |
| DISTRICTS | issue date | every district issue (entered unit): issued, returned, net, doc, organisation, receiver; per-district footer |
| RECEIPTS | receipt date | active receipts, type Boshlang‘ich / Qo‘shimcha / Tuzatilgan; department = batch's |
| ISSUES | issue date | every active issue line incl. write-offs; department/district = recipient |
| EXPIRING | expiry date | RED + YELLOW batches with stock, by expiry |
| EXPIRED | expiry date | EXPIRED batches with stock, «O‘tgan kunlar» |
| BELOW_MIN | — | reagents below min stock: remainder, min, shortfall (in the group unit) |
| MOVEMENT | ledger date («Davr») | per batch: opening at `from`, receipts, issued, returned, written off, [Moslash only if any], closing at `to`; uses `Queries.fullLedger()`; reversals count under their original operation with the original date |
| HISTORY | ledger date | needs a name (`UserError` field `name` if blank/unknown/ambiguous; exact name key wins, a single partial match is accepted); every movement (incl. cancelled + reversal rows) with per-batch running balance, entry time, "Keyin bekor qilingan"; with `from`: one `summary` row "Davr boshidagi qoldiq" per batch |

Excel (`ExcelExport`, fastexcel through `core/Xlsx`): row 1 title (bold 14, navy), organisation (if set), "Yaratilgan: …",
filter lines, blank row, header row (bold white on navy #0B2545, wrapped, thin borders, height from the longest title),
data rows (thin borders; DATE = real date cell `dd.mm.yyyy`; QTY = number with exact decimals `#,##0` / `#,##0.0…`; INT
`#,##0`; STATUS = label on the saturated state colour, bold; whole row lightly tinted by `row.state`; `summary` rows bold
grey), "Ma’lumot topilmadi." when empty, footer lines (bold). Column widths from the spec, header frozen, autofilter over
header + data, A4 landscape, fit to one page wide, header row repeated, page footer "Reaktiv Control · Sahifa &P / &N".
Sheet name = title without `[]:*?/\`, edge `'`, max 31 chars. fastexcel's own zip uses Zip64-style data descriptors that
LibreOffice 24.2 refuses ("source file could not be loaded"); `Xlsx.write` re-packs the workbook as a plain zip (verified:
LibreOffice converts it, `java.util.zip.ZipInputStream` reads it). Files are written via a temp file + atomic move.

PDF (`PdfExport`, OpenPDF 2.0.5): A4 landscape, DejaVu Sans / Sans Bold from `resources/fonts` embedded as Identity-H
(o‘ g‘ ’ ʻ render and extract), title, organisation, generation time, filter lines, `PdfPTable` with `headerRows = 1`
(header repeated on every page), column widths = spec weights widened so no header/cell word is split, coloured status
cells, tinted rows (others zebra), font 8.5–7 pt by column count, footer lines, page footer "Reaktiv Control · Sahifa N / M".

### 7.1 Self-test (`selftest/SelfTest.kt`, used by CI through `--self-test <file>`; wiring in Main.kt is the lead's)

`fun runSelfTest(output: Path): Int` — sets `java.awt.headless=true` first, runs in a daemon thread with a 50 s limit (never
hangs), uses a fresh `Files.createTempDirectory("reaktiv-selftest-")` data dir (never the user's data, never the legacy
auto-import), writes UTF-8 lines as it goes: `# …` environment lines (OS, Java, SQLite version, data dir), then one line
per step `OK  <step>` / `FAIL <step>: <message>`, finally `# Natija: N/N OK, x s`. Exit code: 0 all OK, 1 a step failed,
2 timeout/crash, 3 output file not writable. Temp dir deleted on success, kept on failure. Steps (25): DB open + schema +
seeds; Etil spirti 19 l / 14 l / RED-YELLOW-GREEN; one issue to 4 departments 200/300/150/100 ml; two district issues on
different dates; insufficient stock rolls back the whole multi-line issue; return; cancel + correct (reversals, audit);
expiry thresholds 91/90/31/30/0/−1/null; below-min + expired notifications; each of the 12 kinds → .xlsx + .pdf, re-opened
(fastexcel-reader: title + header row; PdfReader: ≥ 1 page, title, "Yaratilgan:", o‘/g‘); Excel import round-trip from
`template()`; backup create + validate + restore; legacy JSON written with `SheetStore.toJson` imported; close + reopen keeps
data (+ `integrity_check`). Linux run: 25/25 in ≈ 1.6 s.

## 8. Logging, safety

- `java.util.logging` → `logs/reaktiv-control.%g.log` (1 MB × 5), uncaught exception handler logs and shows an Uzbek
  error dialog. Never log reagent data at INFO level beyond operation ids.
- Single-instance lock file in the data dir.
- Buttons that write are disabled while a write runs (no double submit) + the service duplicate guard.

## 9. Tests (spec §18) — `desktop/src/test/kotlin/...`

JUnit 4 (existing). Each spec case is at least one named test; tests use a temp data dir and a fixed `Clock`.
Report: `./gradlew test` + generated `desktop/TEST-RESULTS.md` (every test with pass/fail; nothing claimed that did
not run). Windows: CI runs `./gradlew test` on `windows-latest` too and launches the packaged app with `--self-test`.

## 10. Packaging

`packageName = "ReaktivControl"`, `packageVersion = 2.0.<run_number>` (CI) / `2.0.0` local, same `upgradeUuid` (MSI
upgrades the old "Reaktivlar" install), `modules(...)` must include every JDK module the libraries need (`java.sql`,
`java.logging`, `java.xml`, `java.naming` if needed — verify with `suggestRuntimeModules`). Verified (core wave,
`suggestRuntimeModules` + `jdeps` on every library jar): `java.instrument, jdk.unsupported, java.sql, java.logging,
java.xml, java.desktop`; `java.naming` is not needed. Deb: package
`reaktivcontrol`, `Replaces/Conflicts: reaktivlar`.
