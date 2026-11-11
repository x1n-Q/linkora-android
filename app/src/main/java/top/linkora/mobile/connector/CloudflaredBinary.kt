package top.linkora.mobile.connector

import android.content.Context
import java.io.File
import top.linkora.mobile.runtime.RuntimePacks

object CloudflaredBinary {
    fun resolve(context: Context): File {
        val installation = checkNotNull(RuntimePacks.inspectConnector(context).installation) {
            "Install the compatible Linkora Connector Pack before assigning a public URL."
        }
        File(context.filesDir, "connector").deleteRecursively()
        return installation.executable
    }
}
