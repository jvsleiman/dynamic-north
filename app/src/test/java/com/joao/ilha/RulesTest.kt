package com.joao.ilha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    @Test
    fun redactKeepsOrdinaryChat() {
        val out = redactNotice("Ana", "Bora jogar hoje?", listOf(Msg("Ana", "Bora jogar hoje?")), showSensitive = false)
        assertEquals("Ana", out.title)
        assertEquals("Bora jogar hoje?", out.text)
        assertEquals("Bora jogar hoje?", out.messages.single().text)
    }

    @Test
    fun redactHidesCodeInBodyAndKeepsName() {
        val out = redactNotice("Banco", "Seu código é 123456", emptyList(), showSensitive = false)
        assertEquals("Banco", out.title)
        assertEquals(SENSITIVE_PLACEHOLDER, out.text)
    }

    @Test
    fun redactHidesDigitsSplitFromTheKeyword() {
        val out = redactNotice("482193", "seu código de acesso", emptyList(), showSensitive = false)
        assertEquals(SENSITIVE_PLACEHOLDER, out.title)
        assertEquals("seu código de acesso", out.text)
    }

    @Test
    fun redactEachMessageOnItsOwn() {
        val out = redactNotice(
            "WhatsApp",
            "2 mensagens",
            listOf(Msg("Ana", "oi"), Msg("Banco", "código 908172")),
            showSensitive = false,
        )
        assertEquals("oi", out.messages[0].text)
        assertEquals(SENSITIVE_PLACEHOLDER, out.messages[1].text)
        assertEquals("2 mensagens", out.text)
    }

    @Test
    fun showSensitiveLeavesTheCode() {
        val out = redactNotice("Banco", "código 123456", listOf(Msg("Banco", "código 123456")), showSensitive = true)
        assertEquals("código 123456", out.text)
        assertEquals("código 123456", out.messages.single().text)
    }

    @Test
    fun bedtimeWindowIsFiveMinutesAroundTheReminder() {
        assertFalse(inBedtimeWindow(31))
        assertTrue(inBedtimeWindow(30))
        assertTrue(inBedtimeWindow(25))
        assertFalse(inBedtimeWindow(24))
    }

    @Test
    fun stepCounterFollowsHealthSurfacesOnly() {
        assertTrue(wantsStepCounter(Settings()))
        assertFalse(wantsStepCounter(Settings(homeHealth = false, idleStyle = IdleStyle.Simple)))
        assertTrue(wantsStepCounter(Settings(homeHealth = false, idleStyle = IdleStyle.Health)))
        assertFalse(wantsStepCounter(Settings(homeHealth = false, idleStyle = IdleStyle.Weather)))
    }

    @Test
    fun pendingQueueDropsTheOldestAndSkipsExpired() {
        val queue = rememberPending(rememberPending(rememberPending(emptyList(), "a", 0L), "b", 1L), "c", 2L)
        val full = rememberPending(queue, "d", 3L)
        assertEquals(listOf("b", "c", "d"), full.map { it.first })
        val (next, rest) = nextPending(listOf("a" to 0L, "b" to 15_000L), now = 20_000L)!!
        assertEquals("b", next)
        assertTrue(rest.isEmpty())
        assertNull(nextPending(listOf("a" to 0L), now = 20_000L))
    }

    @Test
    fun downloadDoneNeedsCompletionOrASuccessText() {
        assertTrue(downloadFinished(true, false, 0.4f, "Baixando", "40%"))
        assertFalse(downloadFinished(false, true, 0.95f, "Baixando", "95%"))
        assertTrue(downloadFinished(false, true, 0.95f, "Play Store", "Instalado"))
        assertFalse(downloadFinished(false, true, 0.95f, "Download", "Cancelado"))
        assertTrue(downloadFinished(false, true, 1f, "Arquivo", "100%"))
        assertFalse(downloadFinished(false, false, 1f, "Arquivo", "100%"))
    }

    @Test
    fun meetingNeedsCallStyleOrACallButton() {
        assertTrue(ongoingMeetingIsCall(emptyList(), callType = 1))
        assertTrue(ongoingMeetingIsCall(listOf("Leave"), callType = 0))
        assertFalse(ongoingMeetingIsCall(listOf("Leave a review"), callType = 0))
        assertFalse(ongoingMeetingIsCall(emptyList(), callType = 0))
        assertTrue(actionLooksLikeCallControl("Atender"))
    }

    @Test
    fun hashIsOptionalForOldVersionJson() {
        val bytes = "ilha".toByteArray()
        val hex = sha256Hex(bytes)
        assertTrue(hashMatches(null, hex))
        assertTrue(hashMatches("", hex))
        assertTrue(hashMatches(hex.uppercase(), hex))
        assertFalse(hashMatches("00", hex))
    }
}
