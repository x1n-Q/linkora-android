package top.linkora.mobile.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

data class RuntimePackSpec(
    val id: String,
    val displayName: String,
    val packageName: String,
    val protocolVersion: Int,
    val runtimeVersion: String,
    val executableName: String,
    val supportedAbis: Set<String>,
    val trustedSignerSha256: Set<String>,
    val nativeLibraryHashes: Map<String, String>,
    val assetHashes: Map<String, String> = emptyMap(),
    val dexHashes: Map<String, String> = emptyMap(),
)

enum class RuntimePackState {
    Checking,
    Missing,
    Ready,
    Incompatible,
    Invalid,
}

data class RuntimePackInspection(
    val spec: RuntimePackSpec,
    val state: RuntimePackState,
    val message: String,
    val installation: RuntimePackInstallation? = null,
)

class RuntimePackInstallation internal constructor(
    val spec: RuntimePackSpec,
    val executable: File,
    val versionCode: Long,
    val installedBySameSigner: Boolean,
    private val packageContext: Context,
) {
    fun openAsset(path: String): InputStream {
        check(path in spec.assetHashes) { "The runtime asset is not declared: $path" }
        return packageContext.assets.open(path)
    }
}

class RuntimePackManager(private val context: Context) {
    private val packageManager = context.packageManager

    fun inspect(spec: RuntimePackSpec): RuntimePackInspection {
        if (Build.SUPPORTED_ABIS.none(spec.supportedAbis::contains)) {
            return RuntimePackInspection(
                spec = spec,
                state = RuntimePackState.Incompatible,
                message = "${spec.displayName} does not support this device architecture.",
            )
        }

        val packageInfo = try {
            getPackageInfo(spec.packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return RuntimePackInspection(
                spec = spec,
                state = RuntimePackState.Missing,
                message = "${spec.displayName} is not installed.",
            )
        }
        val applicationInfo = packageInfo.applicationInfo
            ?: return RuntimePackInspection(
                spec = spec,
                state = RuntimePackState.Invalid,
                message = "${spec.displayName} has no application metadata.",
            )

        if (!applicationInfo.enabled) {
            return RuntimePackInspection(
                spec = spec,
                state = RuntimePackState.Incompatible,
                message = "${spec.displayName} is disabled in Android settings.",
            )
        }

        return runCatching {
            validatePackageShape(packageInfo)
            validateSigner(packageInfo, spec)
            validateMetadata(applicationInfo, spec)
            val installedArchive = File(applicationInfo.sourceDir)
            val trustedArchives = RuntimePackCatalog.release(spec.id)
                ?.artifacts
                ?.map(RuntimePackArtifact::archiveSha256)
                .orEmpty()
            check(installedArchive.isFile && installedArchive.sha256() in trustedArchives) {
                "The installed runtime APK is not an approved Linkora release."
            }
            val nativeDirectory = checkNotNull(applicationInfo.nativeLibraryDir)
                .let(::File)
                .canonicalFile
            val libraries = spec.nativeLibraryHashes.mapValues { (name, expectedHash) ->
                val library = File(nativeDirectory, name).canonicalFile
                check(library.path.startsWith("${nativeDirectory.path}${File.separator}")) {
                    "The runtime pack declared an unsafe native library path."
                }
                check(library.isFile) { "The runtime pack is missing $name." }
                check(library.sha256() == expectedHash) {
                    "The runtime pack failed the $name integrity check."
                }
                library
            }
            val executable = checkNotNull(libraries[spec.executableName]) {
                "The runtime executable is not declared in the trusted payload."
            }
            check(executable.canExecute()) {
                "Android did not install the runtime as executable code."
            }

            val packageContext = context.createPackageContext(
                spec.packageName,
                Context.CONTEXT_IGNORE_SECURITY,
            )
            spec.assetHashes.forEach { (path, expectedHash) ->
                val actualHash = packageContext.assets.open(path).use(::sha256)
                check(actualHash == expectedHash) {
                    "The runtime pack failed the $path integrity check."
                }
            }

            val installation = RuntimePackInstallation(
                spec = spec,
                executable = executable,
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                },
                installedBySameSigner = packageManager.checkSignatures(
                    context.packageName,
                    spec.packageName,
                ) == PackageManager.SIGNATURE_MATCH,
                packageContext = packageContext,
            )
            RuntimePackInspection(
                spec = spec,
                state = RuntimePackState.Ready,
                message = "${spec.displayName} ${spec.runtimeVersion} is ready.",
                installation = installation,
            )
        }.getOrElse { error ->
            RuntimePackInspection(
                spec = spec,
                state = RuntimePackState.Invalid,
                message = error.message ?: "The installed runtime pack is invalid.",
            )
        }
    }

    fun validateArchive(archive: File, artifact: RuntimePackArtifact) {
        check(archive.isFile) { "The downloaded runtime APK is missing." }
        check(archive.sha256() == artifact.archiveSha256) {
            "The downloaded runtime APK does not match the published release."
        }
        val spec = artifact.spec
        val packageInfo = checkNotNull(getArchivePackageInfo(archive)) {
            "Android could not read the downloaded runtime APK."
        }
        check(packageInfo.packageName == spec.packageName) {
            "The downloaded APK is not ${spec.displayName}."
        }
        val applicationInfo = checkNotNull(packageInfo.applicationInfo) {
            "The downloaded runtime APK has no application metadata."
        }
        validatePackageShape(packageInfo)
        validateSigner(packageInfo, spec)
        validateMetadata(applicationInfo, spec, artifact.abi)
        val abi = applicationInfo.metaData.getString(METADATA_ABI)

        ZipFile(archive).use { apk ->
            val dexEntries = apk.entries().asSequence()
                .filter { entry -> entry.name.matches(DEX_ENTRY_PATTERN) }
                .associateBy { it.name }
            check(dexEntries.keys == spec.dexHashes.keys) {
                "The runtime APK contains unexpected Android bytecode."
            }
            spec.dexHashes.forEach { (name, expectedHash) ->
                val actualHash = apk.getInputStream(checkNotNull(dexEntries[name]))
                    .buffered()
                    .use(::sha256)
                check(actualHash == expectedHash) {
                    "The runtime APK failed the generated bytecode integrity check."
                }
            }
            spec.nativeLibraryHashes.forEach { (name, expectedHash) ->
                val entry = checkNotNull(apk.getEntry("lib/$abi/$name")) {
                    "The downloaded runtime APK is missing $name."
                }
                val actualHash = apk.getInputStream(entry).buffered().use(::sha256)
                check(actualHash == expectedHash) {
                    "The downloaded runtime APK failed the $name integrity check."
                }
            }
            spec.assetHashes.forEach { (path, expectedHash) ->
                val entry = checkNotNull(apk.getEntry("assets/$path")) {
                    "The downloaded runtime APK is missing $path."
                }
                val actualHash = apk.getInputStream(entry).buffered().use(::sha256)
                check(actualHash == expectedHash) {
                    "The downloaded runtime APK failed the $path integrity check."
                }
            }
        }
    }

    private fun validatePackageShape(packageInfo: PackageInfo) {
        val applicationInfo = checkNotNull(packageInfo.applicationInfo) {
            "The runtime pack has no application metadata."
        }
        check(applicationInfo.flags and ApplicationInfo.FLAG_HAS_CODE == 0) {
            "Runtime packs must be code-free containers."
        }
        check(packageInfo.activities.isNullOrEmpty()) {
            "Runtime packs must not declare activities."
        }
        check(packageInfo.services.isNullOrEmpty()) {
            "Runtime packs must not declare services."
        }
        check(packageInfo.receivers.isNullOrEmpty()) {
            "Runtime packs must not declare receivers."
        }
        check(packageInfo.providers.isNullOrEmpty()) {
            "Runtime packs must not declare providers."
        }
    }

    private fun validateSigner(packageInfo: PackageInfo, spec: RuntimePackSpec) {
        val signers = currentSigners(packageInfo)
        check(signers.size == 1) {
            "The runtime APK must have exactly one signing identity."
        }
        check(signers.single().sha256() in spec.trustedSignerSha256) {
            "The runtime APK is not signed by Linkora."
        }
    }

    @Suppress("DEPRECATION")
    private fun currentSigners(packageInfo: PackageInfo): List<Signature> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            packageInfo.signatures?.toList().orEmpty()
        }

    private fun validateMetadata(
        applicationInfo: ApplicationInfo,
        spec: RuntimePackSpec,
        expectedAbi: String? = null,
    ) {
        val metadata = checkNotNull(applicationInfo.metaData) {
            "The runtime pack metadata is missing."
        }
        check(metadata.getString(METADATA_ID) == spec.id) {
            "The runtime pack identity does not match."
        }
        check(metadata.getInt(METADATA_PROTOCOL, -1) == spec.protocolVersion) {
            "The runtime pack protocol is not compatible with this Linkora version."
        }
        check(metadata.getString(METADATA_VERSION) == spec.runtimeVersion) {
            "The runtime pack version is not compatible with this Linkora version."
        }
        check(metadata.getString(METADATA_EXECUTABLE) == spec.executableName) {
            "The runtime pack executable metadata does not match."
        }
        val abi = metadata.getString(METADATA_ABI)
        check(abi in spec.supportedAbis) {
            "The runtime pack architecture metadata does not match."
        }
        check(expectedAbi == null || abi == expectedAbi) {
            "The downloaded runtime pack is for a different device architecture."
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(packageName: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(packageInspectionFlags().toLong()),
            )
        } else {
            packageManager.getPackageInfo(packageName, packageInspectionFlags())
        }

    @Suppress("DEPRECATION")
    private fun getArchivePackageInfo(archive: File) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                archive.absolutePath,
                PackageManager.PackageInfoFlags.of(packageInspectionFlags().toLong()),
            )
        } else {
            packageManager.getPackageArchiveInfo(
                archive.absolutePath,
                packageInspectionFlags(),
            )
        }

    @Suppress("DEPRECATION")
    private fun packageInspectionFlags(): Int =
        PackageManager.GET_META_DATA or
            PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or
            PackageManager.MATCH_DISABLED_COMPONENTS or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }

    private fun File.sha256(): String = inputStream().buffered().use(::sha256)

    private fun Signature.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val METADATA_PREFIX = "top.linkora.runtime."
        private const val METADATA_ID = "${METADATA_PREFIX}ID"
        private const val METADATA_PROTOCOL = "${METADATA_PREFIX}PROTOCOL"
        private const val METADATA_VERSION = "${METADATA_PREFIX}VERSION"
        private const val METADATA_ABI = "${METADATA_PREFIX}ABI"
        private const val METADATA_EXECUTABLE = "${METADATA_PREFIX}EXECUTABLE"
        private val DEX_ENTRY_PATTERN = Regex("^classes(?:[0-9]+)?[.]dex$")
    }
}
