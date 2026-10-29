package uz.reaktivombor.desktop.ui.pages

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import uz.reaktivombor.desktop.ui.AppState
import uz.reaktivombor.desktop.ui.Page
import uz.reaktivombor.desktop.ui.components.PageScaffold
import uz.reaktivombor.desktop.ui.theme.AppColors

// Vaqtinchalik: keyingi bosqichda bu fayl to‘liq sahifa bilan almashtiriladi.
@Composable
fun SettingsPage(state: AppState) {
    PageScaffold(title = Page.SETTINGS.title) {
        Text("Bu sahifa keyingi bosqichda to‘ldiriladi", color = AppColors.Muted)
    }
}
