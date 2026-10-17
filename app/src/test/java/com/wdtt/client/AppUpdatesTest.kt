package com.wdtt.client
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class AppUpdatesTest {
    private fun release(url: String = "https://github.com/xsaidi/SafarVPN/releases/download/safarvpn-1.4.22/SafarVPN-1.4.22-universal-release.apk") = JSONObject()
        .put("tag_name", "safarvpn-1.4.22").put("assets", org.json.JSONArray().put(JSONObject()
            .put("name", "SafarVPN-1.4.22-universal-release.apk").put("size", 38669631).put("browser_download_url", url)))
    @Test fun latestMustBeStrictlyNewer() {
        assertFalse(ReleasePolicy.newer("safarvpn-1.4.20", "1.4.21"))
        assertFalse(ReleasePolicy.newer("1.4.21", "1.4.21"))
        assertTrue(ReleasePolicy.newer("safarvpn-1.4.22", "1.4.21"))
        assertTrue(ReleasePolicy.newer("v1.4.100", "1.4.99"))
        assertFalse(ReleasePolicy.newer("1.5.0-beta", "1.4.21"))
    }
    @Test fun stableUniversalAssetOnly() {
        assertEquals("1.4.22", ReleasePolicy.parse(release(), "1.4.21")!!.version)
        assertNull(ReleasePolicy.parse(release().put("prerelease", true), "1.4.21"))
        assertNull(ReleasePolicy.parse(release().put("draft", true), "1.4.21"))
        assertNull(ReleasePolicy.parse(release(), "1.4.23"))
    }
    @Test fun redirectsCannotEscapeGithubReleaseHosts() {
        assertTrue(ReleasePolicy.allowedDownload("https://release-assets.githubusercontent.com/a"))
        for (url in listOf("http://github.com/xsaidi/SafarVPN/releases/download/a/b", "https://github.com.evil.invalid/a",
            "https://user@github.com/xsaidi/SafarVPN/releases/download/a/b", "https://github.com/other/project/releases/download/a/b"))
            assertFalse(url, ReleasePolicy.allowedDownload(url))
    }
    @Test(expected = IllegalArgumentException::class) fun untrustedAssetIsRejected() {
        ReleasePolicy.parse(release("https://evil.invalid/file.apk"), "1.4.21")
    }
}
