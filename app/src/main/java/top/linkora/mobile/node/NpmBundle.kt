package top.linkora.mobile.node

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import top.linkora.mobile.runtime.RuntimePacks

object NpmBundle {
    const val VERSION = "11.19.1"
    private const val ASSET_PATH = "node/npm.zip"
    private const val ARCHIVE_SHA256 =
        "e8e8c951a85400bc5706564197dfbf4f1682b52dcc3cba1731041d6b5f49424e"

    fun resolveCli(context: Context): File {
        val installation = File(context.filesDir, "node/npm/$VERSION")
        val marker = File(installation, ".integrity")
        val cli = File(installation, "bin/npm-cli.js")
        if (cli.isFile && marker.readTextOrNull() == ARCHIVE_SHA256) return cli

        installation.deleteRecursively()
        check(installation.mkdirs()) { "The bundled npm directory could not be created." }

        val runtimePack = checkNotNull(RuntimePacks.inspectNode(context).installation) {
            "Install the compatible Node.js Runtime Pack before using npm."
        }
        val openArchive = { runtimePack.openAsset(ASSET_PATH) }
        val archiveHash = openArchive().use(::sha256)
        check(archiveHash == ARCHIVE_SHA256) { "The bundled npm archive failed its integrity check." }

        try {
            openArchive().use { archive ->
                ZipInputStream(archive.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val relativePath = entry.name.removePrefix("./")
                        if (relativePath.isBlank()) continue
                        val output = File(installation, relativePath).canonicalFile
                        check(output.path.startsWith("${installation.canonicalPath}${File.separator}")) {
                            "The bundled npm archive contains an unsafe path."
                        }
                        if (entry.isDirectory) {
                            check(output.mkdirs() || output.isDirectory)
                        } else {
                            check(output.parentFile?.mkdirs() != false || output.parentFile?.isDirectory == true)
                            output.outputStream().use { stream -> zip.copyTo(stream) }
                        }
                        zip.closeEntry()
                    }
                }
            }
            check(cli.isFile) { "The bundled npm CLI is missing." }
            marker.writeText(ARCHIVE_SHA256)
            return cli
        } catch (error: Exception) {
            installation.deleteRecursively()
            throw error
        }
    }

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun File.readTextOrNull(): String? =
        takeIf(File::isFile)?.runCatching { readText() }?.getOrNull()
}
