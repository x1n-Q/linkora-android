package top.linkora.mobile.mariadb

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream
import top.linkora.mobile.runtime.RuntimePackInstallation

data class MariaDbLayout(
    val root: File,
    val data: File,
    val share: File,
    val plugins: File,
    val temporary: File,
    val socket: File,
    val pidFile: File,
    val initializedMarker: File,
)

object MariaDbLayoutManager {
    private const val SHARE_ASSET = "mariadb/share.zip"

    fun prepare(context: Context, installation: RuntimePackInstallation): MariaDbLayout {
        val root = File(context.filesDir, "mariadb").apply { mkdirs() }
        val share = File(root, "share-${MariaDbBinary.VERSION}")
        val shareMarker = File(share, ".ready")
        if (!shareMarker.isFile) {
            check(share.deleteRecursively()) { "Old MariaDB support files could not be replaced." }
            check(share.mkdirs()) { "MariaDB support files could not be created." }
            installation.openAsset(SHARE_ASSET).use { input ->
                ZipInputStream(input.buffered()).use { archive ->
                    var entry = archive.nextEntry
                    while (entry != null) {
                        val destination = File(share, entry.name).canonicalFile
                        check(destination.path.startsWith("${share.canonicalPath}${File.separator}")) {
                            "MariaDB support files contain an unsafe path."
                        }
                        if (entry.isDirectory) {
                            check(destination.mkdirs() || destination.isDirectory)
                        } else {
                            check(
                                destination.parentFile?.let { parent ->
                                    parent.mkdirs() || parent.isDirectory
                                } != false,
                            )
                            destination.outputStream().buffered().use(archive::copyTo)
                        }
                        archive.closeEntry()
                        entry = archive.nextEntry
                    }
                }
            }
            shareMarker.writeText(MariaDbBinary.VERSION)
        }

        val temporary = File(context.cacheDir, "mariadb-tmp").apply { mkdirs() }
        return MariaDbLayout(
            root = root,
            data = File(root, "data").apply { mkdirs() },
            share = share,
            plugins = File(root, "plugins").apply { mkdirs() },
            temporary = temporary,
            socket = File(context.cacheDir, "mariadb.sock"),
            pidFile = File(context.cacheDir, "mariadb.pid"),
            initializedMarker = File(root, ".database-${MariaDbBinary.VERSION}-ready"),
        )
    }
}
