package io.github.paulsnuff.betternightlight.samsung

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun SamsungManualControlOptions(selected: SamsungManualResumePolicy, enabled: Boolean, onSelect: (SamsungManualResumePolicy) -> Unit) {
    Column(Modifier.selectableGroup()) {
        Text("When you change Samsung's slider or toggle, pause automation:", style = MaterialTheme.typography.bodyMedium)
        listOf(
            SamsungManualResumePolicy.NEXT_CHANGE to "Until the next scheduled change",
            SamsungManualResumePolicy.ONE_HOUR to "For one hour",
            SamsungManualResumePolicy.MANUAL to "Until I tap Resume",
        ).forEach { (policy, label) ->
            Row(Modifier.fillMaxWidth().selectable(selected == policy, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(policy) })
                .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected == policy, onClick = null, enabled = enabled)
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text("Your manual setting is kept. Further native changes restart the hour. The next scheduled change means the next transition's start.", style = MaterialTheme.typography.bodySmall)
    }
}
