package com.wdtt.client.account

import com.wdtt.client.ConnectionProfile
import java.util.UUID

/** Account storage has its own namespace; names never select or replace a manual folder. */
internal object AccountProfiles {
    const val GROUP_ID = "account-wdtt"
    const val SUBSCRIPTION_ID = "account-wdtt"
    const val URL = "account://wdtt"
    const val PREFIX = "account-wdtt-"

    fun prepare(owner: Long, profiles: List<ConnectionProfile>): List<ConnectionProfile> {
        require(owner > 0 && profiles.size in 1..64)
        require(profiles.map { it.peer }.distinct().size == profiles.size)
        return profiles.map { profile ->
            require(profile.peer.isNotBlank() && profile.peer.length <= 512)
            require(profile.password.isNotBlank() && profile.password.length <= 4096)
            require(profile.workersPerHash in 1..256 && profile.listenPort in 1..65535)
            profile.copy(
                id = PREFIX + UUID.nameUUIDFromBytes("$owner:${profile.peer}".toByteArray(Charsets.UTF_8)),
                groupId = GROUP_ID,
            )
        }
    }
}
