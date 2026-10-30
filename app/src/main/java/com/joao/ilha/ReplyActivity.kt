package com.joao.ilha

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Caixa de resposta rápida aberta pelo botão "Responder" de uma notificação na ilha. */
class ReplyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        val target = IslandBus.pendingReply
        if (target == null) {
            finish()
            return
        }
        setContent {
            IlhaTheme(Prefs.state.value.appTheme) {
                val p = LocalPalette.current
                var text by remember { mutableStateOf("") }
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable { finish() }.imePadding(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(p.card)
                            .clickable(enabled = false) {}.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(target.title, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = text, onValueChange = { text = it },
                            placeholder = { Text("Mensagem") },
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = { finish() }, colors = ButtonDefaults.buttonColors(containerColor = p.separator, contentColor = p.text)) { Text("Cancelar") }
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = { if (text.isNotBlank()) send(target, text) },
                                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            ) { Text("Enviar") }
                        }
                    }
                }
            }
        }
    }

    private fun send(t: PendingReply, text: String) {
        val input = t.action.remoteInput ?: return finish()
        val intent = Intent()
        RemoteInput.addResultsToIntent(arrayOf(input), intent, Bundle().apply { putCharSequence(input.resultKey, text) })
        runCatching { t.action.intent.send(this, 0, intent) }
        IslandBus.pendingReply = null
        finish()
    }
}

data class PendingReply(val title: String, val action: NotifAction)
