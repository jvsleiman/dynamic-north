package com.joao.ilha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BanksOrdersTest {
    @Test
    fun knownBanksAndWallet() {
        assertTrue(Banks.isBank("com.nu.production"))
        assertTrue(Banks.isBank("br.gov.caixa.tem"))
        assertTrue(Banks.isBank("com.itau.iphone"))
        assertTrue(Banks.isBank("com.nubank.app"))
        assertTrue(Banks.isBank("br.com.bb.android"))
        assertFalse(Banks.isBank(Wallet.PKG))
        assertFalse(Banks.isBank(null))
        assertFalse(Banks.isBank(""))
    }

    @Test
    fun genericTokensDoNotMatchInsideOtherWords() {
        assertFalse(Banks.isBank("com.foodbank.app"))
        assertFalse(Banks.isBank("com.example.embankment"))
        assertFalse(Banks.isBank("com.caixaferramentas.app"))
        assertFalse(Banks.isBank("com.whatsapp"))
    }

    @Test
    fun orderStepsAndEta() {
        assertEquals(OrderStep.OnTheWay, Orders.stepOf("seu pedido saiu para entrega"))
        assertEquals(OrderStep.Preparing, Orders.stepOf("o restaurante confirmou o pedido"))
        assertEquals(OrderStep.Delivered, Orders.stepOf("pedido entregue, bom apetite"))
        assertEquals(OrderStep.Cancelled, Orders.stepOf("pedido cancelado"))
        assertEquals(null, Orders.stepOf("cupom de sobremesa"))
        assertEquals("19:46 - 19:56", Orders.etaOf("previsão 19:46 - 19:56"))
        val now = 1_700_000_000_000L
        assertTrue(Orders.etaOf("chega em 20-30 min", now).contains(":"))
    }
}
