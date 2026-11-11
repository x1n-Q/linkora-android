package top.linkora.mobile.mariadb

import android.content.Context

fun MutableMap<String, String>.putMariaDbEnvironment(context: Context) {
    val credentials = MariaDbCredentialStore.load(context)
    put("DB_HOST", credentials.host)
    put("DB_PORT", credentials.port.toString())
    put("DB_DATABASE", credentials.database)
    put("DB_USERNAME", credentials.username)
    put("DB_PASSWORD", credentials.password)
    put("MYSQL_HOST", credentials.host)
    put("MYSQL_TCP_PORT", credentials.port.toString())
    put("MYSQL_DATABASE", credentials.database)
    put("MYSQL_USER", credentials.username)
    put("MYSQL_PASSWORD", credentials.password)
}
