package top.linkora.mobile.project

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectImportGuardTest {
    private val roots = mutableListOf<File>()

    @After
    fun cleanUp() {
        roots.forEach(File::deleteRecursively)
    }

    @Test
    fun `rejects traversal and separator names`() {
        val root = temporaryDirectory()
        val guard = ProjectImportGuard(root, availableBytes = 1_000)

        listOf(".", "..", "nested/file", "nested\\file").forEach { name ->
            assertThrows(IllegalStateException::class.java) {
                guard.resolveChild(root, name)
            }
        }
    }

    @Test
    fun `rejects a parent outside the workspace`() {
        val root = temporaryDirectory()
        val outside = temporaryDirectory()
        val guard = ProjectImportGuard(root, availableBytes = 1_000)

        assertThrows(IllegalStateException::class.java) {
            guard.resolveChild(outside, "index.js")
        }
    }

    @Test
    fun `enforces entry count`() {
        val root = temporaryDirectory()
        val guard = ProjectImportGuard(
            root,
            availableBytes = 1_000,
            limits = ProjectImportLimits(
                maxEntries = 1,
                maxFileBytes = 100,
                maxProjectBytes = 100,
                reservedFreeBytes = 0,
            ),
        )

        assertEquals(File(root, "one.js").canonicalFile, guard.resolveChild(root, "one.js"))
        assertThrows(IllegalStateException::class.java) {
            guard.resolveChild(root, "two.js")
        }
    }

    @Test
    fun `enforces per-file project and free-space limits`() {
        val root = temporaryDirectory()
        val limits = ProjectImportLimits(
            maxEntries = 10,
            maxFileBytes = 4,
            maxProjectBytes = 6,
            reservedFreeBytes = 2,
        )
        val guard = ProjectImportGuard(root, availableBytes = 8, limits = limits)

        guard.copyFile(
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
            output = File(root, "one.bin"),
            declaredBytes = 4,
            displayPath = "one.bin",
        )
        assertThrows(IllegalStateException::class.java) {
            guard.copyFile(
                input = ByteArrayInputStream(byteArrayOf(5, 6, 7)),
                output = File(root, "two.bin"),
                declaredBytes = 3,
                displayPath = "two.bin",
            )
        }
    }

    @Test
    fun `copies a file within the configured limits`() {
        val root = temporaryDirectory()
        val bytes = "Linkora".toByteArray()
        val output = File(root, "index.txt")
        val guard = ProjectImportGuard(
            root,
            availableBytes = 100,
            limits = ProjectImportLimits(
                maxEntries = 10,
                maxFileBytes = 20,
                maxProjectBytes = 50,
                reservedFreeBytes = 10,
            ),
        )

        guard.copyFile(ByteArrayInputStream(bytes), output, bytes.size.toLong(), "index.txt")

        assertArrayEquals(bytes, output.readBytes())
    }

    private fun temporaryDirectory(): File = Files.createTempDirectory("linkora-import-test").toFile()
        .also(roots::add)
}
