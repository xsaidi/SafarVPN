package com.wdtt.client.account

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MobileAuthProtocolTest {
    private val state = "s".repeat(43)
    private val code = "c".repeat(43)
    private val pending = PendingLogin("google", state, "v".repeat(43), "i".repeat(43), 1000)
    private val https = "https://safarvpn.ru/mobile-auth/return?code=$code&state=$state"

    @Test fun pkceMatchesRfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            MobileAuthProtocol.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val first = MobileAuthProtocol.randomSecret()
        val second = MobileAuthProtocol.randomSecret()
        assertTrue(MobileAuthProtocol.isOpaque(first))
        assertTrue(MobileAuthProtocol.isOpaque(second))
        assertNotEquals(first, second)
    }

    @Test fun acceptsOnlyExactCallbackAddressesAndMatchingLiveProof() {
        assertEquals(code, MobileAuthProtocol.parseReturn(https, pending, 999)?.code)
        assertEquals(code, MobileAuthProtocol.parseReturn(https.replace("https://safarvpn.ru/mobile-auth", "safarvpn://mobile-auth"), pending, 999)?.code)
        assertNull(MobileAuthProtocol.parseReturn(https, pending, 1000))
        assertNull(MobileAuthProtocol.parseReturn(https, null, 999))
        assertNull(MobileAuthProtocol.parseReturn(https.replace(state, "x".repeat(43)), pending, 999))
        for (bad in listOf(
            https.replace("https:", "http:"), https.replace("safarvpn.ru", "safarvpn.ru.evil.test"),
            https.replace("safarvpn.ru", "evil@safarvpn.ru"), https.replace("safarvpn.ru", "safarvpn.ru:443"),
            https.replace("/return?", "/return/?"), https.replace("/return?", "/%72eturn?"),
            "$https#fragment", "$https&code=$code", "$https&%73tate=$state", "$https&access_token=secret",
            https.replace("code=$code", "code=bad"), https.replace("code=$code", "error=unknown"),
            https.replace("state=$state", "state=%XX")
        )) assertNull(bad, MobileAuthProtocol.parseReturn(bad, pending, 999))
    }

    @Test fun doesNotClassifyManualProfileFilesAsAuth() {
        assertTrue(MobileAuthProtocol.isAuthCandidate(https))
        assertTrue(MobileAuthProtocol.isAuthCandidate("safarvpn://wrong/return"))
        assertTrue(MobileAuthProtocol.isAuthCandidate("https://safarvpn.ru/mobile-auth/wrong"))
        assertFalse(MobileAuthProtocol.isAuthCandidate("content://profiles/mobile-auth.conf"))
        assertFalse(MobileAuthProtocol.isAuthCandidate("file:///storage/mobile-auth.qwdtt"))
    }

    @Test fun externalLaunchIsBoundToTrustedProviderAndSession() {
        val session = "i".repeat(43)
        val google = "https://safarvpn.ru/api/cabinet/auth/mobile/google/start?session_id=$session"
        assertTrue(MobileAuthProtocol.trustedLaunch(google, "google", session))
        assertFalse(MobileAuthProtocol.trustedLaunch(google, "google", "z".repeat(43)))
        assertFalse(MobileAuthProtocol.trustedLaunch(google.replace("safarvpn.ru", "safarvpn.ru.evil"), "google", session))
        assertFalse(MobileAuthProtocol.trustedLaunch("$google&session_id=$session", "google", session))
        val telegram = "https://t.me/SafarVpnBot?start=mobileauth_$session"
        assertTrue(MobileAuthProtocol.trustedLaunch(telegram, "telegram", session))
        assertFalse(MobileAuthProtocol.trustedLaunch(telegram, "telegram", "b".repeat(43)))
        assertFalse(MobileAuthProtocol.trustedLaunch(telegram.replace("https:", "tg:"), "telegram", session))
        assertFalse(MobileAuthProtocol.trustedLaunch(telegram, "google", session))
    }

    @Test fun logoutAndNewLoginRejectOldExchangeRefreshAndProfileCompletions() {
        val generation = AccountGeneration()
        val first = generation.current()
        var persisted = "guest"
        assertTrue(generation.apply(first) { persisted = "account" })
        generation.advance { persisted = "guest" }
        repeat(3) { assertFalse(generation.apply(first) { persisted = "old credentials" }) }
        assertEquals("guest", persisted)
        val second = generation.advance()
        assertFalse(generation.apply(first) { persisted = "old profile" })
        assertTrue(generation.apply(second) { persisted = "new account" })
        assertEquals("new account", persisted)
    }

    @Test fun oldAccountProfileAndSubscriptionCannotOverwriteCompletedNewLogin() {
        val generation = AccountGeneration()
        var accountId = 10L
        var subscriptionOwner = 10L
        val newLoginGeneration = generation.advance() // old account request could have started here
        val oldRequestStarted = CountDownLatch(1)
        val finishOldResponse = CountDownLatch(1)
        val responseDone = CountDownLatch(1)
        val applied = AtomicBoolean(true)
        val request = Thread {
            oldRequestStarted.countDown()
            finishOldResponse.await(5, TimeUnit.SECONDS)
            applied.set(generation.apply(newLoginGeneration) {
                accountId = 10L
                subscriptionOwner = 10L
            })
            responseDone.countDown()
        }
        request.start()
        assertTrue(oldRequestStarted.await(5, TimeUnit.SECONDS))
        generation.apply(newLoginGeneration) {
            generation.advance { accountId = 20L; subscriptionOwner = 20L }
        }
        finishOldResponse.countDown()
        assertTrue(responseDone.await(5, TimeUnit.SECONDS))
        request.join()
        assertFalse(applied.get())
        assertEquals(20L, accountId)
        assertEquals(20L, subscriptionOwner)
    }
}
