package top.linkora.mobile.auth

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LinkoraApi(
    private val baseUrl: String = "https://api.linkora.top",
) {
    suspend fun startDeviceAuthorization(): DeviceAuthorization = withContext(Dispatchers.IO) {
        val data = request(
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
        val data = request(
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
        val data = request(
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
        request(
            path = "/api/desktop/session/revoke",
            method = "POST",
            accessToken = accessToken,
        )
    }

    private fun request(
        path: String,
        method: String = "GET",
        body: String? = null,
        accessToken: String? = null,
    ): JSONObject {
        val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Linkora-Android/0.1.1")
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

            return envelope.optJSONObject("data") ?: JSONObject()
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
