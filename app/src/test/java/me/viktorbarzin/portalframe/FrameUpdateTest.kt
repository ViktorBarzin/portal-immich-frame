package me.viktorbarzin.portalframe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision to replace the running app with a downloaded one.
 *
 * This is the only code in the project that can cause other code to run, so the
 * checks are deliberately strict and the failure mode is always "keep showing
 * photos": a malformed manifest, an unexpected scheme, a checksum that doesn't
 * match, or a version that isn't newer all mean "do nothing", never "try anyway".
 * The frames sit on a wall in two countries; a bad update is far more expensive
 * than a missed one.
 */
class FrameUpdateTest {

    private val sha = "a".repeat(64)

    private fun manifestJson(
        versionCode: Int = 9,
        versionName: String = "0.1.8",
        url: String = "https://updates.example/app-0.1.8.apk",
        sha256: String = sha,
    ) = """{"versionCode":$versionCode,"versionName":"$versionName","url":"$url","sha256":"$sha256"}"""

    @Test
    fun `parses a well-formed manifest`() {
        val m = FrameUpdate.parse(manifestJson())
        assertEquals(9, m?.versionCode)
        assertEquals("0.1.8", m?.versionName)
        assertEquals("https://updates.example/app-0.1.8.apk", m?.url)
        assertEquals(sha, m?.sha256)
    }

    @Test
    fun `ignores unknown fields so the manifest can grow without breaking old frames`() {
        val m = FrameUpdate.parse(
            """{"versionCode":9,"versionName":"0.1.8","url":"https://u.example/a.apk",
               "sha256":"$sha","releaseNotes":"hi","minAndroid":28}"""
        )
        assertEquals(9, m?.versionCode)
    }

    @Test
    fun `rejects junk rather than throwing at it`() {
        assertNull(FrameUpdate.parse(""))
        assertNull(FrameUpdate.parse("not json"))
        assertNull(FrameUpdate.parse("<!DOCTYPE html><html>a captive portal</html>"))
        assertNull(FrameUpdate.parse("{}"))
    }

    @Test
    fun `rejects a manifest missing any required field`() {
        assertNull(FrameUpdate.parse("""{"versionName":"0.1.8","url":"https://u.example/a.apk","sha256":"$sha"}"""))
        assertNull(FrameUpdate.parse("""{"versionCode":9,"url":"https://u.example/a.apk","sha256":"$sha"}"""))
        assertNull(FrameUpdate.parse("""{"versionCode":9,"versionName":"0.1.8","sha256":"$sha"}"""))
        assertNull(FrameUpdate.parse("""{"versionCode":9,"versionName":"0.1.8","url":"https://u.example/a.apk"}"""))
    }

    @Test
    fun `rejects a download url that is not http or https`() {
        // The URL names a file we will hand to the package installer, so the scheme
        // allow-list is the same security boundary FrameUrl draws for the WebView.
        assertNull(FrameUpdate.parse(manifestJson(url = "file:///sdcard/evil.apk")))
        assertNull(FrameUpdate.parse(manifestJson(url = "content://evil/apk")))
        assertNull(FrameUpdate.parse(manifestJson(url = "javascript:alert(1)")))
        assertNull(FrameUpdate.parse(manifestJson(url = "ftp://u.example/a.apk")))
    }

    @Test
    fun `rejects a checksum that is not a sha256 hex digest`() {
        assertNull(FrameUpdate.parse(manifestJson(sha256 = "")))
        assertNull(FrameUpdate.parse(manifestJson(sha256 = "abc")))
        assertNull(FrameUpdate.parse(manifestJson(sha256 = "z".repeat(64))))
        assertNull(FrameUpdate.parse(manifestJson(sha256 = "a".repeat(63))))
        assertNull(FrameUpdate.parse(manifestJson(sha256 = "a".repeat(65))))
    }

    @Test
    fun `accepts an upper-case checksum, since sha256sum and CI tooling disagree on case`() {
        assertEquals("a".repeat(64), FrameUpdate.parse(manifestJson(sha256 = "A".repeat(64)))?.sha256)
    }

    @Test
    fun `installs only a strictly newer build`() {
        val m = FrameUpdate.parse(manifestJson(versionCode = 9))!!
        assertTrue(FrameUpdate.shouldInstall(installed = 8, available = m))
        assertFalse(FrameUpdate.shouldInstall(installed = 9, available = m))
        assertFalse(FrameUpdate.shouldInstall(installed = 10, available = m))
    }

    @Test
    fun `never downgrades — a rolled-back manifest must not un-install a fix`() {
        val old = FrameUpdate.parse(manifestJson(versionCode = 3))!!
        assertFalse(FrameUpdate.shouldInstall(installed = 8, available = old))
    }

    @Test
    fun `verifies a download against the manifest checksum`() {
        val bytes = "the apk bytes".toByteArray()
        val digest = FrameUpdate.sha256(bytes)
        assertTrue(FrameUpdate.matches(bytes, digest))
        assertTrue("case must not matter", FrameUpdate.matches(bytes, digest.uppercase()))
        assertFalse(FrameUpdate.matches(bytes, sha))
        assertFalse(FrameUpdate.matches("other bytes".toByteArray(), digest))
    }

    @Test
    fun `sha256 is the standard digest, not something home-made`() {
        // Known-answer test: SHA-256 of the empty input.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            FrameUpdate.sha256(ByteArray(0))
        )
    }
}
