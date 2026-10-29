package uz.reaktivombor.desktop.db

import uz.reaktivombor.desktop.core.UserError
import java.sql.Connection

/**
 * Baza tuzilishi. Versiya `PRAGMA user_version` da; har bir qadam alohida tranzaksiyada.
 * Dasturdan yangiroq versiyadagi baza ochilmaydi (eski dastur yangi ma’lumotni buzmasin).
 *
 * Qoldiq hech qayerda saqlanmaydi: partiya qoldig‘i = stock_movements.qty yig‘indisi. Harakatlar jurnali faqat
 * qo‘shiladi (UPDATE/DELETE trigger bilan taqiqlangan), har bir operatsiya bitta harakat bilan bog‘langan
 * (qisman unique indekslar) — operatsiya ikki marta hisoblanmaydi, bekor qilish ham bir marta.
 */
object Schema {
    const val VERSION = 2

    /** app_settings.app — bu fayl Reaktiv Control bazasi ekanini bildiradi (zaxirani tekshirishda). */
    const val APP_MARKER = "reaktiv-control"

    private const val UNITS = "('l','ml','kg','g','dona')"

    fun userVersion(conn: Connection): Int = conn.queryOne("PRAGMA user_version") { it.getInt(1) } ?: 0

    fun migrate(conn: Connection) {
        val current = userVersion(conn)
        if (current > VERSION) {
            throw UserError(
                "Ma’lumotlar bazasi dasturning yangiroq versiyasida yaratilgan (versiya $current). " +
                    "Dasturni yangilang — bu versiya uni ochmaydi.",
            )
        }
        for (version in current + 1..VERSION) {
            conn.exec("BEGIN IMMEDIATE")
            try {
                steps.getValue(version).forEach(conn::exec)
                conn.exec("PRAGMA user_version = $version")
                conn.exec("COMMIT")
            } catch (t: Throwable) {
                runCatching { conn.exec("ROLLBACK") }
                throw t
            }
        }
    }

    private const val NOW = "strftime('%Y-%m-%dT%H:%M:%S','now','localtime')"

    private val v1 = listOf(
        """
        CREATE TABLE app_settings(
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE departments(
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL CHECK(length(trim(name)) > 0),
            name_key TEXT NOT NULL UNIQUE,
            active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)),
            sort_order INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE districts(
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL CHECK(length(trim(name)) > 0),
            name_key TEXT NOT NULL UNIQUE,
            active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)),
            sort_order INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE reagents(
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL CHECK(length(trim(name)) > 0),
            name_key TEXT NOT NULL UNIQUE,
            formula TEXT NOT NULL DEFAULT '',
            min_stock INTEGER NULL CHECK(min_stock IS NULL OR min_stock >= 0),
            min_stock_unit TEXT NULL CHECK(min_stock_unit IS NULL OR min_stock_unit IN $UNITS),
            note TEXT NOT NULL DEFAULT '',
            archived INTEGER NOT NULL DEFAULT 0 CHECK(archived IN (0,1)),
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            CHECK((min_stock IS NULL) = (min_stock_unit IS NULL))
        )
        """,
        """
        CREATE TABLE batches(
            id INTEGER PRIMARY KEY,
            reagent_id INTEGER NOT NULL REFERENCES reagents(id),
            series TEXT NOT NULL DEFAULT '',
            manufacturer TEXT NOT NULL DEFAULT '',
            produced_on TEXT NULL,
            received_on TEXT NULL,
            expires_on TEXT NULL,
            unit TEXT NOT NULL CHECK(unit IN $UNITS),
            department_id INTEGER NULL REFERENCES departments(id),
            storage_location TEXT NOT NULL DEFAULT '',
            responsible TEXT NOT NULL DEFAULT '',
            note TEXT NOT NULL DEFAULT '',
            archived INTEGER NOT NULL DEFAULT 0 CHECK(archived IN (0,1)),
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            CHECK(expires_on IS NULL OR produced_on IS NULL OR expires_on >= produced_on)
        )
        """,
        // Nom + seriya + muddat + birlik bir xil — bitta partiya (yangi kelgani shu partiyaga qo‘shimcha kirim).
        "CREATE UNIQUE INDEX ux_batch_identity ON batches(reagent_id, series, IFNULL(expires_on,''), unit) WHERE series <> ''",
        "CREATE INDEX ix_batches_reagent_expiry ON batches(reagent_id, expires_on)",
        "CREATE INDEX ix_batches_expiry ON batches(expires_on)",
        "CREATE INDEX ix_batches_department ON batches(department_id)",
        """
        CREATE TABLE receipts(
            id INTEGER PRIMARY KEY,
            batch_id INTEGER NOT NULL REFERENCES batches(id),
            received_on TEXT NOT NULL,
            qty INTEGER NOT NULL CHECK(qty > 0),
            entered_qty TEXT NOT NULL,
            entered_unit TEXT NOT NULL CHECK(entered_unit IN $UNITS),
            received_by TEXT NOT NULL DEFAULT '',
            storage_location TEXT NOT NULL DEFAULT '',
            doc_no TEXT NOT NULL DEFAULT '',
            note TEXT NOT NULL DEFAULT '',
            is_initial INTEGER NOT NULL DEFAULT 0 CHECK(is_initial IN (0,1)),
            created_at TEXT NOT NULL,
            cancelled_at TEXT NULL,
            cancel_reason TEXT NULL,
            corrects_id INTEGER NULL REFERENCES receipts(id),
            CHECK((cancelled_at IS NULL) = (cancel_reason IS NULL))
        )
        """,
        "CREATE INDEX ix_receipts_batch ON receipts(batch_id)",
        "CREATE INDEX ix_receipts_date ON receipts(received_on)",
        """
        CREATE TABLE issues(
            id INTEGER PRIMARY KEY,
            issued_on TEXT NOT NULL,
            doc_no TEXT NOT NULL DEFAULT '',
            issued_by TEXT NOT NULL DEFAULT '',
            note TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL
        )
        """,
        "CREATE INDEX ix_issues_date ON issues(issued_on)",
        """
        CREATE TABLE issue_items(
            id INTEGER PRIMARY KEY,
            issue_id INTEGER NOT NULL REFERENCES issues(id),
            batch_id INTEGER NOT NULL REFERENCES batches(id),
            recipient_type TEXT NOT NULL CHECK(recipient_type IN ('DEPARTMENT','DISTRICT','WRITE_OFF')),
            department_id INTEGER NULL REFERENCES departments(id),
            district_id INTEGER NULL REFERENCES districts(id),
            organization TEXT NOT NULL DEFAULT '',
            receiver TEXT NOT NULL DEFAULT '',
            qty INTEGER NOT NULL CHECK(qty > 0),
            entered_qty TEXT NOT NULL,
            entered_unit TEXT NOT NULL CHECK(entered_unit IN $UNITS),
            fefo_override INTEGER NOT NULL DEFAULT 0 CHECK(fefo_override IN (0,1)),
            expired_override INTEGER NOT NULL DEFAULT 0 CHECK(expired_override IN (0,1)),
            note TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL,
            cancelled_at TEXT NULL,
            cancel_reason TEXT NULL,
            corrects_id INTEGER NULL REFERENCES issue_items(id),
            CHECK((cancelled_at IS NULL) = (cancel_reason IS NULL)),
            CHECK(
                (recipient_type = 'DEPARTMENT' AND department_id IS NOT NULL AND district_id IS NULL) OR
                (recipient_type = 'DISTRICT' AND district_id IS NOT NULL AND department_id IS NULL) OR
                (recipient_type = 'WRITE_OFF' AND department_id IS NULL AND district_id IS NULL)
            )
        )
        """,
        "CREATE INDEX ix_issue_items_issue ON issue_items(issue_id)",
        "CREATE INDEX ix_issue_items_batch ON issue_items(batch_id)",
        "CREATE INDEX ix_issue_items_department ON issue_items(department_id)",
        "CREATE INDEX ix_issue_items_district ON issue_items(district_id)",
        """
        CREATE TABLE returns(
            id INTEGER PRIMARY KEY,
            issue_item_id INTEGER NOT NULL REFERENCES issue_items(id),
            returned_on TEXT NOT NULL,
            qty INTEGER NOT NULL CHECK(qty > 0),
            entered_qty TEXT NOT NULL,
            entered_unit TEXT NOT NULL CHECK(entered_unit IN $UNITS),
            reason TEXT NOT NULL DEFAULT '',
            received_by TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL,
            cancelled_at TEXT NULL,
            cancel_reason TEXT NULL,
            CHECK((cancelled_at IS NULL) = (cancel_reason IS NULL))
        )
        """,
        "CREATE INDEX ix_returns_item ON returns(issue_item_id)",
        // Qoldiqning yagona manbai. Belgisi bilan: kirim/qaytarish +, berish −. Bekor qilish — teskari (REVERSAL) yozuv.
        """
        CREATE TABLE stock_movements(
            id INTEGER PRIMARY KEY,
            batch_id INTEGER NOT NULL REFERENCES batches(id),
            kind TEXT NOT NULL CHECK(kind IN ('RECEIPT','ISSUE','RETURN','ADJUSTMENT','REVERSAL')),
            qty INTEGER NOT NULL CHECK(qty <> 0),
            moved_on TEXT NOT NULL,
            receipt_id INTEGER NULL REFERENCES receipts(id),
            issue_item_id INTEGER NULL REFERENCES issue_items(id),
            return_id INTEGER NULL REFERENCES returns(id),
            reverses_id INTEGER NULL UNIQUE REFERENCES stock_movements(id),
            note TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL,
            CHECK(kind <> 'RECEIPT' OR (receipt_id IS NOT NULL AND issue_item_id IS NULL AND return_id IS NULL AND qty > 0)),
            CHECK(kind <> 'ISSUE' OR (issue_item_id IS NOT NULL AND receipt_id IS NULL AND return_id IS NULL AND qty < 0)),
            CHECK(kind <> 'RETURN' OR (return_id IS NOT NULL AND issue_item_id IS NOT NULL AND receipt_id IS NULL AND qty > 0)),
            CHECK(kind <> 'ADJUSTMENT' OR (receipt_id IS NULL AND issue_item_id IS NULL AND return_id IS NULL)),
            CHECK((kind = 'REVERSAL') = (reverses_id IS NOT NULL)),
            CHECK(receipt_id IS NULL OR issue_item_id IS NULL)
        )
        """,
        "CREATE UNIQUE INDEX ux_movement_receipt ON stock_movements(receipt_id) WHERE kind = 'RECEIPT'",
        "CREATE UNIQUE INDEX ux_movement_issue ON stock_movements(issue_item_id) WHERE kind = 'ISSUE'",
        "CREATE UNIQUE INDEX ux_movement_return ON stock_movements(return_id) WHERE kind = 'RETURN'",
        "CREATE INDEX ix_movements_batch ON stock_movements(batch_id, moved_on)",
        """
        CREATE TABLE audit_logs(
            id INTEGER PRIMARY KEY,
            at TEXT NOT NULL,
            action TEXT NOT NULL,
            entity TEXT NOT NULL DEFAULT '',
            entity_id INTEGER NULL,
            reagent_id INTEGER NULL,
            batch_id INTEGER NULL,
            qty_text TEXT NOT NULL DEFAULT '',
            details TEXT NOT NULL DEFAULT '',
            actor TEXT NOT NULL DEFAULT ''
        )
        """,
        "CREATE INDEX ix_audit_at ON audit_logs(at)",
        "CREATE INDEX ix_audit_reagent ON audit_logs(reagent_id)",
        // Faqat tarixsiz (bitta boshlang‘ich kirimli) partiyani o‘chirish uchun: shu tranzaksiya ichida ochiladi va yopiladi.
        "CREATE TABLE ledger_unlock(batch_id INTEGER PRIMARY KEY)",

        // ---- Harakatlar jurnali: faqat qo‘shiladi ----
        """
        CREATE TRIGGER tr_movements_no_update BEFORE UPDATE ON stock_movements
        BEGIN SELECT RAISE(ABORT, 'append-only'); END
        """,
        """
        CREATE TRIGGER tr_movements_no_delete BEFORE DELETE ON stock_movements
        WHEN NOT EXISTS (SELECT 1 FROM ledger_unlock WHERE batch_id = OLD.batch_id)
        BEGIN SELECT RAISE(ABORT, 'append-only'); END
        """,
        // Bazaning o‘zi kafolatlaydi: partiya qoldig‘i hech qachon manfiy bo‘lmaydi.
        """
        CREATE TRIGGER tr_movements_no_negative AFTER INSERT ON stock_movements
        WHEN (SELECT SUM(qty) FROM stock_movements WHERE batch_id = NEW.batch_id) < 0
        BEGIN SELECT RAISE(ABORT, 'NEGATIVE_STOCK'); END
        """,
        // Teskari yozuv aynan asl harakatning teskarisi bo‘lishi kerak (o‘sha partiya, −miqdor), teskarining teskarisi yo‘q.
        """
        CREATE TRIGGER tr_movements_reversal BEFORE INSERT ON stock_movements
        WHEN NEW.kind = 'REVERSAL' AND NOT EXISTS (
            SELECT 1 FROM stock_movements o
            WHERE o.id = NEW.reverses_id AND o.kind <> 'REVERSAL' AND o.batch_id = NEW.batch_id AND o.qty = -NEW.qty
        )
        BEGIN SELECT RAISE(ABORT, 'BAD_REVERSAL'); END
        """,

        // ---- Audit jurnali: faqat qo‘shiladi ----
        """
        CREATE TRIGGER tr_audit_no_update BEFORE UPDATE ON audit_logs
        BEGIN SELECT RAISE(ABORT, 'append-only'); END
        """,
        """
        CREATE TRIGGER tr_audit_no_delete BEFORE DELETE ON audit_logs
        BEGIN SELECT RAISE(ABORT, 'append-only'); END
        """,

        // ---- Operatsiyalar: faqat bir marta «bekor qilindi» deb belgilash mumkin, boshqa hech narsa o‘zgarmaydi ----
        """
        CREATE TRIGGER tr_receipts_guard BEFORE UPDATE ON receipts
        WHEN OLD.cancelled_at IS NOT NULL OR NEW.cancelled_at IS NULL
          OR NEW.batch_id IS NOT OLD.batch_id OR NEW.qty IS NOT OLD.qty OR NEW.received_on IS NOT OLD.received_on
          OR NEW.entered_qty IS NOT OLD.entered_qty OR NEW.entered_unit IS NOT OLD.entered_unit
          OR NEW.is_initial IS NOT OLD.is_initial OR NEW.created_at IS NOT OLD.created_at OR NEW.corrects_id IS NOT OLD.corrects_id
        BEGIN SELECT RAISE(ABORT, 'append-only'); END
        """,
        """
        CREATE TRIGGER tr_receipts_no_delete BEFORE DELETE ON receipts
        WHEN NOT EXISTS (SELECT 1 FROM ledger_unlock WHERE batch_id = OLD.batch_id)
        BEGIN SELECT RAISE(ABORT, 'append-only'); END
        """,
        """
        CREATE TRIGGER tr_issues_no_update BEFORE UPDATE ON issues
        BEGIN SELECT RAISE(ABORT, 'append-only'); END
        """,
        """
        CREATE TRIGGER tr_issues_no_delete BEFORE DELETE ON issues
        BEGIN SELECT RAISE(ABORT, 'append-only'); END
        """,
        """
        CREATE TRIGGER tr_issue_items_guard BEFORE UPDATE ON issue_items
        WHEN OLD.cancelled_at IS NOT NULL OR NEW.cancelled_at IS NULL
          OR NEW.issue_id IS NOT OLD.issue_id OR NEW.batch_id IS NOT OLD.batch_id OR NEW.qty IS NOT OLD.qty
          OR NEW.recipient_type IS NOT OLD.recipient_type OR NEW.department_id IS NOT OLD.department_id
          OR NEW.district_id IS NOT OLD.district_id OR NEW.entered_qty IS NOT OLD.entered_qty
          OR NEW.entered_unit IS NOT OLD.entered_unit OR NEW.created_at IS NOT OLD.created_at
          OR NEW.corrects_id IS NOT OLD.corrects_id
        BEGIN SELECT RAISE(ABORT, 'append-only'); END
        """,
        """
        CREATE TRIGGER tr_issue_items_no_delete BEFORE DELETE ON issue_items
        BEGIN SELECT RAISE(ABORT, 'append-only'); END
        """,
        """
        CREATE TRIGGER tr_returns_guard BEFORE UPDATE ON returns
        WHEN OLD.cancelled_at IS NOT NULL OR NEW.cancelled_at IS NULL
          OR NEW.issue_item_id IS NOT OLD.issue_item_id OR NEW.qty IS NOT OLD.qty OR NEW.returned_on IS NOT OLD.returned_on
          OR NEW.entered_qty IS NOT OLD.entered_qty OR NEW.entered_unit IS NOT OLD.entered_unit
          OR NEW.created_at IS NOT OLD.created_at
        BEGIN SELECT RAISE(ABORT, 'append-only'); END
        """,
        """
        CREATE TRIGGER tr_returns_no_delete BEFORE DELETE ON returns
        BEGIN SELECT RAISE(ABORT, 'append-only'); END
        """,

        // ---- Boshlang‘ich qiymatlar ----
        """
        INSERT INTO app_settings(key, value) VALUES
            ('app', '$APP_MARKER'), ('org_name', ''), ('actor_name', ''), ('auto_backup_keep', '14')
        """,
        """
        INSERT INTO departments(name, name_key, active, sort_order, created_at, updated_at) VALUES
            ('Gistologiya', 'gistologiya', 1, 1, $NOW, $NOW),
            ('Biologiya', 'biologiya', 1, 2, $NOW, $NOW),
            ('Sud-kimyo', 'sud-kimyo', 1, 3, $NOW, $NOW),
            ('Morfologiya', 'morfologiya', 1, 4, $NOW, $NOW)
        """,
    ).map { it.trimIndent() }

    /**
     * 2-versiya: eski jadvaldan ko‘chirilgan qatorlar ro‘yxati (kalit — «createdAt|nom kaliti»). Shu qator boshqa faylda
     * (.bak.json, reaktivlar.json, qayta saqlangan fayl) yana uchrasa — ikkinchi marta qo‘shilmaydi.
     */
    private val v2 = listOf(
        """
        CREATE TABLE legacy_rows(
            key TEXT PRIMARY KEY,
            imported_at TEXT NOT NULL,
            source TEXT
        )
        """,
    ).map { it.trimIndent() }

    private val steps: Map<Int, List<String>> = mapOf(1 to v1, 2 to v2)
}
