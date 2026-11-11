package top.linkora.mobile.database

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReadOnlyQueryPolicyTest {
    @Test
    fun acceptsSupportedReadOnlyQueries() {
        listOf(
            "SELECT * FROM users",
            "show tables;",
            "DESCRIBE projects",
            "desc projects",
            "EXPLAIN SELECT * FROM projects",
        ).forEach { query ->
            assertNull(query, ReadOnlyQueryPolicy.validate(query))
        }
    }

    @Test
    fun rejectsWriteAndMultipleQueries() {
        listOf(
            "INSERT INTO users(name) VALUES ('Daniel')",
            "UPDATE users SET admin = 1",
            "DELETE FROM users",
            "SELECT 1; DROP TABLE users",
        ).forEach { query ->
            assertNotNull(query, ReadOnlyQueryPolicy.validate(query))
        }
    }

    @Test
    fun rejectsDangerousSelectForms() {
        listOf(
            "SELECT * FROM users FOR UPDATE",
            "SELECT * FROM users LOCK IN SHARE MODE",
            "SELECT secret INTO OUTFILE '/tmp/data' FROM users",
            "SELECT LOAD_FILE('/etc/passwd')",
        ).forEach { query ->
            assertNotNull(query, ReadOnlyQueryPolicy.validate(query))
        }
    }
}
