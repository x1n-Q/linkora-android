package top.linkora.mobile.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

data class RuntimePackSpec(
    val id: String,
    val displayName: String,
    val packageName: String,
    val protocolVersion: Int,
    val runtimeVersion: String,
    val executableName: String,
    val supportedAbis: Set<String>,
    val nativeLibraryHashes: Map<String, String>,
    val assetHashes: Map<String, String> = emptyMap(),
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

        val applicationInfo = try {
            getApplicationInfo(spec.packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return RuntimePackInspection(
                spec = spec,
                state = RuntimePackState.Missing,
                message = "${spec.displayName} is not installed.",
            )
        }

        if (!applicationInfo.enabled) {
            return RuntimePackInspection(
                spec = spec,
                state = RuntimePackState.Incompatible,
                message = "${spec.displayName} is disabled in Android settings.",
            )
        }

        return runCatching {
            validateMetadata(applicationInfo, spec)
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

            val packageInfo = getPackageInfo(spec.packageName)
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

    private fun validateMetadata(applicationInfo: ApplicationInfo, spec: RuntimePackSpec) {
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
        check(metadata.getString(METADATA_ABI) in spec.supportedAbis) {
            "The runtime pack architecture metadata does not match."
        }
    }

    @Suppress("DEPRECATION")
    private fun getApplicationInfo(packageName: String): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(packageName: String) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            packageManager.getPackageInfo(packageName, 0)
        }

    private fun File.sha256(): String = inputStream().buffered().use(::sha256)

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
    }
}
