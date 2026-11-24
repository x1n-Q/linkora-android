package top.linkora.mobile.runtime

import android.os.Build

data class RuntimePackArtifact(
    val spec: RuntimePackSpec,
    val abi: String,
    val fileName: String,
    val downloadUrl: String,
    val estimatedBytes: Long,
)

data class RuntimePackRelease(
    val spec: RuntimePackSpec,
    val artifacts: List<RuntimePackArtifact>,
)

object RuntimePackCatalog {
    private const val RELEASE_BASE =
        "https://github.com/x1n-Q/linkora-runtime-packs/releases/latest/download"

    val Node = release(
        spec = RuntimePacks.Node,
        sizes = mapOf("arm64-v8a" to 26L * 1_024L * 1_024L),
    )

    val Php = release(
        spec = RuntimePacks.Php,
        sizes = mapOf("arm64-v8a" to 5L * 1_024L * 1_024L),
    )

    val MariaDb = release(
        spec = RuntimePacks.MariaDb,
        sizes = mapOf("arm64-v8a" to 14L * 1_024L * 1_024L),
    )

    val Connector = release(
        spec = RuntimePacks.Connector,
        sizes = mapOf("arm64-v8a" to 14L * 1_024L * 1_024L),
    )

    val releases = listOf(Node, Php, MariaDb, Connector)

    fun release(id: String): RuntimePackRelease? = releases.firstOrNull { it.spec.id == id }

    fun resolve(id: String): RuntimePackArtifact? {
        val release = release(id) ?: return null
        return Build.SUPPORTED_ABIS.firstNotNullOfOrNull { supportedAbi ->
            release.artifacts.firstOrNull { it.abi == supportedAbi }
        }
    }

    fun deviceEntry(release: RuntimePackRelease): RuntimePackArtifact =
        resolve(release.spec.id) ?: RuntimePackArtifact(
            spec = release.spec,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            fileName = "",
            downloadUrl = "",
            estimatedBytes = 0L,
        )

    private fun release(spec: RuntimePackSpec, sizes: Map<String, Long>): RuntimePackRelease =
        RuntimePackRelease(
            spec = spec,
            artifacts = sizes.map { (abi, size) ->
                val fileName = "linkora-runtime-${spec.id}-$abi.apk"
                RuntimePackArtifact(
                    spec = spec,
                    abi = abi,
                    fileName = fileName,
                    downloadUrl = "$RELEASE_BASE/$fileName",
                    estimatedBytes = size,
                )
            },
        )
}
