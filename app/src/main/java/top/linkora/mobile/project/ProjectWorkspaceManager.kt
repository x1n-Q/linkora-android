package top.linkora.mobile.project

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.json.JSONObject

class ProjectWorkspaceManager(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun restoreProject(): ProjectWorkspace = prepareProject(activeKind())

    fun prepareProject(kind: ProjectKind): ProjectWorkspace {
        val definition = definitionFor(kind)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportToDownloads(definition)
        } else {
            exportToAppDocuments(definition)
        }

        preferences.edit().putString(ACTIVE_KIND_KEY, kind.id).apply()
        val workspace = selectedTreeUri(kind)?.let { uri ->
            runCatching { buildTreeWorkspace(uri, kind) }.getOrElse {
                clearSelectedTree(kind)
                null
            }
        } ?: starterWorkspace(definition)
        activateLocalOrigin(kind)
        return workspace
    }

    fun selectProjectTree(uri: Uri): ProjectWorkspace {
        require(uri.scheme == "content") { "Choose a folder from Android Files." }
        val kind = activeKind()
        preferences.edit().remove(launchCommandKey(kind)).apply()
        val workspace = buildTreeWorkspace(uri, kind)
        preferences.edit().putString(selectedTreeKey(kind), uri.toString()).apply()
        activateLocalOrigin(kind)
        return workspace
    }

    fun activeKind(): ProjectKind = ProjectKind.fromId(
        preferences.getString(ACTIVE_KIND_KEY, ProjectKind.Static.id),
    )

    fun updateLaunchCommand(value: String): ProjectWorkspace {
        val kind = activeKind()
        require(kind != ProjectKind.Static) { "Static projects do not use a start command." }
        val command = ProjectCommandParser.parse(value, kind).display()
        preferences.edit().putString(launchCommandKey(kind), command).apply()
        return prepareProject(kind)
    }

    fun prepareNodeLaunch(): NodeLaunchConfig {
        check(activeKind() == ProjectKind.Node) { "Choose a Node.js project first." }
        val runtimeDirectory = nodeRuntimeProjectDirectory().apply {
            check(mkdirs() || isDirectory) {
                "The private Node.js workspace could not be created."
            }
        }

        val selectedTree = selectedTreeUri(ProjectKind.Node)
        val importedFiles = if (selectedTree != null) {
            val rootId = DocumentsContract.getTreeDocumentId(selectedTree)
            val importGuard = ProjectImportGuard(runtimeDirectory)
            buildSet {
                copyTree(
                    treeUri = selectedTree,
                    parentId = rootId,
                    destination = runtimeDirectory,
                    depth = 0,
                    relativeParent = "",
                    importedFiles = this,
                    importGuard = importGuard,
                )
            }
        } else {
            copyStarterToRuntime(NODE_DEFINITION, runtimeDirectory)
        }
        updateImportedSourceManifest(runtimeDirectory, importedFiles)

        val inspection = inspectNodeDirectory(runtimeDirectory)
        val command = ProjectCommandParser.parse(
            storedLaunchCommand(ProjectKind.Node) ?: inspection.defaultCommand,
            ProjectKind.Node,
        )
        return NodeLaunchConfig(
            projectDirectory = runtimeDirectory,
            command = command,
        )
    }

    fun preparePhpLaunch(): PhpLaunchConfig {
        check(activeKind() == ProjectKind.Php) { "Choose a PHP project first." }
        val runtimeDirectory = phpRuntimeProjectDirectory().apply {
            check(mkdirs() || isDirectory) {
                "The private PHP workspace could not be created."
            }
        }

        val selectedTree = selectedTreeUri(ProjectKind.Php)
        val importedFiles = if (selectedTree != null) {
            val rootId = DocumentsContract.getTreeDocumentId(selectedTree)
            val importGuard = ProjectImportGuard(runtimeDirectory)
            buildSet {
                copyTree(
                    treeUri = selectedTree,
                    parentId = rootId,
                    destination = runtimeDirectory,
                    depth = 0,
                    relativeParent = "",
                    importedFiles = this,
                    importGuard = importGuard,
                )
            }
        } else {
            copyStarterToRuntime(PHP_DEFINITION, runtimeDirectory)
        }
        updateImportedSourceManifest(runtimeDirectory, importedFiles)

        val hasIndex = File(runtimeDirectory, PHP_ENTRY_POINT).isFile
        val hasArtisan = File(runtimeDirectory, "artisan").isFile &&
            File(runtimeDirectory, "public/index.php").isFile
        check(hasIndex || hasArtisan) { "No runnable PHP entry point was found after project sync." }
        val defaultCommand = if (hasArtisan) PHP_ARTISAN_COMMAND else PHP_SERVER_COMMAND
        val command = ProjectCommandParser.parse(
            storedLaunchCommand(ProjectKind.Php) ?: defaultCommand,
            ProjectKind.Php,
        )
        return PhpLaunchConfig(
            projectDirectory = runtimeDirectory,
            command = command,
        )
    }

    fun openProjectFile(requestPath: String): ProjectFile? {
        val safePath = requestPath
            .replace('\\', '/')
            .trimStart('/')
            .ifBlank { "index.html" }
        if (safePath.split('/').any { it.isBlank() || it == "." || it == ".." }) {
            return null
        }

        selectedTreeUri(ProjectKind.Static)?.let { treeUri ->
            return runCatching { openTreeFile(treeUri, safePath) }.getOrNull()
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openDownloadFile(safePath)
        } else {
            openLegacyFile(safePath)
        }
    }

    private fun activateLocalOrigin(kind: ProjectKind) {
        if (kind == ProjectKind.Static) {
            ProjectServerService.start(context)
        } else {
            context.stopService(Intent(context, ProjectServerService::class.java))
        }
    }

    private fun starterWorkspace(definition: ProjectDefinition): ProjectWorkspace {
        val inspection = when (definition.kind) {
            ProjectKind.Node -> NodeInspection(
                entryPoint = "server.js",
                commands = listOf("npm start", "node server.js"),
                defaultCommand = "npm start",
                hasDependencies = false,
                sqliteCompatibilityDrivers = emptyList(),
            )
            else -> null
        }
        return ProjectWorkspace(
            name = definition.displayName,
            visibleLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "Download/LinkoraProjects/${definition.slug}"
            } else {
                legacyProjectDirectory(definition).absolutePath
            },
            files = definition.files,
            localUrl = LOCAL_URL,
            isStarter = true,
            kind = definition.kind,
            entryPoint = inspection?.entryPoint ?: if (definition.kind == ProjectKind.Php) {
                PHP_ENTRY_POINT
            } else {
                null
            },
            launchCommand = when (definition.kind) {
                ProjectKind.Node -> storedLaunchCommand(definition.kind)
                    ?: inspection?.defaultCommand
                ProjectKind.Php -> storedLaunchCommand(definition.kind) ?: PHP_SERVER_COMMAND
                ProjectKind.Static -> null
            },
            availableCommands = when (definition.kind) {
                ProjectKind.Node -> inspection?.commands.orEmpty()
                ProjectKind.Php -> listOf(PHP_SERVER_COMMAND)
                ProjectKind.Static -> emptyList()
            },
            hasDependencies = inspection?.hasDependencies ?: false,
            sqliteCompatibilityDrivers = inspection?.sqliteCompatibilityDrivers.orEmpty(),
        )
    }

    private fun selectedTreeUri(kind: ProjectKind): Uri? {
        val stored = preferences.getString(selectedTreeKey(kind), null)
            ?: if (kind == ProjectKind.Static) {
                preferences.getString(LEGACY_SELECTED_TREE_KEY, null)
            } else {
                null
            }
        return stored?.let(Uri::parse)
    }

    private fun selectedTreeKey(kind: ProjectKind) = "selected_tree_uri_${kind.id}"

    private fun launchCommandKey(kind: ProjectKind) = "launch_command_${kind.id}"

    private fun storedLaunchCommand(kind: ProjectKind): String? = preferences
        .getString(launchCommandKey(kind), null)
        ?.takeIf(String::isNotBlank)

    private fun clearSelectedTree(kind: ProjectKind) {
        preferences.edit()
            .remove(selectedTreeKey(kind))
            .apply {
                if (kind == ProjectKind.Static) remove(LEGACY_SELECTED_TREE_KEY)
            }
            .apply()
    }

    private fun buildTreeWorkspace(treeUri: Uri, kind: ProjectKind): ProjectWorkspace {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val root = queryDocument(
            DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId),
        ) ?: error("The selected project folder is no longer available.")
        check(root.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            "Choose a project folder, not a single file."
        }

        val children = queryChildren(treeUri, root.id)
        val nodeInspection = when (kind) {
            ProjectKind.Static -> {
                check(children.any { it.name == "index.html" && !it.isDirectory }) {
                    "Choose a folder containing an index.html file."
                }
                null
            }

            ProjectKind.Node -> inspectNodeTree(treeUri)

            ProjectKind.Php -> {
                val hasIndex = children.any { it.name == PHP_ENTRY_POINT && !it.isDirectory }
                val hasArtisan = children.any { it.name == "artisan" && !it.isDirectory } &&
                    treeFileExists(treeUri, "public/index.php")
                check(hasIndex || hasArtisan) {
                    "Choose a PHP folder containing index.php or a Laravel artisan project."
                }
                null
            }
        }

        return ProjectWorkspace(
            name = root.name.ifBlank { "Local project" },
            visibleLocation = treeLocation(treeUri),
            files = children
                .sortedWith(compareBy<DocumentEntry>({ !it.isDirectory }, { it.name.lowercase() }))
                .take(MAX_VISIBLE_FILES)
                .map { if (it.isDirectory) "${it.name}/" else it.name },
            localUrl = LOCAL_URL,
            isStarter = false,
            kind = kind,
            entryPoint = nodeInspection?.entryPoint ?: if (kind == ProjectKind.Php) {
                PHP_ENTRY_POINT
            } else {
                null
            },
            launchCommand = when (kind) {
                ProjectKind.Node -> storedLaunchCommand(kind) ?: nodeInspection?.defaultCommand
                ProjectKind.Php -> storedLaunchCommand(kind) ?: if (
                    children.any { it.name == "artisan" && !it.isDirectory }
                ) {
                    PHP_ARTISAN_COMMAND
                } else {
                    PHP_SERVER_COMMAND
                }
                ProjectKind.Static -> null
            },
            availableCommands = when (kind) {
                ProjectKind.Node -> nodeInspection?.commands.orEmpty()
                ProjectKind.Php -> buildList {
                    add(PHP_SERVER_COMMAND)
                    if (children.any { it.name == "artisan" && !it.isDirectory }) {
                        add(PHP_ARTISAN_COMMAND)
                    }
                }
                ProjectKind.Static -> emptyList()
            },
            hasDependencies = nodeInspection?.hasDependencies ?: false,
            sqliteCompatibilityDrivers = nodeInspection?.sqliteCompatibilityDrivers.orEmpty(),
        )
    }

    private fun inspectNodeTree(treeUri: Uri): NodeInspection {
        val manifest = readTreeText(treeUri, "package.json")?.let(::parseManifest)
        val candidates = buildList {
            manifest?.startEntry?.let(::add)
            manifest?.main?.let(::add)
            addAll(NODE_ENTRY_CANDIDATES)
        }
        val entryPoint = candidates
            .mapNotNull(::safeRelativePath)
            .distinct()
            .firstOrNull { treeFileExists(treeUri, it) }
        check(entryPoint != null || manifest?.scripts?.isNotEmpty() == true) {
                "Choose a Node.js folder containing package.json and a runnable " +
                    "script, server.js, index.js, app.js, .mjs, or .cjs entry file."
        }
        val commands = nodeCommands(manifest, entryPoint)
        return NodeInspection(
            entryPoint = entryPoint,
            commands = commands,
            defaultCommand = commands.first(),
            hasDependencies = manifest?.hasDependencies == true,
            sqliteCompatibilityDrivers = manifest?.sqliteCompatibilityDrivers.orEmpty(),
        )
    }

    private fun inspectNodeDirectory(directory: File): NodeInspection {
        val manifest = File(directory, "package.json")
            .takeIf(File::isFile)
            ?.readText()
            ?.let(::parseManifest)
        val candidates = buildList {
            manifest?.startEntry?.let(::add)
            manifest?.main?.let(::add)
            addAll(NODE_ENTRY_CANDIDATES)
        }
        val root = directory.canonicalFile
        val entryPoint = candidates
            .mapNotNull(::safeRelativePath)
            .distinct()
            .firstOrNull { candidate ->
                val file = File(root, candidate).canonicalFile
                file.isFile && file.path.startsWith("${root.path}${File.separator}")
            }
        check(entryPoint != null || manifest?.scripts?.isNotEmpty() == true) {
            "No runnable Node.js script or entry file was found after project sync."
        }
        val commands = nodeCommands(manifest, entryPoint)
        return NodeInspection(
            entryPoint = entryPoint,
            commands = commands,
            defaultCommand = commands.first(),
            hasDependencies = manifest?.hasDependencies == true,
            sqliteCompatibilityDrivers = manifest?.sqliteCompatibilityDrivers.orEmpty(),
        )
    }

    private fun parseManifest(contents: String): NodeManifest {
        val manifest = runCatching { JSONObject(contents) }
            .getOrElse { error("package.json is not valid JSON.") }
        val scriptsObject = manifest.optJSONObject("scripts")
        val scripts = scriptsObject?.keys()?.asSequence()
            ?.filter { it.isNotBlank() && it.none(Char::isWhitespace) }
            ?.toList()
            ?.sortedWith(compareBy<String> { it != "start" }.thenBy { it })
            .orEmpty()
        val startScript = scriptsObject?.optString("start")
        val startEntry = startScript
            ?.let { NODE_START_PATTERN.matchEntire(it)?.groupValues?.get(1) }
        val dependencyGroups = listOfNotNull(
            manifest.optJSONObject("dependencies"),
            manifest.optJSONObject("devDependencies"),
            manifest.optJSONObject("optionalDependencies"),
        )
        return NodeManifest(
            main = manifest.optString("main").takeIf(String::isNotBlank),
            startEntry = startEntry,
            scripts = scripts,
            hasDependencies = dependencyGroups.any { it.length() > 0 },
            sqliteCompatibilityDrivers = SQLITE_COMPATIBILITY_DRIVERS.filter { driver ->
                dependencyGroups.any { it.has(driver) }
            },
        )
    }

    private fun nodeCommands(manifest: NodeManifest?, entryPoint: String?): List<String> =
        buildList {
            manifest?.scripts.orEmpty().forEach { script ->
                add(if (script == "start") "npm start" else "npm run $script")
            }
            entryPoint?.let { path ->
                add(ProjectCommand(ProjectExecutable.Node, listOf(path)).display())
            }
        }.distinct()

    private fun safeRelativePath(path: String): String? {
        val normalized = path.replace('\\', '/').removePrefix("./")
        if (normalized.isBlank() || normalized.startsWith('/')) return null
        if (normalized.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        return normalized.takeIf {
            it.endsWith(".js", ignoreCase = true) ||
                it.endsWith(".mjs", ignoreCase = true) ||
                it.endsWith(".cjs", ignoreCase = true)
        }
    }

    private fun readTreeText(treeUri: Uri, path: String): String? =
        findTreeFile(treeUri, path)?.let { entry ->
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.id)
            context.contentResolver.openInputStream(documentUri)?.bufferedReader()?.use { it.readText() }
        }

    private fun treeFileExists(treeUri: Uri, path: String): Boolean =
        findTreeFile(treeUri, path)?.isDirectory == false

    private fun findTreeFile(treeUri: Uri, path: String): DocumentEntry? {
        var parentId = DocumentsContract.getTreeDocumentId(treeUri)
        val segments = path.split('/')
        var selected: DocumentEntry? = null
        segments.forEachIndexed { index, segment ->
            val entry = queryChildren(treeUri, parentId).firstOrNull { it.name == segment }
                ?: return null
            selected = entry
            if (index < segments.lastIndex) {
                if (!entry.isDirectory) return null
                parentId = entry.id
            }
        }
        return selected
    }

    private fun openTreeFile(treeUri: Uri, path: String): ProjectFile? {
        val file = findTreeFile(treeUri, path)?.takeUnless(DocumentEntry::isDirectory) ?: return null
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, file.id)
        val stream = context.contentResolver.openInputStream(documentUri) ?: return null
        return ProjectFile(
            input = stream,
            length = file.size,
            mimeType = file.mimeType.takeUnless { it == "application/octet-stream" }
                ?: file.name.mimeType(),
        )
    }

    private fun copyTree(
        treeUri: Uri,
        parentId: String,
        destination: File,
        depth: Int,
        relativeParent: String,
        importedFiles: MutableSet<String>,
        importGuard: ProjectImportGuard,
    ) {
        check(depth <= MAX_TREE_DEPTH) { "The project folder is nested too deeply." }
        importGuard.requireWorkspaceDirectory(destination)
        check(destination.mkdirs() || destination.isDirectory) {
            "The private runtime workspace could not be created."
        }
        queryChildren(treeUri, parentId).forEach { entry ->
            if (entry.name in IGNORED_NODE_DIRECTORIES) return@forEach
            val relativePath = if (relativeParent.isBlank()) {
                entry.name
            } else {
                "$relativeParent/${entry.name}"
            }
            val output = importGuard.resolveChild(destination, entry.name)
            if (entry.isDirectory) {
                if (output.isFile) check(output.delete()) {
                    "A previous project file could not be replaced with a directory."
                }
                copyTree(
                    treeUri = treeUri,
                    parentId = entry.id,
                    destination = output,
                    depth = depth + 1,
                    relativeParent = relativePath,
                    importedFiles = importedFiles,
                    importGuard = importGuard,
                )
            } else {
                if (output.isDirectory) check(output.deleteRecursively()) {
                    "A previous project directory could not be replaced with a file."
                }
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.id)
                val temporary = File.createTempFile(".linkora-import-", ".tmp", destination)
                try {
                    checkNotNull(context.contentResolver.openInputStream(documentUri)).use { input ->
                        importGuard.copyFile(
                            input = input,
                            output = temporary,
                            declaredBytes = entry.size,
                            displayPath = relativePath,
                        )
                    }
                    Files.move(
                        temporary.toPath(),
                        output.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } finally {
                    temporary.delete()
                }
                importedFiles += relativePath
            }
        }
    }

    private fun copyStarterToRuntime(
        definition: ProjectDefinition,
        destination: File,
    ): Set<String> = buildSet {
        definition.files.forEach { relativePath ->
            val output = File(destination, relativePath)
            if (output.isDirectory) check(output.deleteRecursively()) {
                "A previous project directory could not be replaced with a starter file."
            }
            check(output.parentFile?.mkdirs() != false || output.parentFile?.isDirectory == true)
            val input = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val parent = relativePath.substringBeforeLast('/', "")
                val fileName = relativePath.substringAfterLast('/')
                val downloadPath = definition.downloadPath +
                    if (parent.isBlank()) "" else "$parent/"
                val uri = checkNotNull(findDownloadUri(fileName, downloadPath)) {
                    "The editable starter file $relativePath is missing from Downloads."
                }
                checkNotNull(context.contentResolver.openInputStream(uri))
            } else {
                File(legacyProjectDirectory(definition), relativePath).inputStream()
            }
            input.use { source ->
                output.outputStream().use { stream -> source.copyTo(stream) }
            }
            add(relativePath)
        }
    }

    private fun updateImportedSourceManifest(
        runtimeDirectory: File,
        importedFiles: Set<String>,
    ) {
        val metadataDirectory = File(runtimeDirectory, LINKORA_RUNTIME_DIRECTORY).apply {
            check(mkdirs() || isDirectory) { "Linkora runtime metadata could not be created." }
        }
        val manifest = File(metadataDirectory, IMPORTED_SOURCE_MANIFEST)
        val previousFiles = manifest
            .takeIf(File::isFile)
            ?.readLines()
            .orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
        val runtimeRoot = runtimeDirectory.canonicalFile

        (previousFiles - importedFiles).forEach { relativePath ->
            val staleFile = File(runtimeRoot, relativePath).canonicalFile
            val insideRuntime = staleFile.path.startsWith("${runtimeRoot.path}${File.separator}")
            if (insideRuntime && staleFile.isFile) {
                check(staleFile.delete()) { "A removed project source file could not be cleared." }
            }
        }

        manifest.writeText(
            importedFiles.sorted().joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun nodeRuntimeProjectDirectory(): File {
        val sourceKey = selectedTreeUri(ProjectKind.Node)?.toString() ?: "linkora-node-starter"
        val projectId = MessageDigest.getInstance("SHA-256")
            .digest(sourceKey.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
            .take(24)
        val destination = File(context.filesDir, "$NODE_RUNTIME_PROJECT_ROOT/$projectId")
        migrateLegacyRuntime(destination)
        return destination
    }

    private fun phpRuntimeProjectDirectory(): File {
        val sourceKey = selectedTreeUri(ProjectKind.Php)?.toString() ?: "linkora-php-starter"
        val projectId = MessageDigest.getInstance("SHA-256")
            .digest(sourceKey.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
            .take(24)
        return File(context.filesDir, "$PHP_RUNTIME_PROJECT_ROOT/$projectId")
    }

    private fun migrateLegacyRuntime(destination: File) {
        if (preferences.getBoolean(LEGACY_RUNTIME_MIGRATED_KEY, false)) return
        val legacyDirectory = File(context.filesDir, LEGACY_NODE_RUNTIME_PROJECT_PATH)
        if (legacyDirectory.isDirectory && !destination.exists()) {
            val moved = legacyDirectory.renameTo(destination)
            if (!moved) {
                check(legacyDirectory.copyRecursively(destination, overwrite = true)) {
                    "The existing Node.js workspace could not be migrated."
                }
                check(legacyDirectory.deleteRecursively()) {
                    "The previous Node.js workspace could not be removed after migration."
                }
            }
        }
        preferences.edit().putBoolean(LEGACY_RUNTIME_MIGRATED_KEY, true).apply()
    }

    private fun queryDocument(documentUri: Uri): DocumentEntry? =
        context.contentResolver.query(documentUri, DOCUMENT_COLUMNS, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            DocumentEntry(
                id = cursor.getString(0),
                name = cursor.getString(1).orEmpty(),
                mimeType = cursor.getString(2).orEmpty(),
                size = if (cursor.isNull(3)) -1L else cursor.getLong(3),
            )
        }

    private fun queryChildren(treeUri: Uri, parentId: String): List<DocumentEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        return context.contentResolver.query(childrenUri, DOCUMENT_COLUMNS, null, null, null)
            ?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            DocumentEntry(
                                id = cursor.getString(0),
                                name = cursor.getString(1).orEmpty(),
                                mimeType = cursor.getString(2).orEmpty(),
                                size = if (cursor.isNull(3)) -1L else cursor.getLong(3),
                            ),
                        )
                    }
                }
            }.orEmpty()
    }

    private fun treeLocation(treeUri: Uri): String {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        return when {
            documentId.startsWith("primary:") ->
                "Internal storage/${documentId.removePrefix("primary:")}"
            documentId.contains(':') -> documentId.substringAfter(':')
            else -> documentId
        }.trimEnd('/')
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToDownloads(definition: ProjectDefinition) {
        definition.files.forEach { relativeFile ->
            val parent = relativeFile.substringBeforeLast('/', "")
            val fileName = relativeFile.substringAfterLast('/')
            val relativePath = definition.downloadPath + if (parent.isBlank()) "" else "$parent/"
            if (findDownloadUri(fileName, relativePath) != null) return@forEach

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, fileName.mimeType())
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = checkNotNull(
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
            ) { "The starter project could not be created in Downloads." }

            try {
                context.assets.open("${definition.assetRoot}/$relativeFile").use { source ->
                    checkNotNull(context.contentResolver.openOutputStream(uri, "w")).use { output ->
                        source.copyTo(output)
                    }
                }
                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null,
                )
            } catch (error: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun openDownloadFile(path: String): ProjectFile? {
        val parent = path.substringBeforeLast('/', "")
        val fileName = path.substringAfterLast('/')
        val relativePath = STATIC_DEFINITION.downloadPath + if (parent.isBlank()) "" else "$parent/"
        val uri = findDownloadUri(fileName, relativePath) ?: return null
        val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r") ?: return null
        val stream = context.contentResolver.openInputStream(uri) ?: run {
            descriptor.close()
            return null
        }
        return ProjectFile(
            input = object : FilterInputStream(stream) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        descriptor.close()
                    }
                }
            },
            length = descriptor.length,
            mimeType = fileName.mimeType(),
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findDownloadUri(fileName: String, relativePath: String): Uri? =
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(fileName, relativePath),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0),
                )
            } else {
                null
            }
        }

    private fun exportToAppDocuments(definition: ProjectDefinition) {
        val directory = legacyProjectDirectory(definition).apply {
            check(mkdirs() || isDirectory) { "The project folder could not be created." }
        }
        definition.files.forEach { relativePath ->
            val destination = File(directory, relativePath)
            check(destination.parentFile?.mkdirs() != false || destination.parentFile?.isDirectory == true)
            if (!destination.exists()) {
                context.assets.open("${definition.assetRoot}/$relativePath").use { source ->
                    destination.outputStream().use { output -> source.copyTo(output) }
                }
            }
        }
    }

    private fun openLegacyFile(path: String): ProjectFile? {
        val root = legacyProjectDirectory(STATIC_DEFINITION).canonicalFile
        val file = File(root, path).canonicalFile
        if (!file.path.startsWith("${root.path}${File.separator}") || !file.isFile) return null
        return ProjectFile(
            input = file.inputStream(),
            length = file.length(),
            mimeType = file.name.mimeType(),
        )
    }

    private fun legacyProjectDirectory(definition: ProjectDefinition): File {
        val root = checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS))
        return File(root, "LinkoraProjects/${definition.slug}")
    }

    private fun definitionFor(kind: ProjectKind): ProjectDefinition = when (kind) {
        ProjectKind.Static -> STATIC_DEFINITION
        ProjectKind.Node -> NODE_DEFINITION
        ProjectKind.Php -> PHP_DEFINITION
    }

    private fun String.mimeType(): String = when (substringAfterLast('.').lowercase()) {
        "html", "htm" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js", "mjs", "cjs" -> "text/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "php" -> "application/x-httpd-php"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "txt" -> "text/plain; charset=utf-8"
        else -> "application/octet-stream"
    }

    companion object {
        const val LOCAL_PORT = 8787
        const val LOCAL_URL = "http://127.0.0.1:$LOCAL_PORT"

        private val STATIC_DEFINITION = ProjectDefinition(
            kind = ProjectKind.Static,
            slug = "linkora-starter",
            displayName = "Linkora Starter",
            assetRoot = "starter-site",
            files = listOf("index.html", "styles.css", "app.js", "linkora-mark.svg"),
        )
        private val NODE_DEFINITION = ProjectDefinition(
            kind = ProjectKind.Node,
            slug = "linkora-node-sample",
            displayName = "Linkora Node Sample",
            assetRoot = "starter-node",
            files = listOf(
                "package.json",
                "server.js",
                "public/index.html",
                "public/styles.css",
                "public/app.js",
                "public/linkora-mark.svg",
            ),
        )
        private val PHP_DEFINITION = ProjectDefinition(
            kind = ProjectKind.Php,
            slug = "linkora-php-sample",
            displayName = "Linkora PHP Sample",
            assetRoot = "starter-php",
            files = listOf("index.php", "styles.css"),
        )
        private val NODE_ENTRY_CANDIDATES = listOf(
            "server.js",
            "server.mjs",
            "server.cjs",
            "index.js",
            "index.mjs",
            "index.cjs",
            "app.js",
            "app.mjs",
            "app.cjs",
        )
        private val NODE_START_PATTERN = Regex("^\\s*node\\s+([A-Za-z0-9_./-]+)\\s*$")
        private val IGNORED_NODE_DIRECTORIES = setOf("node_modules", ".git", ".linkora")
        private val SQLITE_COMPATIBILITY_DRIVERS = listOf("better-sqlite3", "sqlite3")
        private const val NODE_RUNTIME_PROJECT_ROOT = "node/projects"
        private const val PHP_RUNTIME_PROJECT_ROOT = "php/projects"
        private const val PHP_ENTRY_POINT = "index.php"
        private const val PHP_SERVER_COMMAND = "php -S {HOST}:{PORT} -t ."
        private const val PHP_ARTISAN_COMMAND =
            "php artisan serve --host={HOST} --port={PORT}"
        private const val LEGACY_NODE_RUNTIME_PROJECT_PATH = "$NODE_RUNTIME_PROJECT_ROOT/active"
        private const val LINKORA_RUNTIME_DIRECTORY = ".linkora"
        private const val IMPORTED_SOURCE_MANIFEST = "imported-source-files.txt"
        private const val PREFERENCES = "project_workspace"
        private const val ACTIVE_KIND_KEY = "active_project_kind"
        private const val LEGACY_SELECTED_TREE_KEY = "selected_tree_uri"
        private const val LEGACY_RUNTIME_MIGRATED_KEY = "legacy_node_runtime_migrated"
        private const val MAX_VISIBLE_FILES = 24
        private const val MAX_TREE_DEPTH = 32
        private val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}

private data class ProjectDefinition(
    val kind: ProjectKind,
    val slug: String,
    val displayName: String,
    val assetRoot: String,
    val files: List<String>,
) {
    val downloadPath: String
        get() = "Download/LinkoraProjects/$slug/"
}

private data class NodeManifest(
    val main: String?,
    val startEntry: String?,
    val scripts: List<String>,
    val hasDependencies: Boolean,
    val sqliteCompatibilityDrivers: List<String>,
)

private data class NodeInspection(
    val entryPoint: String?,
    val commands: List<String>,
    val defaultCommand: String,
    val hasDependencies: Boolean,
    val sqliteCompatibilityDrivers: List<String>,
)

private data class DocumentEntry(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
) {
    val isDirectory: Boolean
        get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

class ProjectFile(
    val input: InputStream,
    val length: Long,
    val mimeType: String,
) : AutoCloseable {
    override fun close() = input.close()
}
