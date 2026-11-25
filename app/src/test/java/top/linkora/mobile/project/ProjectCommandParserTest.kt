package top.linkora.mobile.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectCommandParserTest {
    @Test
    fun parsesNodePackageScript() {
        val command = ProjectCommandParser.parse(
            "npm run dev -- --host {HOST} --port {PORT}",
            ProjectKind.Node,
        )

        assertEquals(ProjectExecutable.Npm, command.executable)
        assertEquals(
            listOf("run", "dev", "--", "--host", "127.0.0.1", "--port", "8787"),
            command.argumentsFor("127.0.0.1", 8787),
        )
    }

    @Test
    fun keepsQuotedArgumentsTogether() {
        val command = ProjectCommandParser.parse(
            "php -S {HOST}:{PORT} -t \"public files\"",
            ProjectKind.Php,
        )

        assertEquals("public files", command.arguments.last())
    }

    @Test
    fun rejectsWrongRuntime() {
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCommandParser.parse("php index.php", ProjectKind.Node)
        }
    }
}
