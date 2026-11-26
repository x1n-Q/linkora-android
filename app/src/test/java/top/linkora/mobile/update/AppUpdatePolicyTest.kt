package top.linkora.mobile.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {
    @Test
    fun `compares stable semantic versions`() {
        assertTrue(AppUpdatePolicy.isNewerVersion("0.8.0", "0.7.0"))
        assertTrue(AppUpdatePolicy.isNewerVersion("v1.0", "0.9.12"))
        assertFalse(AppUpdatePolicy.isNewerVersion("0.7.0", "0.7.0"))
        assertFalse(AppUpdatePolicy.isNewerVersion("0.6.9", "0.7.0"))
        assertFalse(AppUpdatePolicy.isNewerVersion("latest", "0.7.0"))
    }

    @Test
    fun `only accepts release APKs from the official GitHub repository`() {
        val official =
            "https://github.com/x1n-Q/linkora-android/releases/download/v0.8.0/linkora-mobile-v0.8.0.apk"
        assertEquals(official, AppUpdatePolicy.allowedDownloadUrl(official))
        assertNull(AppUpdatePolicy.allowedDownloadUrl("http://github.com/x1n-Q/linkora-android/a.apk"))
        assertNull(AppUpdatePolicy.allowedDownloadUrl("https://github.example/x1n-Q/linkora-android/a.apk"))
        assertNull(AppUpdatePolicy.allowedDownloadUrl("https://github.com/other/project/releases/download/v1/a.apk"))
    }

    @Test
    fun `normalizes GitHub SHA256 digests`() {
        val digest = "A".repeat(64)
        assertEquals("a".repeat(64), AppUpdatePolicy.normalizedSha256("sha256:$digest"))
        assertNull(AppUpdatePolicy.normalizedSha256("sha256:1234"))
    }
}
