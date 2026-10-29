package top.linkora.mobile.node

import android.content.Context
import java.io.File
import java.nio.file.Files
import org.json.JSONObject

class NodeCompatibilityManager(private val context: Context) {
    fun detectDrivers(projectDirectory: File): List<String> {
        val packageFile = File(projectDirectory, PACKAGE_FILE)
        if (!packageFile.isFile) return emptyList()
        val manifest = JSONObject(packageFile.readText())
        val dependencyGroups = DEPENDENCY_GROUPS.mapNotNull(manifest::optJSONObject)
        return SUPPORTED_DRIVERS.filter { driver ->
            dependencyGroups.any { dependencies -> dependencies.has(driver) }
        }
    }

    fun ensureProjectAdapters(projectDirectory: File): List<String> {
        val drivers = detectDrivers(projectDirectory)
        if (drivers.isEmpty()) return emptyList()
        val nodeModules = File(projectDirectory, "node_modules").apply {
            check(mkdirs() || isDirectory) { "The Node.js modules directory could not be created." }
        }
        drivers.forEach { driver ->
            val source = prepareAdapterSource(driver)
            val destination = File(nodeModules, driver)
            deleteAdapterDestination(destination, driver)
            check(source.copyRecursively(destination, overwrite = true)) {
                "The Linkora $driver compatibility adapter could not be installed."
            }
        }
        return drivers
    }

    fun prepareDependencyInstall(projectDirectory: File): InstallSession {
        val drivers = detectDrivers(projectDirectory)
        if (drivers.isEmpty()) return InstallSession.empty()

        val packageFile = File(projectDirectory, PACKAGE_FILE)
        val originalPackage = packageFile.readBytes()
        val lockFiles = LOCK_FILES.associate { name ->
            val file = File(projectDirectory, name)
            file to file.takeIf(File::isFile)?.readBytes()
        }
        val restoreProjectFiles = {
            packageFile.writeBytes(originalPackage)
            lockFiles.forEach { (file, contents) ->
                if (contents == null) {
                    if (file.exists()) check(file.delete()) {
                        "A temporary npm lock file could not be removed."
                    }
                } else {
                    file.writeBytes(contents)
                }
            }
        }
        try {
            lockFiles.keys.forEach { file ->
                if (file.exists()) check(file.delete()) {
                    "The npm lock file could not be prepared for compatibility installation."
                }
            }
            val manifest = JSONObject(originalPackage.decodeToString())
            val adapterSources = drivers.associateWith(::prepareAdapterSource)

            DEPENDENCY_GROUPS.forEach { groupName ->
                val dependencies = manifest.optJSONObject(groupName) ?: return@forEach
                drivers.forEach { driver ->
                    if (dependencies.has(driver)) {
                        dependencies.put(driver, "file:${adapterSources.getValue(driver).absolutePath}")
                    }
                }
            }
            packageFile.writeText("${manifest.toString(2)}\n")
        } catch (error: Throwable) {
            restoreProjectFiles()
            throw error
        }

        return InstallSession {
            restoreProjectFiles()
            ensureProjectAdapters(projectDirectory)
        }
    }

    private fun prepareAdapterSource(driver: String): File {
        check(driver in SUPPORTED_DRIVERS) { "Unsupported compatibility driver: $driver" }
        val destination = File(context.filesDir, "$ADAPTER_SOURCE_PATH/$driver")
        check(destination.deleteRecursively()) {
            "The previous $driver compatibility adapter could not be refreshed."
        }
        copyAssetTree("$ADAPTER_ASSET_PATH/$driver", destination)
        return destination
    }

    private fun deleteAdapterDestination(destination: File, driver: String) {
        if (Files.isSymbolicLink(destination.toPath())) {
            check(Files.deleteIfExists(destination.toPath())) {
                "The existing $driver package link could not be replaced."
            }
            return
        }
        check(destination.deleteRecursively()) {
            "The existing $driver package could not be replaced."
        }
    }

    private fun copyAssetTree(assetPath: String, destination: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            check(destination.parentFile?.mkdirs() != false || destination.parentFile?.isDirectory == true)
            context.assets.open(assetPath).use { input ->
                destination.outputStream().use(input::copyTo)
            }
            return
        }

        check(destination.mkdirs() || destination.isDirectory) {
            "The compatibility adapter directory could not be created."
        }
        children.forEach { child ->
            copyAssetTree("$assetPath/$child", File(destination, child))
        }
    }

    class InstallSession private constructor(private val restoreAction: (() -> Unit)?) {
        private var restored = false

        fun restore() {
            if (restored) return
            restored = true
            restoreAction?.invoke()
        }

        companion object {
            fun empty() = InstallSession(null)
            operator fun invoke(restoreAction: () -> Unit) = InstallSession(restoreAction)
        }
    }

    companion object {
        val SUPPORTED_DRIVERS = listOf("better-sqlite3", "sqlite3")

        private val DEPENDENCY_GROUPS = listOf(
            "dependencies",
            "devDependencies",
            "optionalDependencies",
        )
        private val LOCK_FILES = listOf("package-lock.json", "npm-shrinkwrap.json")
        private const val PACKAGE_FILE = "package.json"
        private const val ADAPTER_ASSET_PATH = "node-compat"
        private const val ADAPTER_SOURCE_PATH = "node/compat/packages"
    }
}
