package top.linkora.mobile.node

import android.content.Context
import java.io.File
import java.nio.file.Files
import top.linkora.mobile.runtime.RuntimePacks

object NodeBinary {
    const val VERSION = "24.22.1"

    data class Installation(
        val executable: File,
        val source: String,
    )

    fun resolve(context: Context): File = resolveInstallation(context).executable

    fun resolveInstallation(context: Context): Installation {
        val runtimePack = checkNotNull(RuntimePacks.inspectNode(context).installation) {
            "Install the compatible Node.js Runtime Pack before starting this project."
        }
        return Installation(
            executable = runtimePack.executable,
            source = "Verified Node.js Runtime Pack ${runtimePack.spec.runtimeVersion}",
        )
    }

    fun prepareCommandDirectory(context: Context, executable: File): File {
        val directory = File(context.filesDir, "node/bin").apply {
            check(mkdirs() || isDirectory) { "The private Node.js command directory is unavailable." }
        }
        val command = File(directory, "node")
        Files.deleteIfExists(command.toPath())
        Files.createSymbolicLink(command.toPath(), executable.toPath())
        check(command.canonicalFile == executable.canonicalFile) {
            "The private Node.js command link could not be verified."
        }
        return directory
    }
}
