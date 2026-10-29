package uz.reaktivombor.desktop.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Dastur belgilari. material-icons-core’da borlari — undan (Outlined uslubida), yo‘qlari — shu yerda 24×24 to‘rda
 * 2 qalinlikdagi chiziq bilan chizilgan (Outlined belgilar bilan bir xil ko‘rinadi). Rang Icon(tint) dan olinadi.
 */
object AppIcons {
    // Navigatsiya (13 bo‘lim)
    val Dashboard: ImageVector get() = Icons.Outlined.Home
    val Reagents: ImageVector by lazy { lineIcon("Reagents") { flask(cx = 12f, neckHalf = 2.2f, baseHalf = 8f) } }
    val AddReagent: ImageVector by lazy {
        lineIcon("AddReagent") {
            flask(cx = 9.5f, neckHalf = 2f, baseHalf = 7f)
            moveTo(19f, 2.5f); verticalLineTo(9.5f)
            moveTo(15.5f, 6f); horizontalLineTo(22.5f)
        }
    }
    val Receipts: ImageVector by lazy {
        lineIcon("Receipts") {
            tray()
            moveTo(12f, 2.5f); verticalLineTo(10.5f)
            moveTo(8.5f, 7f); lineTo(12f, 10.5f); lineTo(15.5f, 7f)
        }
    }
    val Issues: ImageVector by lazy {
        lineIcon("Issues") {
            tray()
            moveTo(12f, 10.5f); verticalLineTo(2.5f)
            moveTo(8.5f, 6f); lineTo(12f, 2.5f); lineTo(15.5f, 6f)
        }
    }
    val Departments: ImageVector by lazy {
        lineIcon("Departments") {
            // Asosiy bino va yon qanot
            moveTo(5f, 21f); verticalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 7f, 3f)
            horizontalLineTo(13f)
            arcTo(2f, 2f, 0f, false, true, 15f, 5f)
            verticalLineTo(21f)
            moveTo(15f, 9f); horizontalLineTo(18f)
            arcTo(1.5f, 1.5f, 0f, false, true, 19.5f, 10.5f)
            verticalLineTo(21f)
            moveTo(3f, 21f); horizontalLineTo(21f)
            // Derazalar
            moveTo(8.5f, 7f); horizontalLineTo(11.5f)
            moveTo(8.5f, 11f); horizontalLineTo(11.5f)
            moveTo(8.5f, 15f); horizontalLineTo(11.5f)
            moveTo(17.2f, 13f); horizontalLineTo(17.3f)
            moveTo(17.2f, 17f); horizontalLineTo(17.3f)
        }
    }
    val Districts: ImageVector get() = Icons.Outlined.LocationOn
    val Expiry: ImageVector by lazy {
        lineIcon("Expiry") {
            moveTo(5.5f, 2.5f); horizontalLineTo(18.5f)
            moveTo(5.5f, 21.5f); horizontalLineTo(18.5f)
            moveTo(7f, 2.5f)
            curveTo(7f, 7.5f, 10.5f, 9f, 10.5f, 12f)
            curveTo(10.5f, 15f, 7f, 16.5f, 7f, 21.5f)
            moveTo(17f, 2.5f)
            curveTo(17f, 7.5f, 13.5f, 9f, 13.5f, 12f)
            curveTo(13.5f, 15f, 17f, 16.5f, 17f, 21.5f)
            // Pastdagi qum
            moveTo(9f, 19f); quadTo(12f, 16.5f, 15f, 19f)
        }
    }
    val Stock: ImageVector by lazy {
        lineIcon("Stock") {
            roundRect(2.5f, 13f, 8.5f, 8f, 1.2f)
            roundRect(13f, 13f, 8.5f, 8f, 1.2f)
            roundRect(7.75f, 3f, 8.5f, 8f, 1.2f)
            moveTo(5.5f, 13f); verticalLineTo(15.5f)
            moveTo(16f, 13f); verticalLineTo(15.5f)
            moveTo(10.75f, 3f); verticalLineTo(5.5f)
        }
    }
    val Reports: ImageVector by lazy {
        lineIcon("Reports") {
            moveTo(3f, 3f); verticalLineTo(19f)
            arcTo(2f, 2f, 0f, false, false, 5f, 21f)
            horizontalLineTo(21f)
            moveTo(8f, 17f); verticalLineTo(12f)
            moveTo(12.5f, 17f); verticalLineTo(7f)
            moveTo(17f, 17f); verticalLineTo(10f)
        }
    }
    val History: ImageVector by lazy {
        lineIcon("History") {
            moveTo(3f, 12f)
            arcTo(9f, 9f, 0f, true, false, 5.64f, 5.64f)
            lineTo(3f, 8.3f)
            moveTo(3f, 3.5f); verticalLineTo(8.3f); horizontalLineTo(7.8f)
            moveTo(12f, 7f); verticalLineTo(12f); lineTo(15.5f, 14f)
        }
    }
    val Backup: ImageVector by lazy {
        lineIcon("Backup") {
            ellipse(12f, 5f, 8f, 3f)
            moveTo(4f, 5f); verticalLineTo(19f)
            arcTo(8f, 3f, 0f, false, false, 20f, 19f)
            verticalLineTo(5f)
            moveTo(4f, 12f)
            arcTo(8f, 3f, 0f, false, false, 20f, 12f)
        }
    }
    val Settings: ImageVector get() = Icons.Outlined.Settings

    // Umumiy amallar
    val Add: ImageVector get() = Icons.Filled.Add
    val Edit: ImageVector get() = Icons.Outlined.Edit
    val Delete: ImageVector get() = Icons.Outlined.Delete
    val Search: ImageVector get() = Icons.Filled.Search
    val Close: ImageVector get() = Icons.Filled.Close
    val Check: ImageVector get() = Icons.Filled.Check
    val Refresh: ImageVector get() = Icons.Filled.Refresh
    val More: ImageVector get() = Icons.Filled.MoreVert
    val DropDown: ImageVector get() = Icons.Filled.ArrowDropDown
    val ChevronDown: ImageVector get() = Icons.Filled.KeyboardArrowDown
    val ChevronUp: ImageVector get() = Icons.Filled.KeyboardArrowUp
    val ChevronLeft: ImageVector get() = Icons.AutoMirrored.Outlined.KeyboardArrowLeft
    val ChevronRight: ImageVector get() = Icons.AutoMirrored.Outlined.KeyboardArrowRight
    val Calendar: ImageVector get() = Icons.Outlined.DateRange
    val Bell: ImageVector get() = Icons.Outlined.Notifications
    val Person: ImageVector get() = Icons.Outlined.Person
    val Info: ImageVector get() = Icons.Outlined.Info
    // Material’ning Outlined.Warning’i to‘la bo‘yalgan — boshqalariga mos chiziqli uchburchak.
    val Warning: ImageVector by lazy {
        lineIcon("Warning") {
            moveTo(12f, 3.5f); lineTo(21.5f, 20f); horizontalLineTo(2.5f); close()
            moveTo(12f, 9.5f); verticalLineTo(14f)
            moveTo(12f, 17f); verticalLineTo(17.1f)
        }
    }
    val Success: ImageVector get() = Icons.Outlined.CheckCircle
    val Error: ImageVector by lazy {
        lineIcon("Error") {
            circle(12f, 12f, 9f)
            moveTo(12f, 7.5f); verticalLineTo(12.5f)
            moveTo(12f, 16.2f); verticalLineTo(16.3f)
        }
    }
    val Cancel: ImageVector by lazy {
        lineIcon("Cancel") {
            circle(12f, 12f, 9f)
            moveTo(5.6f, 5.6f); lineTo(18.4f, 18.4f)
        }
    }
    val Undo: ImageVector by lazy {
        lineIcon("Undo") {
            moveTo(9f, 14f); lineTo(4f, 9f); lineTo(9f, 4f)
            moveTo(4f, 9f); horizontalLineTo(14.5f)
            arcTo(5.5f, 5.5f, 0f, false, true, 14.5f, 20f)
            horizontalLineTo(11f)
        }
    }
    val Archive: ImageVector by lazy {
        lineIcon("Archive") {
            roundRect(2.5f, 3.5f, 19f, 5f, 1f)
            moveTo(4.5f, 8.5f); verticalLineTo(19f)
            arcTo(1.5f, 1.5f, 0f, false, false, 6f, 20.5f)
            horizontalLineTo(18f)
            arcTo(1.5f, 1.5f, 0f, false, false, 19.5f, 19f)
            verticalLineTo(8.5f)
            moveTo(10f, 12.5f); horizontalLineTo(14f)
        }
    }
    val Filter: ImageVector by lazy {
        lineIcon("Filter") {
            moveTo(3f, 4f); horizontalLineTo(21f)
            lineTo(14f, 12.5f); verticalLineTo(19f)
            lineTo(10f, 21f); verticalLineTo(12.5f)
            close()
        }
    }
    val Columns: ImageVector by lazy {
        lineIcon("Columns") {
            roundRect(3f, 4f, 18f, 16f, 2f)
            moveTo(9f, 4f); verticalLineTo(20f)
            moveTo(15f, 4f); verticalLineTo(20f)
        }
    }
    val ExpandAll: ImageVector by lazy {
        lineIcon("ExpandAll") {
            moveTo(7f, 6f); lineTo(12f, 11f); lineTo(17f, 6f)
            moveTo(7f, 13f); lineTo(12f, 18f); lineTo(17f, 13f)
        }
    }
    val CollapseAll: ImageVector by lazy {
        lineIcon("CollapseAll") {
            moveTo(7f, 11f); lineTo(12f, 6f); lineTo(17f, 11f)
            moveTo(7f, 18f); lineTo(12f, 13f); lineTo(17f, 18f)
        }
    }
    val Download: ImageVector by lazy {
        lineIcon("Download") {
            moveTo(12f, 3f); verticalLineTo(15f)
            moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
            moveTo(4f, 17f); verticalLineTo(19f)
            arcTo(2f, 2f, 0f, false, false, 6f, 21f)
            horizontalLineTo(18f)
            arcTo(2f, 2f, 0f, false, false, 20f, 19f)
            verticalLineTo(17f)
        }
    }
    val Upload: ImageVector by lazy {
        lineIcon("Upload") {
            moveTo(12f, 15f); verticalLineTo(3f)
            moveTo(7f, 8f); lineTo(12f, 3f); lineTo(17f, 8f)
            moveTo(4f, 17f); verticalLineTo(19f)
            arcTo(2f, 2f, 0f, false, false, 6f, 21f)
            horizontalLineTo(18f)
            arcTo(2f, 2f, 0f, false, false, 20f, 19f)
            verticalLineTo(17f)
        }
    }
    val Folder: ImageVector by lazy {
        lineIcon("Folder") {
            moveTo(3f, 7f); verticalLineTo(18f)
            arcTo(2f, 2f, 0f, false, false, 5f, 20f)
            horizontalLineTo(19f)
            arcTo(2f, 2f, 0f, false, false, 21f, 18f)
            verticalLineTo(9f)
            arcTo(2f, 2f, 0f, false, false, 19f, 7f)
            horizontalLineTo(12f)
            lineTo(10f, 4f)
            horizontalLineTo(5f)
            arcTo(2f, 2f, 0f, false, false, 3f, 6f)
            close()
        }
    }
    /** Excel fayli: varaq va katakchalar. */
    val Excel: ImageVector by lazy {
        lineIcon("Excel") {
            roundRect(3f, 3f, 18f, 18f, 2f)
            moveTo(3f, 9f); horizontalLineTo(21f)
            moveTo(3f, 15f); horizontalLineTo(21f)
            moveTo(9f, 3f); verticalLineTo(21f)
        }
    }
    /** PDF / hujjat: buklangan burchakli varaq. */
    val Document: ImageVector by lazy {
        lineIcon("Document") {
            moveTo(14f, 2.5f); horizontalLineTo(6.5f)
            arcTo(2f, 2f, 0f, false, false, 4.5f, 4.5f)
            verticalLineTo(19.5f)
            arcTo(2f, 2f, 0f, false, false, 6.5f, 21.5f)
            horizontalLineTo(17.5f)
            arcTo(2f, 2f, 0f, false, false, 19.5f, 19.5f)
            verticalLineTo(8f)
            close()
            moveTo(14f, 2.5f); verticalLineTo(8f); horizontalLineTo(19.5f)
            moveTo(8.5f, 13f); horizontalLineTo(15.5f)
            moveTo(8.5f, 17f); horizontalLineTo(13.5f)
        }
    }
    val Inbox: ImageVector by lazy {
        lineIcon("Inbox") {
            tray()
            moveTo(3f, 13f); lineTo(5.6f, 5.2f)
            arcTo(1.5f, 1.5f, 0f, false, true, 7f, 4.2f)
            horizontalLineTo(17f)
            arcTo(1.5f, 1.5f, 0f, false, true, 18.4f, 5.2f)
            lineTo(21f, 13f)
        }
    }
}

private fun lineIcon(name: String, build: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = build,
    ).build()

/** Kolba: og‘zi, tor bo‘yni va keng tubi (burchaklari yumaloq), ichida suyuqlik chizig‘i. */
private fun PathBuilder.flask(cx: Float, neckHalf: Float, baseHalf: Float) {
    val top = 3f
    val neckBottom = 9.5f
    val bottom = 21f
    moveTo(cx - neckHalf - 1.3f, top); horizontalLineTo(cx + neckHalf + 1.3f)
    moveTo(cx - neckHalf, top); verticalLineTo(neckBottom)
    lineTo(cx - baseHalf + 0.8f, bottom - 1.6f)
    quadTo(cx - baseHalf, bottom, cx - baseHalf + 1.6f, bottom)
    horizontalLineTo(cx + baseHalf - 1.6f)
    quadTo(cx + baseHalf, bottom, cx + baseHalf - 0.8f, bottom - 1.6f)
    lineTo(cx + neckHalf, neckBottom)
    verticalLineTo(top)
    val liquid = 15f
    val spread = (liquid - neckBottom) / (bottom - neckBottom) * (baseHalf - neckHalf)
    moveTo(cx - neckHalf - spread + 0.6f, liquid); horizontalLineTo(cx + neckHalf + spread - 0.6f)
}

/** Kirim/chiqim qutisi (tepasi ochiq laganda). */
private fun PathBuilder.tray() {
    moveTo(3f, 13f); horizontalLineTo(7.5f)
    lineTo(9f, 15.5f); horizontalLineTo(15f)
    lineTo(16.5f, 13f); horizontalLineTo(21f)
    moveTo(3f, 13f); verticalLineTo(19f)
    arcTo(2f, 2f, 0f, false, false, 5f, 21f)
    horizontalLineTo(19f)
    arcTo(2f, 2f, 0f, false, false, 21f, 19f)
    verticalLineTo(13f)
}

private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    horizontalLineTo(x + w - r)
    arcTo(r, r, 0f, false, true, x + w, y + r)
    verticalLineTo(y + h - r)
    arcTo(r, r, 0f, false, true, x + w - r, y + h)
    horizontalLineTo(x + r)
    arcTo(r, r, 0f, false, true, x, y + h - r)
    verticalLineTo(y + r)
    arcTo(r, r, 0f, false, true, x + r, y)
    close()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) = ellipse(cx, cy, r, r)

private fun PathBuilder.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
    moveTo(cx - rx, cy)
    arcTo(rx, ry, 0f, true, true, cx + rx, cy)
    arcTo(rx, ry, 0f, true, true, cx - rx, cy)
    close()
}
