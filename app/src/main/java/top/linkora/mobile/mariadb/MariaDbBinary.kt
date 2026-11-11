package top.linkora.mobile.mariadb

import android.content.Context
import top.linkora.mobile.runtime.RuntimePackInstallation
import top.linkora.mobile.runtime.RuntimePackState
import top.linkora.mobile.runtime.RuntimePacks

object MariaDbBinary {
    const val VERSION = "13.0.2"

    fun resolve(context: Context): RuntimePackInstallation {
        val inspection = RuntimePacks.inspectMariaDb(context)
        check(inspection.state == RuntimePackState.Ready) { inspection.message }
        return checkNotNull(inspection.installation)
    }
}
