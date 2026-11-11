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
                "6162e6cd7850c0eee23837859d2240a4649b398c8263cfbb19984a375e0b8576",
        ),
    )

    val MariaDb = RuntimePackSpec(
        id = "mariadb",
        displayName = "MariaDB Runtime Pack",
        packageName = "top.linkora.runtime.mariadb",
        protocolVersion = 1,
        runtimeVersion = "13.0.2",
        executableName = "libmariadbd_runtime.so",
        supportedAbis = setOf("arm64-v8a"),
        nativeLibraryHashes = mapOf(
            "libmariadbd_runtime.so" to
                "2f222e39293fba966e11421adfa1b854e55af9179673d3fb76dc22313a56e4d8",
            "libandroid-support.so" to
                "b85cf4366fa2f2babbba65919aaaeaddd9d06c8f760e94d926e1de473f975b76",
            "libpcre2-8.so" to
                "c377920ae65a2459379855f436d0fa794840e87f04102696998336b7134727b7",
            "libcrypt.so" to
                "2eb51887ee32c148a215dd58f823ee0d0f6967d11c0a2a2763e9a9f69867fcf8",
            "liblinkora_z.so" to
                "bb7a5d47358e499dc9a384a23637a10e6ae4e2d95916f3d75e2064fcf859713d",
            "liblinkora_ssl.so" to
                "69eab05c3df398558e6956e2b367d274195b85874bfd4efa468d1f6e804ecc58",
            "liblinkora_crypto.so" to
                "2234d1756cd56345d220e0a2312efdc811ca1e1b4c3eed26f3cd762570e7e6a9",
            "libc++_shared.so" to
                "3fa975c388ee7407f75a562f8a157f3b329ccbea56d34511a30becfa19dd665a",
        ),
        assetHashes = mapOf(
            "mariadb/share.zip" to
                "5a2910048432b47ae6788a12d965a0788a4b9f011089491833b4c17e04130290",
        ),
    )

    val Connector = RuntimePackSpec(
        id = "connector",
        displayName = "Linkora Connector Pack",
        packageName = "top.linkora.runtime.connector",
        protocolVersion = 1,
        runtimeVersion = "2026.10.4",
        executableName = "libcloudflared.so",
        supportedAbis = setOf("arm64-v8a"),
        nativeLibraryHashes = mapOf(
            "libcloudflared.so" to
                "218a64c6c09cdb0cb9c751cde31b976c0a1c4db9fd6d6d512c4ef96f374840e1",
        ),
    )

    fun inspectNode(context: Context): RuntimePackInspection =
        RuntimePackManager(context).inspect(Node)

    fun inspectPhp(context: Context): RuntimePackInspection =
        RuntimePackManager(context).inspect(Php)

    fun inspectMariaDb(context: Context): RuntimePackInspection =
        RuntimePackManager(context).inspect(MariaDb)

    fun inspectConnector(context: Context): RuntimePackInspection =
        RuntimePackManager(context).inspect(Connector)
}
