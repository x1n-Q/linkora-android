package top.linkora.mobile.auth

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.linkora.mobile.publish.DomainPool
import top.linkora.mobile.publish.ManagedHostname
import top.linkora.mobile.publish.TunnelAccess

class LinkoraApi(
    private val baseUrl: String = "https://api.linkora.top",
) {
    suspend fun startDeviceAuthorization(): DeviceAuthorization = withContext(Dispatchers.IO) {
        val data = requestObject(
            path = "/api/desktop/device/start",
            method = "POST",
            body = JSONObject()
                .put("clientName", "Linkora Android")
                .toString(),
        )

        DeviceAuthorization(
            deviceCode = data.requiredString("deviceCode"),
            userCode = data.requiredString("userCode"),
            verificationUrl = data.requiredString("verificationUrl"),
            expiresInSeconds = data.optLong("expiresIn", 600L),
            pollIntervalSeconds = data.optLong("pollInterval", 3L).coerceAtLeast(1L),
        )
    }

    suspend fun pollDeviceToken(deviceCode: String): DeviceTokenResult = withContext(Dispatchers.IO) {
        val data = requestObject(
            path = "/api/desktop/device/token",
            method = "POST",
            body = JSONObject().put("deviceCode", deviceCode).toString(),
        )

        when (data.requiredString("status")) {
            "PENDING" -> DeviceTokenResult.Pending
            "APPROVED" -> DeviceTokenResult.Approved(
                accessToken = data.requiredString("accessToken"),
                expiresAt = data.requiredString("expiresAt"),
            )
            else -> throw LinkoraApiException(500, "Linkora returned an unknown authorization state.")
        }
    }

    suspend fun getSession(accessToken: String): LinkoraSession = withContext(Dispatchers.IO) {
        val data = requestObject(
            path = "/api/desktop/session",
            accessToken = accessToken,
        )

        LinkoraSession(
            id = data.requiredString("id"),
            email = data.requiredString("email"),
            hostnameQuota = data.optInt("hostnameQuota"),
            tokenExpiresAt = data.requiredString("tokenExpiresAt"),
        )
    }

    suspend fun revokeSession(accessToken: String) = withContext(Dispatchers.IO) {
        requestObject(
            path = "/api/desktop/session/revoke",
            method = "POST",
            accessToken = accessToken,
        )
    }

    suspend fun getDomainPools(accessToken: String): List<DomainPool> =
        withContext(Dispatchers.IO) {
            val data = requestArray(
                path = "/api/subdomains/pools",
                accessToken = accessToken,
            )
            buildList {
                for (index in 0 until data.length()) {
                    val pool = data.optJSONObject(index) ?: continue
                    val id = pool.optString("id")
                    val domain = pool.optString("domain")
                    if (id.isNotBlank() && domain.isNotBlank()) {
                        add(DomainPool(id = id, domain = domain))
                    }
                }
            }
        }

    suspend fun getManagedHostnames(accessToken: String): List<ManagedHostname> =
        withContext(Dispatchers.IO) {
            val data = requestObject(
                path = "/api/subdomains/my",
                accessToken = accessToken,
            ).optJSONArray("subdomains") ?: JSONArray()
            buildList {
                for (index in 0 until data.length()) {
                    val hostname = data.optJSONObject(index) ?: continue
                    if (hostname.optString("connectionType") != "MANAGED_TUNNEL") continue
                    val id = hostname.optString("id")
                    val name = hostname.optString("hostname")
                    if (id.isBlank() || name.isBlank()) continue
                    add(
                        ManagedHostname(
                            id = id,
                            hostname = name,
                            tunnelId = hostname.optJSONObject("managedTunnel")
                                ?.optString("id")
                                ?.takeIf(String::isNotBlank),
                            status = hostname.optString("status"),
                        ),
                    )
                }
            }
        }

    suspend fun claimManagedHostname(
        accessToken: String,
        poolId: String,
        label: String,
        localServiceUrl: String,
    ): ManagedHostname = withContext(Dispatchers.IO) {
        val data = requestObject(
            path = "/api/subdomains",
            method = "POST",
            body = JSONObject()
                .put("domainPoolId", poolId)
                .put("label", label)
                .put("connectionType", "MANAGED_TUNNEL")
                .put("target", localServiceUrl)
                .toString(),
            accessToken = accessToken,
        )
        ManagedHostname(
            id = data.requiredString("id"),
            hostname = data.requiredString("hostname"),
            tunnelId = data.optJSONObject("managedTunnel")
                ?.optString("id")
                ?.takeIf(String::isNotBlank),
            status = data.optString("status"),
        )
    }

    suspend fun createTunnel(
        accessToken: String,
        subdomainId: String,
        hostname: String,
        localServiceUrl: String,
    ): TunnelAccess = withContext(Dispatchers.IO) {
        val data = requestObject(
            path = "/api/tunnels",
            method = "POST",
            body = JSONObject()
                .put("subdomainId", subdomainId)
                .put("localServiceUrl", localServiceUrl)
                .toString(),
            accessToken = accessToken,
        )
        TunnelAccess(
            hostname = hostname,
            token = data.requiredString("token"),
        )
    }

    suspend fun connectTunnel(
        accessToken: String,
        tunnelId: String,
        localServiceUrl: String,
    ): TunnelAccess = withContext(Dispatchers.IO) {
        val data = requestObject(
            path = "/api/desktop/tunnels/${java.net.URLEncoder.encode(tunnelId, Charsets.UTF_8.name())}/connect",
            method = "POST",
            body = JSONObject().put("localServiceUrl", localServiceUrl).toString(),
            accessToken = accessToken,
        )
        TunnelAccess(
            hostname = data.requiredString("hostname"),
            token = data.requiredString("tunnelToken"),
        )
    }

    private fun requestObject(
        path: String,
        method: String = "GET",
        body: String? = null,
        accessToken: String? = null,
    ): JSONObject = requestData(path, method, body, accessToken) as? JSONObject
        ?: throw LinkoraApiException(500, "Linkora returned an invalid object response.")

    private fun requestArray(
        path: String,
        method: String = "GET",
        body: String? = null,
        accessToken: String? = null,
    ): JSONArray = requestData(path, method, body, accessToken) as? JSONArray
        ?: throw LinkoraApiException(500, "Linkora returned an invalid list response.")

    private fun requestData(
        path: String,
        method: String = "GET",
        body: String? = null,
        accessToken: String? = null,
    ): Any {
        val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Linkora-Android/0.3.1")
            accessToken?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
            }

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(body)
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val rawResponse = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val envelope = runCatching { JSONObject(rawResponse) }.getOrElse {
                throw LinkoraApiException(status, "Linkora returned an invalid response.")
            }

            if (status !in 200..299 || !envelope.optBoolean("success")) {
                throw LinkoraApiException(
                    status = status,
                    message = envelope.optString("error").ifBlank { "The Linkora request failed." },
                )
            }

            return envelope.opt("data") ?: JSONObject()
        } finally {
            connection.disconnect()
        }
    }
}

class LinkoraApiException(
    val status: Int,
    message: String,
) : Exception(message)

private fun JSONObject.requiredString(name: String): String =
    optString(name).takeIf { it.isNotBlank() }
        ?: throw LinkoraApiException(500, "Linkora response is missing $name.")
