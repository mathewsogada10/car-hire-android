package ke.carhire.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ke.carhire.LocalRecord
import ke.carhire.data.repository.Repository

@Composable
fun AuditScreen(repository: Repository, all: List<LocalRecord>) {

    var evidence by remember { mutableStateOf("") }
    LaunchedEffect(all) {
        evidence =
            repository.dao
                .evidence()
                .filter { it.kind == "audit" }
                .take(100)
                .joinToString("\n\n") { it.payload }
    }
    Text(
        evidence.ifEmpty { "No activity yet" },
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
    )
}
