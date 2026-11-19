package ke.carhire.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ke.carhire.LocalRecord
import ke.carhire.ui.components.title

@Composable
fun EditorField(f: Field, values: MutableMap<String, String>, all: List<LocalRecord>) {
    val value = values[f.key].orEmpty()
    if (f.type == "instant") DateTimeField(f, value) { values[f.key] = it }
    else if (f.type == "boolean")
        Row {
            Checkbox(value == "true", { values[f.key] = it.toString() })
            Text(f.label, modifier = Modifier.padding(top = 12.dp))
        }
    else if (f.options.isNotEmpty() || f.reference.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        var search by remember { mutableStateOf("") }
        val choices =
            if (f.reference.isNotEmpty())
                all.filter {
                        it.kind == f.reference &&
                            it.deletedAt == null &&
                            (search.isEmpty() || title(it).contains(search, true))
                    }
                    .take(100)
                    .map { it.id to title(it) }
            else f.options.map { it to it }
        Column {
            if (f.reference.isNotEmpty())
                OutlinedTextField(
                    search,
                    { search = it },
                    label = { Text("Search ${f.label}") },
                    modifier = Modifier.fillMaxWidth(),
                )
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${f.label}: ${choices.find { it.first == value }?.second ?: value.ifEmpty { "Choose" }}"
                )
            }
            DropdownMenu(expanded, { expanded = false }) {
                if (f.optional)
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            values[f.key] = ""
                            expanded = false
                        },
                    )
                choices.forEach { (key, title) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            values[f.key] = key
                            expanded = false
                        },
                    )
                }
            }
        }
    } else
        OutlinedTextField(
            value,
            { values[f.key] = it },
            label = { Text(f.label + if (f.optional) " (optional)" else "") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        if (f.type in listOf("money", "number")) KeyboardType.Decimal
                        else KeyboardType.Text
                ),
        )
}
