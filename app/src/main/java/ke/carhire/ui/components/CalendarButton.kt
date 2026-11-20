package ke.carhire.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import ke.carhire.R

@Composable
internal fun CalendarButton(
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(painterResource(R.drawable.ic_calendar), contentDescription = description)
    }
}
