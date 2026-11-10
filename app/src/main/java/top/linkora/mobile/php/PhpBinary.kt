package top.linkora.mobile.php

import android.content.Context
import top.linkora.mobile.runtime.RuntimePackState
import top.linkora.mobile.runtime.RuntimePacks

object PhpBinary {
    const val VERSION = "8.4.27"

    fun resolve(context: Context): java.io.File {
        val inspection = RuntimePacks.inspectPhp(context)
        check(inspection.state == RuntimePackState.Ready) { inspection.message }
        return checkNotNull(inspection.installation).executable
    }
}
