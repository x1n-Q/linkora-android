package top.linkora.mobile.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkoraAuthUrlPolicyTest {
    @Test
    fun `allows secure Linkora addresses`() {
        assertEquals(
            "https://linkora.top/device?code=ABC",
            LinkoraAuthUrlPolicy.allowedUrl("https://linkora.top/device?code=ABC"),
        )
        assertEquals(
            "https://account.linkora.top/device",
            LinkoraAuthUrlPolicy.allowedUrl("https://account.linkora.top/device"),
        )
    }

    @Test
    fun `rejects insecure deceptive and non-web addresses`() {
        listOf(
            "http://linkora.top/device",
            "https://linkora.top.evil.test/device",
            "https://linkora.top@evil.test/device",
            "https://linkora.top:8443/device",
            "intent://linkora.top/device",
            "not a url",
        ).forEach { url -> assertNull(LinkoraAuthUrlPolicy.allowedUrl(url)) }
    }
}
