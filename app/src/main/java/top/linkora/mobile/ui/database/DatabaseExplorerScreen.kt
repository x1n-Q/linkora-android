package top.linkora.mobile.ui.database

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.linkora.mobile.database.DatabaseExplorerMode
import top.linkora.mobile.database.DatabaseExplorerState
import top.linkora.mobile.database.DatabaseQueryResult
import top.linkora.mobile.database.DatabaseTable
import top.linkora.mobile.ui.theme.Charcoal
import top.linkora.mobile.ui.theme.DisabledSurface
import top.linkora.mobile.ui.theme.DisabledText
import top.linkora.mobile.ui.theme.MutedText
import top.linkora.mobile.ui.theme.Oxblood
import top.linkora.mobile.ui.theme.PaleStone
import top.linkora.mobile.ui.theme.PaperRaised
import top.linkora.mobile.ui.theme.WarmPaper

@Composable
fun DatabaseExplorerScreen(
    state: DatabaseExplorerState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetMode: (DatabaseExplorerMode) -> Unit,
    onShowTables: () -> Unit,
    onSelectTable: (String) -> Unit,
    onUpdateQuery: (String) -> Unit,
    onRunQuery: () -> Unit,
) {
    BackHandler(onBack = onBack)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sidePadding = ((maxWidth - 720.dp) / 2).coerceAtLeast(18.dp)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = sidePadding,
                top = 18.dp,
                end = sidePadding,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Database explorer",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = "Browse MariaDB safely from this phone.",
                            modifier = Modifier.padding(top = 3.dp),
                            color = MutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    ExplorerButton("Back", onBack, primary = false)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExplorerButton(
                        label = "Tables",
                        onClick = { onSetMode(DatabaseExplorerMode.Tables) },
                        modifier = Modifier.weight(1f),
                        primary = state.mode == DatabaseExplorerMode.Tables,
                    )
                    ExplorerButton(
                        label = "SQL",
                        onClick = { onSetMode(DatabaseExplorerMode.Sql) },
                        modifier = Modifier.weight(1f),
                        primary = state.mode == DatabaseExplorerMode.Sql,
                    )
                    ExplorerButton(
                        label = "Refresh",
                        onClick = onRefresh,
                        enabled = !state.isLoading,
                        primary = false,
                    )
                }
            }

            if (state.isLoading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Oxblood,
                        trackColor = PaleStone,
                    )
                }
            }

            state.error?.let { error ->
                item { ExplorerNotice(error, error = true) }
            }
            state.message?.let { message ->
                item { ExplorerNotice(message, error = false) }
            }

            when (state.mode) {
                DatabaseExplorerMode.Tables -> {
                    if (state.selectedTable == null) {
                        item {
                            Text("Tables", style = MaterialTheme.typography.titleLarge)
                        }
                        if (!state.isLoading && state.tables.isEmpty()) {
                            item {
                                EmptyDatabase()
                            }
                        }
                        items(state.tables, key = DatabaseTable::name) { table ->
                            TableRow(table = table, onClick = { onSelectTable(table.name) })
                        }
                    } else {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = state.selectedTable,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "Read-only preview",
                                        color = MutedText,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                ExplorerButton("All tables", onShowTables, primary = false)
                            }
                        }
                        state.result?.let { result ->
                            item { ResultGrid(result) }
                        }
                    }
                }

                DatabaseExplorerMode.Sql -> {
                    item {
                        Text("Read-only SQL", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "SELECT, SHOW, DESCRIBE, DESC, and EXPLAIN are supported.",
                            modifier = Modifier.padding(top = 3.dp),
                            color = MutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onUpdateQuery,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("SQL query") },
                            minLines = 5,
                            maxLines = 12,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            enabled = !state.isLoading,
                        )
                    }
                    item {
                        ExplorerButton(
                            label = "Run query",
                            onClick = onRunQuery,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading,
                        )
                    }
                    state.result?.let { result ->
                        item { ResultGrid(result) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableRow(table: DatabaseTable, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = PaperRaised,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, PaleStone),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = table.name,
                modifier = Modifier.weight(1f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = table.estimatedRows?.let { "~$it rows" } ?: "Open",
                color = MutedText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ResultGrid(result: DatabaseQueryResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, PaleStone),
    ) {
        if (result.columns.isEmpty()) {
            Text("The query returned no columns.", modifier = Modifier.padding(16.dp))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                ResultRow(values = result.columns, header = true)
                result.rows.forEach { row ->
                    HorizontalDivider(color = PaleStone)
                    ResultRow(values = row, header = false)
                }
                if (result.rows.isEmpty()) {
                    HorizontalDivider(color = PaleStone)
                    Text(
                        text = "No rows",
                        modifier = Modifier.padding(14.dp),
                        color = MutedText,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(values: List<String>, header: Boolean) {
    Row {
        values.forEach { value ->
            Text(
                text = value,
                modifier = Modifier
                    .width(148.dp)
                    .padding(horizontal = 10.dp, vertical = 11.dp),
                color = if (header) Oxblood else Charcoal,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                maxLines = if (header) 2 else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyDatabase() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PaperRaised,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, PaleStone),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("No tables yet", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Start your project once so it can create its database tables, then refresh.",
                modifier = Modifier.padding(top = 5.dp),
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ExplorerNotice(text: String, error: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (error) Oxblood.copy(alpha = 0.07f) else PaperRaised,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (error) Oxblood else PaleStone),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            color = if (error) Oxblood else MutedText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ExplorerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Oxblood else PaperRaised,
            contentColor = if (primary) PaperRaised else Charcoal,
            disabledContainerColor = DisabledSurface,
            disabledContentColor = DisabledText,
        ),
        border = if (primary) null else BorderStroke(1.dp, PaleStone),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
