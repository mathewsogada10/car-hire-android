package ke.carhire.ui.editor

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ke.carhire.LocalRecord
import ke.carhire.ReminderWorker
import ke.carhire.data.repository.Repository
import ke.carhire.domain.model.SETTINGS_ID
import ke.carhire.ui.components.AttachmentPanel
import ke.carhire.ui.components.ErrorText
import ke.carhire.util.amount
import ke.carhire.util.text
import ke.carhire.util.uuid
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun RecordEditor(
    kind: String,
    row: LocalRecord?,
    all: List<LocalRecord>,
    repository: Repository,
    close: () -> Unit,
) {
    val fields = Forms.fields.getValue(kind)
    val values =
        remember(row?.id, kind) {
            mutableStateMapOf<String, String>().apply {
                fields.forEach { f ->
                    val d = row?.data()
                    put(
                        f.key,
                        if (d?.containsKey(f.key) == true) {
                            when (f.type) {
                                "money" ->
                                    java.math.BigDecimal.valueOf(d.amount(f.key), 2).toPlainString()
                                "instant" -> BusinessDateTime.toInput(d.text(f.key))
                                else -> d.text(f.key)
                            }
                        } else f.options.firstOrNull() ?: if (f.type == "boolean") "false" else "",
                    )
                }
                if (kind == "settings" && row == null)
                    putAll(
                        mapOf(
                            "bufferMinutes" to "0",
                            "reminderLeadDays" to "3",
                            "reminderTime" to "08:00",
                            "backupReminderDays" to "7",
                        )
                    )
            }
        }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val id = remember(row?.id, kind) { row?.id ?: if (kind == "settings") SETTINGS_ID else uuid() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp).testTag("record-editor"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                if (row == null) "New ${Forms.titles[kind]}" else "Edit ${Forms.titles[kind]}",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (row != null && kind != "settings") item { AttachmentPanel(row.id, repository) }
        items(fields) { f -> EditorField(f, values, all) }
        item {
            ErrorText(error)
            if (kind == "settings" && Build.VERSION.SDK_INT >= 33)
                OutlinedButton(
                    onClick = {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                ) {
                    Text("Grant notification permission")
                }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val d = row?.data()?.toMutableMap() ?: mutableMapOf()
                                fields.forEach { f ->
                                    val v = values[f.key].orEmpty().trim()
                                    if (v.isEmpty() && f.optional) d.remove(f.key)
                                    else
                                        d[f.key] =
                                            when (f.type) {
                                                "boolean" -> JsonPrimitive(v == "true")
                                                "money" -> {
                                                    require(
                                                        v.matches(Regex("\\d{1,10}(\\.\\d{1,2})?"))
                                                    ) {
                                                        "${f.label}: use at most two decimal places"
                                                    }
                                                    JsonPrimitive(
                                                        java.math
                                                            .BigDecimal(v)
                                                            .movePointRight(2)
                                                            .longValueExact()
                                                    )
                                                }
                                                "number" -> JsonPrimitive(v.toLong())
                                                "instant" ->
                                                    JsonPrimitive(
                                                        BusinessDateTime.toInstant(v, f.label)
                                                            .toString()
                                                    )
                                                else -> JsonPrimitive(v)
                                            }
                                }
                                repository.save(kind, id, JsonObject(d), row?.updatedAt)
                                ReminderWorker.schedule(context)
                                close()
                            } catch (e: Exception) {
                                error = e.message ?: "Save failed; form retained"
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(if (busy) "Saving..." else "Save")
                }
                OutlinedButton(onClick = close, enabled = !busy) { Text("Cancel") }
            }
        }
    }
}
