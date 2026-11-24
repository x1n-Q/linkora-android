package top.linkora.mobile.runtime

import android.os.Build

data class RuntimePackArtifact(
    val spec: RuntimePackSpec,
    val abi: String,
    val fileName: String,
    val downloadUrl: String,
    val estimatedBytes: Long,
    val archiveSha256: String,
)

private data class RuntimeArtifactRelease(
    val size: Long,
    val sha256: String,
)

data class RuntimePackRelease(
    val spec: RuntimePackSpec,
    val artifacts: List<RuntimePackArtifact>,
)

object RuntimePackCatalog {
    private const val RELEASE_BASE =
        "https://github.com/x1n-Q/linkora-runtime-packs/releases/download/runtime-packs-v0.7.0"

    val Node = release(
        spec = RuntimePacks.Node,
        artifacts = mapOf(
            "arm64-v8a" to RuntimeArtifactRelease(
                size = 23_367_174L,
                sha256 = "059abe5b775eac0ad71d15cbd3861c8bff79532f85a2e64be54a7455881f5321",
            ),
        ),
    )

    val Php = release(
        spec = RuntimePacks.Php,
        artifacts = mapOf(
            "arm64-v8a" to RuntimeArtifactRelease(
                size = 3_912_714L,
                sha256 = "16954b75e4e5bdbf59cf278f52f15b3c05caddeb4fb3249509ca8574aa13405e",
            ),
        ),
    )

    val MariaDb = release(
        spec = RuntimePacks.MariaDb,
        artifacts = mapOf(
            "arm64-v8a" to RuntimeArtifactRelease(
                size = 12_343_754L,
                sha256 = "362cc87faadf09d88409c02af084ca258688ede75a7236e1b0e688bb61d33071",
            ),
        ),
    )

    val Connector = release(
        spec = RuntimePacks.Connector,
        artifacts = mapOf(
            "arm64-v8a" to RuntimeArtifactRelease(
                size = 9_715_236L,
                sha256 = "80359dc282fefb43d0af7939663a15e5dfe06f42a6a455c3b818d5df09d3ee4a",
            ),
        ),
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
            archiveSha256 = "",
        )

    private fun release(
        spec: RuntimePackSpec,
        artifacts: Map<String, RuntimeArtifactRelease>,
    ): RuntimePackRelease =
        RuntimePackRelease(
            spec = spec,
            artifacts = artifacts.map { (abi, release) ->
                val fileName = "linkora-runtime-${spec.id}-$abi.apk"
                RuntimePackArtifact(
                    spec = spec,
                    abi = abi,
                    fileName = fileName,
                    downloadUrl = "$RELEASE_BASE/$fileName",
                    estimatedBytes = release.size,
                    archiveSha256 = release.sha256,
                )
            },
        )
}
