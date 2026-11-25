package top.linkora.mobile.project

import java.io.File
import java.io.InputStream

internal data class ProjectImportLimits(
    val maxEntries: Int = 20_000,
    val maxFileBytes: Long = 512L * 1024L * 1024L,
    val maxProjectBytes: Long = 1024L * 1024L * 1024L,
    val reservedFreeBytes: Long = 256L * 1024L * 1024L,
)

internal class ProjectImportGuard(
    root: File,
    availableBytes: Long = root.usableSpace,
    private val limits: ProjectImportLimits = ProjectImportLimits(),
) {
    private val canonicalRoot = root.canonicalFile
    private val rootPrefix = "${canonicalRoot.path}${File.separator}"
    private val startingAvailableBytes = availableBytes.coerceAtLeast(0L)
    private var importedEntries = 0
    private var importedBytes = 0L

    fun requireWorkspaceDirectory(directory: File) {
        val canonicalDirectory = directory.canonicalFile
        check(
            canonicalDirectory == canonicalRoot || canonicalDirectory.path.startsWith(rootPrefix),
        ) { "The project contains a path outside its private workspace." }
    }

    fun resolveChild(parent: File, name: String): File {
        check(
            name.isNotBlank() &&
                name != "." &&
                name != ".." &&
                '/' !in name &&
                '\\' !in name &&
                name.none(Char::isISOControl),
        ) { "The project contains an unsupported file name." }

        importedEntries += 1
        check(importedEntries <= limits.maxEntries) {
            "The project contains more than ${limits.maxEntries} files and folders."
        }

        val canonicalParent = parent.canonicalFile
        requireWorkspaceDirectory(canonicalParent)
        return File(canonicalParent, name).canonicalFile.also { child ->
            check(child.path.startsWith(rootPrefix)) {
                "The project contains a path outside its private workspace."
            }
        }
    }

    fun copyFile(
        input: InputStream,
        output: File,
        declaredBytes: Long,
        displayPath: String,
    ) {
        if (declaredBytes >= 0L) {
            check(declaredBytes <= limits.maxFileBytes) {
                "$displayPath is larger than the ${limits.maxFileBytes.toReadableSize()} file limit."
            }
            check(importedBytes + declaredBytes <= limits.maxProjectBytes) {
                "The project is larger than the ${limits.maxProjectBytes.toReadableSize()} import limit."
            }
            check(startingAvailableBytes - importedBytes - declaredBytes >= limits.reservedFreeBytes) {
                "There is not enough free storage to import this project safely."
            }
        }

        var fileBytes = 0L
        output.outputStream().buffered().use { destination ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue

                fileBytes += read
                check(fileBytes <= limits.maxFileBytes) {
                    "$displayPath is larger than the ${limits.maxFileBytes.toReadableSize()} file limit."
                }
                check(importedBytes + read <= limits.maxProjectBytes) {
                    "The project is larger than the ${limits.maxProjectBytes.toReadableSize()} import limit."
                }
                check(startingAvailableBytes - importedBytes - read >= limits.reservedFreeBytes) {
                    "There is not enough free storage to import this project safely."
                }

                destination.write(buffer, 0, read)
                importedBytes += read
            }
        }
    }

    private fun Long.toReadableSize(): String = when {
        this % (1024L * 1024L * 1024L) == 0L -> "${this / (1024L * 1024L * 1024L)} GB"
        this % (1024L * 1024L) == 0L -> "${this / (1024L * 1024L)} MB"
        else -> "$this bytes"
    }
}
