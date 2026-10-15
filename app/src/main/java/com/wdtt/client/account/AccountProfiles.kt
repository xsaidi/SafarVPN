package com.wdtt.client.account

import com.wdtt.client.ConnectionProfile
import java.util.UUID

/** Account storage has its own namespace; names never select or replace a manual folder. */
internal object AccountProfiles {
    const val GROUP_ID = "account-wdtt"
    const val SUBSCRIPTION_ID = "account-wdtt"
    const val URL = "account://wdtt"
    const val PREFIX = "account-wdtt-"
    fun legacyId(owner: Long, peer: String): String = PREFIX + UUID.nameUUIDFromBytes("$owner:$peer".toByteArray(Charsets.UTF_8))

    fun prepare(owner: Long, profiles: List<ConnectionProfile>): List<ConnectionProfile> {
        require(owner > 0 && profiles.size in 1..64)
        require(profiles.map { it.peer }.distinct().size == profiles.size)
        val nodes = profiles.mapNotNull { it.nodeId }
        require(nodes.all { it > 0 } && nodes.distinct().size == nodes.size)
        return profiles.map { profile ->
            require(profile.peer.isNotBlank() && profile.peer.length <= 512)
            require(profile.password.isNotBlank() && profile.password.length <= 4096)
            require(profile.workersPerHash in 1..256 && profile.listenPort in 1..65535)
            profile.copy(
                id = profile.nodeId?.let { PREFIX + UUID.nameUUIDFromBytes("$owner:node:$it".toByteArray(Charsets.UTF_8)) }
                    ?: legacyId(owner, profile.peer),
                groupId = GROUP_ID,
            )
        }
    }
}
