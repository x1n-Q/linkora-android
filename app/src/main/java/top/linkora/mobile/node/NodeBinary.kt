package top.linkora.mobile.node

import android.content.Context
import android.os.Build
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

object NodeBinary {
    const val VERSION = "24.22.1"
    private const val LIBRARY_NAME = "libnode_runtime.so"
    private const val ARM64_SHA256 =
        "c03a6a63832dc27dec14d6879a194242c7f2142301471c18d67354fe137d2b6a"

    fun resolve(context: Context): File {
        check(Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
            "The built-in Node.js runtime currently requires an ARM64 Android device."
        }
        val executable = File(context.applicationInfo.nativeLibraryDir, LIBRARY_NAME)
        check(executable.isFile) { "The bundled Node.js runtime is missing." }
        check(executable.sha256() == ARM64_SHA256) {
            "The bundled Node.js runtime failed its integrity check."
        }
        check(executable.canExecute()) {
            "Android did not install the Node.js runtime as executable code."
        }
        return executable
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
