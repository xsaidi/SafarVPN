package com.wdtt.client
import org.junit.Assert.*
import org.junit.Test
class DefaultBypassTest {
    @Test fun concreteServicesFitExistingRoutingLimit() {
        assertTrue(DefaultBypass.domains.size < BypassRoutes.MAX_RULES)
        assertFalse(DefaultBypass.domains.any { it in setOf("ru", "su", "xn--p1ai") || it.startsWith("domain:") })
        assertTrue(DefaultBypass.domains.containsAll(listOf("maps.google.com", "vk.com", "yandex.ru")))
    }
    @Test fun defaultsPreserveUserEntriesAndDoNotDuplicate() {
        val merged = DefaultBypass.mergeDomains("10.0.0.0/8\nexample.org\nvk.com")
        assertTrue(merged.startsWith("10.0.0.0/8\nexample.org\nvk.com"))
        assertEquals(merged, DefaultBypass.mergeDomains(merged))
        val apps = DefaultBypass.mergePackages("user.example,com.vkontakte.android")
        assertTrue(apps.startsWith("user.example,com.vkontakte.android"))
        assertEquals(apps, DefaultBypass.mergePackages(apps))
    }
}
