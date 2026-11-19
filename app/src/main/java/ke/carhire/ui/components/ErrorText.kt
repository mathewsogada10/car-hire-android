package ke.carhire.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ErrorText(message: String) {
    if (message.isNotBlank())
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 12.dp),
        )
}
