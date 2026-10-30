package de.scenicroutes.app.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import de.scenicroutes.app.ui.Tokens

/**
 * A10: Im Fahrmodus beendet die System-Zurück-Geste die Navigation nicht mehr versehentlich.
 * Zurück (auch die vorausschauende Geste ab Android 14) öffnet „Navigation beenden?“;
 * „Beenden“ ruft [onStop] (wie der Beenden-Knopf), „Weiterfahren“ oder Zurück im Dialog bleibt im Fahrmodus.
 * Außerhalb des Fahrmodus ([active] = false) greift die normale Zurück-Navigation.
 */
@Composable
fun DriveBackGuard(active: Boolean, onStop: () -> Unit) {
    var ask by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = active) { ask = !ask }
    if (active && ask) StopNavigationDialog(onConfirm = { ask = false; onStop() }, onDismiss = { ask = false })
}

@Composable
fun StopNavigationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("stopNavDialog"),
        title = { Text("Navigation beenden?") },
        text = { Text("Die Zielführung wird gestoppt. Eine laufende Aufzeichnung kannst du danach speichern.", style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.heightIn(min = Tokens.touchDrive).testTag("stopNavConfirm")) {
                Text("Beenden", style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Tokens.touchDrive).testTag("stopNavCancel")) {
                Text("Weiterfahren", style = MaterialTheme.typography.titleMedium)
            }
        },
    )
}
