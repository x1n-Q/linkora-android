package top.linkora.mobile.database

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.sql.Blob
import java.sql.Clob
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.linkora.mobile.mariadb.MariaDbCredentialStore

enum class DatabaseExplorerMode {
    Tables,
    Sql,
}

data class DatabaseTable(
    val name: String,
    val estimatedRows: Long?,
)

data class DatabaseQueryResult(
    val columns: List<String>,
    val rows: List<List<String>>,
    val truncated: Boolean,
)

data class DatabaseExplorerState(
    val mode: DatabaseExplorerMode = DatabaseExplorerMode.Tables,
    val isLoading: Boolean = false,
    val tables: List<DatabaseTable> = emptyList(),
    val selectedTable: String? = null,
    val result: DatabaseQueryResult? = null,
    val query: String = "SHOW TABLES",
    val message: String? = null,
    val error: String? = null,
)

class DatabaseExplorerViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(DatabaseExplorerState())
    val uiState = _uiState.asStateFlow()
    private var activeJob: Job? = null

    fun setMode(mode: DatabaseExplorerMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                selectedTable = if (mode == DatabaseExplorerMode.Tables) it.selectedTable else null,
                result = null,
                error = null,
                message = null,
            )
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query, error = null, message = null) }
    }

    fun showTables() {
        _uiState.update {
            it.copy(selectedTable = null, result = null, error = null, message = null)
        }
    }

    fun refresh() {
        runDatabaseTask { connection ->
            val credentials = MariaDbCredentialStore.load(getApplication())
            val tables = connection.prepareStatement(
                """
                SELECT TABLE_NAME, TABLE_ROWS
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, credentials.database)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(
                                DatabaseTable(
                                    name = result.getString("TABLE_NAME"),
                                    estimatedRows = result.getLong("TABLE_ROWS").let { rows ->
                                        if (result.wasNull()) null else rows
                                    },
                                ),
                            )
                        }
                    }
                }
            }
            val selectedTable = _uiState.value.selectedTable
                ?.takeIf { selected -> tables.any { it.name == selected } }
            val selectedResult = selectedTable?.let { readTable(connection, it) }
            _uiState.update { current ->
                current.copy(
                    tables = tables,
                    selectedTable = selectedTable,
                    result = selectedResult,
                    message = selectedResult?.let(::resultMessage),
                )
            }
        }
    }

    fun selectTable(tableName: String) {
        if (_uiState.value.tables.none { it.name == tableName }) {
            _uiState.update { it.copy(error = "That table is no longer available.") }
            return
        }
        runDatabaseTask { connection ->
            val result = readTable(connection, tableName)
            _uiState.update {
                it.copy(selectedTable = tableName, result = result, message = resultMessage(result))
            }
        }
    }

    fun runQuery() {
        val query = _uiState.value.query.trim()
        val validationError = ReadOnlyQueryPolicy.validate(query)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError, message = null) }
            return
        }
        val normalizedQuery = query.removeSuffix(";").trim()
        runDatabaseTask { connection ->
            val result = connection.createStatement().use { statement ->
                statement.maxRows = RESULT_LIMIT + 1
                statement.executeQuery(normalizedQuery).use(::readResult)
            }
            _uiState.update {
                it.copy(result = result, selectedTable = null, message = resultMessage(result))
            }
        }
    }

    private fun runDatabaseTask(block: suspend (Connection) -> Unit) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, message = null) }
            try {
                withContext(Dispatchers.IO) {
                    openConnection().use { connection -> block(connection) }
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(error = friendlyError(error), message = null)
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun openConnection(): Connection {
        val credentials = MariaDbCredentialStore.load(getApplication())
        Class.forName("org.mariadb.jdbc.Driver")
        val properties = Properties().apply {
            setProperty("user", credentials.username)
            setProperty("password", credentials.password)
            setProperty("connectTimeout", "3000")
            setProperty("socketTimeout", "5000")
        }
        return DriverManager.getConnection(
            "jdbc:mariadb://${credentials.host}:${credentials.port}/${credentials.database}",
            properties,
        ).also { connection ->
            runCatching { connection.isReadOnly = true }
        }
    }

    private fun readResult(resultSet: ResultSet): DatabaseQueryResult {
        val metadata = resultSet.metaData
        val columns = (1..metadata.columnCount).map(metadata::getColumnLabel)
        val rows = buildList {
            while (resultSet.next() && size <= RESULT_LIMIT) {
                add((1..metadata.columnCount).map { index -> formatValue(resultSet.getObject(index)) })
            }
        }
        return DatabaseQueryResult(
            columns = columns,
            rows = rows.take(RESULT_LIMIT),
            truncated = rows.size > RESULT_LIMIT,
        )
    }

    private fun readTable(connection: Connection, tableName: String): DatabaseQueryResult {
        val safeName = tableName.replace("`", "``")
        return connection.createStatement().use { statement ->
            statement.maxRows = RESULT_LIMIT + 1
            statement.executeQuery("SELECT * FROM `$safeName`").use(::readResult)
        }
    }

    private fun formatValue(value: Any?): String = when (value) {
        null -> "NULL"
        is ByteArray -> "[${value.size} bytes]"
        is Blob -> "[${value.length()} byte blob]"
        is Clob -> value.getSubString(1, minOf(value.length(), VALUE_LIMIT.toLong()).toInt())
        else -> value.toString().let { text ->
            if (text.length > VALUE_LIMIT) text.take(VALUE_LIMIT) + "..." else text
        }
    }

    private fun resultMessage(result: DatabaseQueryResult): String = when {
        result.truncated -> "Showing the first $RESULT_LIMIT rows."
        result.rows.size == 1 -> "1 row returned."
        else -> "${result.rows.size} rows returned."
    }

    private fun friendlyError(error: Exception): String {
        val detail = error.message?.lineSequence()?.firstOrNull()?.take(180)
        return if (detail.isNullOrBlank()) {
            "Could not open the database. Make sure MariaDB is running."
        } else {
            "Could not open the database. $detail"
        }
    }

    private companion object {
        const val RESULT_LIMIT = 100
        const val VALUE_LIMIT = 500
    }
}

internal object ReadOnlyQueryPolicy {
    private val allowedQuery = Regex(
        "^(SELECT|SHOW|DESCRIBE|DESC|EXPLAIN)\\b[\\s\\S]*$",
        RegexOption.IGNORE_CASE,
    )
    private val blockedPatterns = listOf(
        Regex("\\bINTO\\s+(OUTFILE|DUMPFILE)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bFOR\\s+UPDATE\\b", RegexOption.IGNORE_CASE),
        Regex("\\bLOCK\\s+IN\\s+SHARE\\s+MODE\\b", RegexOption.IGNORE_CASE),
        Regex("\\bLOAD_FILE\\s*\\(", RegexOption.IGNORE_CASE),
    )

    fun validate(query: String): String? {
        if (query.isBlank()) return "Enter a query first."
        val normalized = query.removeSuffix(";").trim()
        if (';' in normalized) return "Only one query can run at a time."
        if (!allowedQuery.matches(normalized)) {
            return "Read-only mode allows SELECT, SHOW, DESCRIBE, DESC, and EXPLAIN."
        }
        if (blockedPatterns.any { it.containsMatchIn(normalized) }) {
            return "This query can write files or lock data, so Linkora blocked it."
        }
        return null
    }
}
