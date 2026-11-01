package com.joao.ilha

/**
 * Apps de banco e pagamento. Com um deles aberto a ilha some de vez (sem janela por cima),
 * e as notificações deles não passam pelo Notch. Muitos bancos bloqueiam o acesso quando
 * outro app desenha por cima ou acompanha a tela.
 */
object Banks {
    private val exact = setOf(
        "com.nu.production", "br.com.intermedium", "com.c6bank.app", "br.com.bb.android", "br.com.gabba.Caixa",
        "br.gov.caixa.tem", "br.com.caixa.fgts", "com.santander.app", "br.com.santander.way", "br.com.original.bank",
        "br.com.uol.ps.myaccount", "com.mercadopago.wallet", "com.picpay", "br.com.next", "br.com.neon",
        "br.com.bancopan", "br.com.agibank", "com.btg.pactual.banking", "br.com.xp.carteira", "br.com.rico.mobile",
        "com.paypal.android.p2pmobile", "br.com.sicoobnet", "br.com.sicredi", "br.com.banrisul", "com.wise.android",
        "com.revolut.revolut", "br.com.willbank", "br.com.mobills", "br.com.digio", "br.com.bradesco.next",
    )
    private val parts = listOf(
        "bank", "banco", "itau", "bradesco", "santander", "caixa", "sicredi", "sicoob", "banrisul", "nubank",
        "picpay", "mercadopago", "pagbank", "pagseguro", "c6bank", "intermedium", "paypal", ".bb.", "btgpactual",
    )

    fun isBank(pkg: String?): Boolean {
        if (pkg.isNullOrEmpty() || pkg == Wallet.PKG) return false
        if (pkg in exact) return true
        val p = pkg.lowercase()
        return parts.any { it in p }
    }
}
