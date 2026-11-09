package top.linkora.mobile.node

import android.content.Context
import android.os.Build
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import top.linkora.mobile.runtime.RuntimePacks

object NodeBinary {
    const val VERSION = "24.22.1"
    private const val LIBRARY_NAME = "libnode_runtime.so"
    private const val ARM64_SHA256 =
        "c03a6a63832dc27dec14d6879a194242c7f2142301471c18d67354fe137d2b6a"

    data class Installation(
        val executable: File,
        val source: String,
    )

    fun resolve(context: Context): File = resolveInstallation(context).executable

    fun resolveInstallation(context: Context): Installation {
        check(Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
            "The Node.js runtime currently requires an ARM64 Android device."
        }
        val runtimePack = RuntimePacks.inspectNode(context).installation
        if (runtimePack != null) {
            return Installation(
                executable = runtimePack.executable,
                source = "Verified Node.js Runtime Pack ${runtimePack.spec.runtimeVersion}",
            )
        }

        val executable = File(context.applicationInfo.nativeLibraryDir, LIBRARY_NAME)
        check(executable.isFile) { "The bundled Node.js runtime is missing." }
        check(executable.sha256() == ARM64_SHA256) {
            "The bundled Node.js runtime failed its integrity check."
        }
        check(executable.canExecute()) {
            "Android did not install the Node.js runtime as executable code."
        }
        return Installation(
            executable = executable,
            source = "Built-in Node.js migration fallback $VERSION",
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

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
