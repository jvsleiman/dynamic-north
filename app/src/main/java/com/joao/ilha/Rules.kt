package com.joao.ilha

/** Texto mostrado no lugar de um código de verificação. */
internal const val SENSITIVE_PLACEHOLDER = "Conteúdo sensível oculto"

private val sensitiveDigits = Regex("\\b\\d{4,8}\\b")
private val sensitiveWords = listOf("código", "codigo", "code", "otp", "senha", "verifica", "token")

/** Título, corpo ou mensagem parecem um código de verificação. */
internal fun isSensitiveText(text: String): Boolean {
    val all = text.lowercase()
    return sensitiveDigits.containsMatchIn(all) && sensitiveWords.any { it in all }
}

internal data class RedactedNotice(val title: String, val text: String, val messages: List<Msg>)

/**
 * Esconde código de verificação no título, no corpo e em cada mensagem.
 * Com [showSensitive] ligado, devolve o texto original.
 */
internal fun redactNotice(title: String, text: String, messages: List<Msg>, showSensitive: Boolean): RedactedNotice {
    if (showSensitive) return RedactedNotice(title, text, messages)
    val combined = isSensitiveText("$title $text")
    return RedactedNotice(
        title = redactSecretField(title, combined),
        text = redactSecretField(text, combined),
        messages = messages.map { m ->
            if (isSensitiveText(m.text) || isSensitiveText("${m.sender} ${m.text}")) m.copy(text = SENSITIVE_PLACEHOLDER) else m
        },
    )
}

/** Redige o campo se ele sozinho é sensível, ou se o conjunto é e este campo carrega os dígitos. */
private fun redactSecretField(field: String, combinedSensitive: Boolean): String {
    if (isSensitiveText(field)) return SENSITIVE_PLACEHOLDER
    if (combinedSensitive && sensitiveDigits.containsMatchIn(field)) return SENSITIVE_PLACEHOLDER
    return field
}

/** Minutos antes de dormir em que o aviso ainda pode aparecer (o laço não acorda no segundo exato). */
internal const val BEDTIME_LEAD_MIN = 30
internal const val BEDTIME_WINDOW_MIN = 5

internal fun inBedtimeWindow(minutesToBed: Int): Boolean =
    minutesToBed in (BEDTIME_LEAD_MIN - BEDTIME_WINDOW_MIN)..BEDTIME_LEAD_MIN

/** Passos do sensor só enquanto a ilha mostra saúde ou passos. */
internal fun wantsStepCounter(s: Settings): Boolean =
    s.homeHealth || s.idleStyle == IdleStyle.Health || s.idleStyle == IdleStyle.Steps

internal const val PENDING_MAX = 3
internal const val PENDING_MAX_AGE_MS = 20_000L

/** Fila curta: o aviso mais novo entra e o mais antigo sai quando passa de [max]. */
internal fun <T> rememberPending(queue: List<Pair<T, Long>>, item: T, now: Long, max: Int = PENDING_MAX): List<Pair<T, Long>> {
    val next = queue + (item to now)
    return if (next.size <= max) next else next.takeLast(max)
}

/** Próximo aviso ainda dentro do prazo, e o restante da fila. Null se todos expiraram. */
internal fun <T> nextPending(queue: List<Pair<T, Long>>, now: Long, maxAge: Long = PENDING_MAX_AGE_MS): Pair<T, List<Pair<T, Long>>>? {
    val fresh = queue.filter { now - it.second < maxAge }
    val first = fresh.firstOrNull() ?: return null
    return first.first to fresh.drop(1)
}

/**
 * Download concluído: chegou a 100%, ou sumiu depois de passar de 90% com texto de sucesso.
 * Sumir sozinho (cancelar, falha) não conta.
 */
internal fun downloadFinished(
    progressedToComplete: Boolean,
    disappeared: Boolean,
    maxSeen: Float,
    lastTitle: String,
    lastText: String,
): Boolean {
    if (progressedToComplete) return true
    if (!disappeared) return false
    val blob = "$lastTitle $lastText".lowercase()
    val failed = listOf("cancel", "falhou", "failed", "erro", "error", "interromp").any { it in blob }
    if (failed) return false
    if (maxSeen >= 1f) return true
    val ok = listOf("concluíd", "concluid", "completed", "instalado", "installed", "sucesso", "success", "baixado", "downloaded")
    return maxSeen >= 0.9f && ok.any { it in blob }
}

/** CallStyle: 1 recebida, 2 em andamento, 3 triagem. */
internal fun ongoingMeetingIsCall(actionTitles: List<String>, callType: Int): Boolean {
    if (callType == 1 || callType == 2 || callType == 3) return true
    return actionTitles.any { actionLooksLikeCallControl(it) }
}

internal fun actionLooksLikeCallControl(title: String): Boolean {
    val t = title.trim().lowercase()
    val words = listOf(
        "atender", "aceitar", "answer", "accept",
        "recusar", "rejeitar", "decline", "reject",
        "desligar", "encerrar", "hang up", "end call",
    )
    if (words.any { w -> t == w || t.startsWith("$w ") || t.endsWith(" $w") }) return true
    return t == "sair" || t == "leave" || t == "sair da chamada" || t == "leave call" || t == "leave meeting"
}

internal fun sha256Hex(bytes: ByteArray): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * [expected] vazio (version.json antigo, sem o campo) aceita o arquivo.
 * Com hash publicado, só instala se os dois forem iguais.
 */
internal fun hashMatches(expected: String?, actualHex: String): Boolean {
    if (expected.isNullOrBlank()) return true
    return expected.trim().equals(actualHex, ignoreCase = true)
}
