package uz.reaktivombor.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.reaktivombor.desktop.ui.AppIcons
import uz.reaktivombor.desktop.ui.theme.AppColors
import uz.reaktivombor.desktop.ui.theme.Severity
import uz.reaktivombor.desktop.ui.theme.style

/**
 * Tasdiqlash oynasi. [danger] — qaytarib bo‘lmaydigan amal (qizil tugma). [busy] — yozish ketyapti: tugmalar
 * o‘chiriladi (ikki marta bosilmasin).
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Tasdiqlash",
    dismissLabel: String = "Bekor qilish",
    danger: Boolean = false,
    busy: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = if (danger) {
            { Icon(AppIcons.Warning, contentDescription = null, tint = AppColors.Error) }
        } else {
            null
        },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge, color = AppColors.Text) },
        confirmButton = {
            AppButton(confirmLabel, onClick = onConfirm, kind = if (danger) ButtonKind.DANGER else ButtonKind.PRIMARY, enabled = !busy)
        },
        dismissButton = { AppButton(dismissLabel, onClick = onDismiss, kind = ButtonKind.SECONDARY, enabled = !busy) },
        modifier = Modifier.width(DialogWidth),
        shape = RoundedCornerShape(12.dp),
        containerColor = AppColors.Surface,
    )
}

/**
 * Sabab so‘raydigan oyna (bekor qilish, tuzatish, hisobdan chiqarish). Sabab yozilmaguncha tasdiqlash tugmasi
 * o‘chiq; [onConfirm] ga chetlari tozalangan sabab beriladi.
 */
@Composable
fun ReasonDialog(
    title: String,
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    label: String = "Sabab",
    placeholder: String = "Sababini yozing",
    confirmLabel: String = "Tasdiqlash",
    dismissLabel: String = "Bekor qilish",
    danger: Boolean = false,
    busy: Boolean = false,
    initialReason: String = "",
) {
    var reason by remember { mutableStateOf(initialReason) }
    var tried by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val blank = reason.isBlank()
    val submit = {
        tried = true
        if (!blank && !busy) onConfirm(reason.trim())
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodyLarge, color = AppColors.Text, modifier = Modifier.padding(bottom = 12.dp))
                }
                AppTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = label,
                    required = true,
                    placeholder = placeholder,
                    error = if (tried && blank) "Sabab kiritilishi shart." else null,
                    onEnter = submit,
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = focus,
                    testTag = "reason",
                )
            }
        },
        confirmButton = {
            AppButton(confirmLabel, onClick = submit, kind = if (danger) ButtonKind.DANGER else ButtonKind.PRIMARY, enabled = !blank && !busy)
        },
        dismissButton = { AppButton(dismissLabel, onClick = onDismiss, kind = ButtonKind.SECONDARY, enabled = !busy) },
        modifier = Modifier.width(DialogWidth),
        shape = RoundedCornerShape(12.dp),
        containerColor = AppColors.Surface,
    )
}

/** Xato oynasi: tushunarli matn va (bo‘lsa) «Batafsil» ostida texnik tafsilot (nusxa olsa bo‘ladi). */
@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    title: String = "Xatolik",
    details: String? = null,
) {
    var showDetails by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Error, contentDescription = null, tint = Severity.ERROR.style.accent) },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyLarge, color = AppColors.Text)
                if (details != null && showDetails) {
                    SelectionContainer {
                        Text(
                            // Tabulyatsiya belgisi shriftda yo‘q (kvadrat bo‘lib chiqadi).
                            details.replace("\t", "    "),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = AppColors.Muted,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(AppColors.Background)
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { AppButton("Yopish", onClick = onDismiss) },
        dismissButton = if (details != null) {
            { AppButton(if (showDetails) "Tafsilotni yashirish" else "Batafsil", onClick = { showDetails = !showDetails }, kind = ButtonKind.TEXT) }
        } else {
            null
        },
        modifier = Modifier.width(DialogWidth),
        shape = RoundedCornerShape(12.dp),
        containerColor = AppColors.Surface,
    )
}

private val DialogWidth = 460.dp
