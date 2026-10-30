package de.scenicroutes.app.ui

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import de.scenicroutes.app.data.UpdateState

/** Dialog „Update verfügbar“: Laden → Prüfen → (Freigabe) → Installieren / Fehler. Immer abbrechbar. */
@Composable
fun UpdateDialog(s: UpdateState, onUpdate: () -> Unit, onDismiss: () -> Unit, onAllow: () -> Unit = {}, onBrowser: () -> Unit = {}) {
    val v = when (s) { is UpdateState.Available -> s.v; is UpdateState.Downloading -> s.v; is UpdateState.Verifying -> s.v; is UpdateState.Installing -> s.v
        is UpdateState.NeedsPermission -> s.v; is UpdateState.Confirm -> s.v; is UpdateState.Failed -> s.v; else -> null } ?: return
    val busy = s is UpdateState.Downloading || s is UpdateState.Verifying || s is UpdateState.Installing || s is UpdateState.Confirm
    val required = s is UpdateState.Available && s.required
    AlertDialog(
        onDismissRequest = { if (!required) onDismiss() },
        icon = { Icon(Lucide.RefreshCw, null) },
        title = { Text(Tokens.Terms.update) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.spaceSm), modifier = Modifier.testTag("updateDialog")) {
                Text("Version ${v.versionName}" + (if (v.sizeBytes > 0) " · ${mb(v.sizeBytes)}" else ""), style = MaterialTheme.typography.titleSmall)
                if (s is UpdateState.Available) v.changes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                if (required) Text("Diese Version wird nicht mehr unterstützt – bitte aktualisieren.", color = MaterialTheme.colorScheme.error)
                when (s) {
                    is UpdateState.Downloading -> { if (s.fraction != null) LinearProgressIndicator({ s.fraction }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Lädt …" + (s.fraction?.let { " ${Math.round(it * 100)} %" } ?: "")) }
                    is UpdateState.Verifying -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Prüfe SHA-256 …") }
                    is UpdateState.NeedsPermission -> Text("Damit Scenic Routes sich aktualisieren darf, bitte „Installation aus dieser Quelle zulassen“ einschalten und zurückkehren. Es geht dann automatisch weiter.", modifier = Modifier.testTag("updPerm"))
                    is UpdateState.Installing, is UpdateState.Confirm -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Installation läuft – bitte im Android-Dialog bestätigen", modifier = Modifier.testTag("updInstalling")) }
                    is UpdateState.Failed -> Text(s.msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("updError"))
                    else -> {}
                }
                if (s is UpdateState.Failed || s is UpdateState.NeedsPermission) TextButton(onBrowser, modifier = Modifier.testTag("updBrowser")) { Text("Im Browser herunterladen") }
            }
        },
        confirmButton = {
            when (s) {
                is UpdateState.NeedsPermission -> Button(onAllow, modifier = Modifier.testTag("updAllow")) { Text("Installation erlauben") }
                is UpdateState.Failed -> Button(onUpdate, modifier = Modifier.testTag("updateNow")) { Text("Erneut versuchen") }
                is UpdateState.Available -> Button(onUpdate, modifier = Modifier.testTag("updateNow")) { Text("Jetzt aktualisieren") }
                else -> {}
            }
        },
        dismissButton = { if (!required) TextButton(onDismiss, modifier = Modifier.testTag("updCancel")) { Text(if (busy) "Abbrechen" else "Später") } },
    )
}
