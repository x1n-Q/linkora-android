package top.linkora.mobile.runtime

import android.content.Context

object RuntimePacks {
    val Node = RuntimePackSpec(
        id = "node",
        displayName = "Node.js Runtime Pack",
        packageName = "top.linkora.runtime.node",
        protocolVersion = 1,
        runtimeVersion = "24.22.1",
        executableName = "libnode_runtime.so",
        supportedAbis = setOf("arm64-v8a"),
        nativeLibraryHashes = mapOf(
            "libnode_runtime.so" to
                "c03a6a63832dc27dec14d6879a194242c7f2142301471c18d67354fe137d2b6a",
            "libc++_shared.so" to
                "cd61762848882a16c8244c964a6f396c0caa0b440588a210ce9cc4ab0e6d9f0c",
        ),
        assetHashes = mapOf(
            "node/npm.zip" to
                "e8e8c951a85400bc5706564197dfbf4f1682b52dcc3cba1731041d6b5f49424e",
        ),
    )

    val Php = RuntimePackSpec(
        id = "php",
        displayName = "PHP Runtime Pack",
        packageName = "top.linkora.runtime.php",
        protocolVersion = 1,
        runtimeVersion = "8.4.27",
        executableName = "libphp_runtime.so",
        supportedAbis = setOf("arm64-v8a"),
        nativeLibraryHashes = mapOf(
            "libphp_runtime.so" to
                "17d779851bb77c6a2f29efbf92d2d2911b373d8cb8c3e2263f9d3c2a11111776",
        ),
    )

    fun inspectNode(context: Context): RuntimePackInspection =
        RuntimePackManager(context).inspect(Node)

    fun inspectPhp(context: Context): RuntimePackInspection =
        RuntimePackManager(context).inspect(Php)
}
