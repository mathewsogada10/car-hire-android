package ke.carhire.ui.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ke.carhire.R
import ke.carhire.domain.rules.Rules
import ke.carhire.ui.editor.Forms
import kotlinx.coroutines.launch

internal fun workspaceTitle(page: String): String =
    when (page) {
        "home" -> "Home"
        "financial" -> "Financial report"
        "data" -> "Data & backups"
        "sync" -> "Sync & data health"
        "audit" -> "Audit"
        else -> Forms.titles.getValue(page)
    }

@Composable
fun WorkspaceNavigation(
    page: String,
    onSelect: (String) -> Unit,
    content: @Composable (openNavigation: () -> Unit, navigationOpen: Boolean) -> Unit,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val expanded = drawer.isOpen || drawer.targetValue == DrawerValue.Open
    val close: () -> Unit = { scope.launch { drawer.close() } }
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = expanded,
        drawerContent = {
            ModalDrawerSheet(Modifier.fillMaxHeight().width(320.dp)) {
                if (expanded) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Navigation",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        IconButton(onClick = close) {
                            Icon(painterResource(R.drawable.ic_close), "Close navigation menu")
                        }
                    }
                    Text(
                        "Scroll to view all sections",
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    HorizontalDivider()
                    LazyColumn(
                        Modifier.weight(1f).testTag("workspace-navigation"),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        val sections =
                            listOf(
                                "Overview" to listOf("home", "financial"),
                                "Business" to Rules.kinds.filterNot { it == "settings" },
                                "Tools & settings" to listOf("data", "sync", "audit", "settings"),
                            )
                        sections.forEach { (heading, keys) ->
                            item {
                                Text(
                                    heading,
                                    Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            items(keys, key = { it }) { key ->
                                NavigationDrawerItem(
                                    label = { Text(workspaceTitle(key)) },
                                    selected = page == key,
                                    onClick = {
                                        scope.launch {
                                            drawer.close()
                                            if (key != page) onSelect(key)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) {
        content({ scope.launch { drawer.open() } }, expanded)
    }
    BackHandler(enabled = expanded, onBack = close)
}
