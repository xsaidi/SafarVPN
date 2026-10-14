package com.wdtt.client.account

import com.wdtt.client.ConnectionProfile
import com.wdtt.client.ParsedRemoteSubscription
import com.wdtt.client.ProfilesStore
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import org.junit.Assert.*
import org.junit.Test

class AccountProfilesTest {
    private fun profile(peer: String, password: String = "synthetic") =
        ConnectionProfile("import-id", "Server", peer, "hash", 9, 9000, password)

    @Test fun refreshAndCredentialRotationPreserveProfileSelection() {
        val original = AccountProfiles.prepare(1, listOf(profile("node.example:56000"))).single()
        val refreshed = AccountProfiles.prepare(1, listOf(profile("node.example:56000", "rotated"))).single()
        assertEquals(original.id, refreshed.id)
        assertEquals("rotated", refreshed.password)
        assertEquals(AccountProfiles.GROUP_ID, refreshed.groupId)
        assertNotEquals("import-id", refreshed.id)
    }

    @Test fun ownersAndNodesHaveDifferentIdentitiesRegardlessOfDisplayName() {
        val first = AccountProfiles.prepare(1, listOf(profile("one.example:56000"), profile("two.example:56000")))
        val other = AccountProfiles.prepare(2, listOf(profile("one.example:56000"))).single()
        assertNotEquals(first[0].id, first[1].id)
        assertNotEquals(first[0].id, other.id)
        assertTrue(first.all { it.id.startsWith(AccountProfiles.PREFIX) })
    }

    @Test fun malformedOrDuplicateProfilesFailBeforeReplacingSavedData() {
        for (profiles in listOf(emptyList(), listOf(profile("")), listOf(profile("node", "")),
            listOf(profile("node"), profile("node")), listOf(profile("node").copy(listenPort = 0)))) {
            assertThrows(IllegalArgumentException::class.java) { AccountProfiles.prepare(1, profiles) }
        }
    }

    @Test fun lateDownloadCannotRecreateProfilesAfterLogoutOrAccountSwitch() {
        val generation = AccountGeneration()
        val download = generation.current()
        var saved = AccountProfiles.prepare(1, listOf(profile("node")))
        generation.advance { saved = emptyList() }
        assertFalse(generation.apply(download) { saved = AccountProfiles.prepare(1, listOf(profile("node"))) })
        assertTrue(saved.isEmpty())
        val login = generation.advance { saved = AccountProfiles.prepare(2, listOf(profile("node"))) }
        val newOwnerId = saved.single().id
        assertFalse(generation.apply(download) { saved = emptyList() })
        assertTrue(generation.apply(login) { assertEquals(newOwnerId, saved.single().id) })
    }

    @Test fun atomicRefreshLogoutAndSwitchPreserveManualProfilesAndTraffic() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("profiles_ids") to "manual",
            stringPreferencesKey("profile_name_manual") to "Manual",
            stringPreferencesKey("groups_ids") to "manual-folder",
            stringPreferencesKey("group_name_manual-folder") to "Same name",
            stringPreferencesKey("subscriptions_ids") to "manual-sub",
            stringPreferencesKey("sub_url_manual-sub") to "https://example.invalid/manual",
        )
        val parsed = ParsedRemoteSubscription(listOf(profile("one"), profile("two")), "Same name")
        val first = AccountProfiles.prepare(1, parsed.profiles)
        fun merge(profiles: List<ConnectionProfile>, data: ParsedRemoteSubscription?) {
            ProfilesStore.mergeAccountPreferences(prefs, profiles, data, profiles.associate { it.id to "encrypted-only" })
        }
        merge(first, parsed)
        prefs[doublePreferencesKey("profile_traffic_${first[0].id}")] = 123.0
        merge(first, parsed)
        assertEquals(3, prefs[stringPreferencesKey("profiles_ids")]!!.split(',').size)
        assertEquals(123.0, prefs[doublePreferencesKey("profile_traffic_${first[0].id}")]!!, 0.0)
        assertEquals("encrypted-only", prefs[stringPreferencesKey("profile_pass_enc_${first[0].id}")])
        val next = AccountProfiles.prepare(2, listOf(profile("two")))
        merge(next, parsed.copy(profiles = next))
        assertNull(prefs[stringPreferencesKey("profile_pass_enc_${first[0].id}")])
        assertNull(prefs[stringPreferencesKey("profile_pass_enc_${first[1].id}")])
        merge(emptyList(), null)
        assertEquals("manual", prefs[stringPreferencesKey("profiles_ids")])
        assertEquals("Manual", prefs[stringPreferencesKey("profile_name_manual")])
        assertEquals("manual-folder", prefs[stringPreferencesKey("groups_ids")])
        assertEquals("manual-sub", prefs[stringPreferencesKey("subscriptions_ids")])
        assertEquals("https://example.invalid/manual", prefs[stringPreferencesKey("sub_url_manual-sub")])
        assertNull(prefs[stringPreferencesKey("profile_pass_enc_${next.single().id}")])
    }
}
