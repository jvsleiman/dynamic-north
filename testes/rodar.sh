#!/usr/bin/env bash
# Bateria de testes do Notch num emulador Android (roda no GitHub Actions).
# Dispara cada recurso pelo caminho real do Android quando dá (notificação, carregador, bateria,
# volume, Não perturbe, desbloqueio, timer do relógio) e pelos exemplos internos quando não dá
# (Bluetooth, chamada, música, ao vivo). Tira um print por teste e procura travamentos.
set -u
PKG=com.joao.ilha
OUT=resultado
mkdir -p $OUT/prints
n=0
log() { echo "$*" | tee -a $OUT/resumo.txt; }
shot() { n=$((n+1)); f=$(printf "%02d-%s.png" $n "$1"); adb exec-out screencap -p > "$OUT/prints/$f"; log "print $f"; }
caso() { adb shell am broadcast -a com.joao.ilha.TESTE -n $PKG/.TestReceiver --es caso "$1" >/dev/null; }
fechar() { adb shell input tap $((W/2)) $((H*2/3)); sleep 1.2; }
alive() { adb shell pidof $PKG >/dev/null && echo vivo || echo MORTO; }

adb wait-for-device
adb shell settings put global window_animation_scale 1
adb shell cmd overlay enable com.android.internal.display.cutout.emulation.hole 2>/dev/null || true
adb install -r -g app/build/outputs/apk/debug/app-debug.apk | tee -a $OUT/resumo.txt
adb logcat -c
adb shell cmd notification allow_listener $PKG/$PKG.NotifListener
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.IslandService
adb shell settings put secure accessibility_enabled 1
SIZE=$(adb shell wm size | tail -1 | sed 's/.*: //'); W=${SIZE%x*}; H=${SIZE#*x}
log "tela ${W}x${H}"
adb shell input keyevent 224; adb shell wm dismiss-keyguard; adb shell input keyevent 3
# O emulador é lento na primeira abertura: espera a ilha aparecer e a animação de atualização passar.
for i in $(seq 1 30); do adb shell dumpsys window windows | grep -q "com.joao.ilha" && break; sleep 1; done
sleep 4
shot "animacao-atualizacao"
sleep 12
shot "ilha-parada"
log "serviço: $(alive)"

log "== Notificações (pelo Android) =="
adb shell cmd notification post -S messaging --conversation "Futebol" --message "Ana:Bora jogar hoje?" --message "Bia:Partiu" -t "Futebol" conversa "Partiu" >/dev/null
sleep 1.5; shot "notificacao-conversa"
sleep 6
adb shell cmd notification post -t "Pedido" simples "Saiu para entrega" >/dev/null
sleep 1.5; shot "notificacao-simples"
sleep 6
caso whatsapp; sleep 1.5; shot "teste-whatsapp"
sleep 6

log "== Carregador e bateria (pelo Android) =="
adb shell dumpsys battery unplug; sleep 3
adb shell dumpsys battery set ac 1; sleep 1.5; shot "carregador-ligado"
sleep 5
adb shell dumpsys battery unplug; sleep 1.5; shot "carregador-desligado"
sleep 5
adb shell dumpsys battery set level 9; sleep 2; shot "bateria-fraca"
sleep 5
adb shell dumpsys battery reset

log "== Volume, Não perturbe, desbloqueio (pelo Android) =="
adb shell input keyevent 24; sleep 1; shot "volume"
sleep 3
adb shell cmd notification set_dnd priority; sleep 1.5; shot "nao-perturbe-ligado"
sleep 3
adb shell cmd notification set_dnd off; sleep 1.5; shot "nao-perturbe-desligado"
sleep 3
adb shell input keyevent 223; sleep 2; adb shell input keyevent 224; sleep 1; adb shell wm dismiss-keyguard; sleep 1.2; shot "desbloqueio"
sleep 3

log "== Timer do relógio (pelo Android) =="
adb shell am start -a android.intent.action.SET_TIMER --ei android.intent.extra.alarm.LENGTH 300 --ez android.intent.extra.alarm.SKIP_UI true >/dev/null 2>&1
sleep 3; adb shell input keyevent 3; sleep 2; shot "timer-relogio"
adb shell dumpsys notification --noredact > $OUT/notificacoes.txt

log "== Exemplos internos =="
for c in bluetooth musica chamada chamada-ativa reuniao timer cronometro rota gravacao corrida treino download camera; do
  caso limpar; sleep 1
  caso $c; sleep 2; shot "$c"
done
log "== Pedido de comida (etapas) =="
caso limpar; sleep 1
for i in 1 2 3 4 5; do caso pedido; sleep 1.2; shot "pedido-aberto-$i"; sleep 3; shot "pedido-pequeno-$i"; done
caso limpar; sleep 1
caso carteira; sleep 0.3; shot "carteira-1"; sleep 0.5; shot "carteira-2"; sleep 0.9; shot "carteira-3"; sleep 1.5; shot "carteira-4"; sleep 1.5; caso limpar; sleep 1
caso musica; sleep 1; caso atividade; sleep 1.5; shot "musica-aberta"; fechar
caso limpar; sleep 1
caso principal; sleep 1.5; shot "ilha-principal"
for i in 1 2 3 4 5 6 7 8; do caso clima; sleep 1.2; shot "clima-$i"; done
fechar
caso controles; sleep 1.5; shot "controles"; fechar
log "== Toque na ilha =="
adb shell input tap $((W/2)) 40; sleep 1.5; shot "toque-na-ilha"; fechar
log "== Aviso com a ilha aberta (deve aparecer depois de fechar) =="
caso principal; sleep 1; caso bluetooth; sleep 1; fechar; sleep 1; shot "bluetooth-depois-de-fechar"

log "== Animações de atualização =="
fechar; caso limpar; sleep 2
caso intro; sleep 2; shot "notch-chuva"; sleep 5; shot "notch-mar"; sleep 5; shot "notch-penhasco"; sleep 5; shot "notch-letreiro"; sleep 5
caso cavalo; sleep 2; shot "cavalo"; sleep 3; shot "cavalo-titulo"; sleep 3
log "== Travamentos =="
log "serviço no fim: $(alive)"
adb logcat -d -b crash > $OUT/travamentos.txt
adb shell run-as $PKG cat files/ultimo-erro.txt > $OUT/ultimo-erro.txt 2>/dev/null || true
adb logcat -d | grep -i -E "joao.ilha|AndroidRuntime" > $OUT/log-app.txt || true
if grep -q "$PKG" $OUT/travamentos.txt; then log "TRAVOU: veja travamentos.txt"; else log "nenhum travamento"; fi
# animação NOTCH: 926 quadros
# pedido: altura da ilha aberta
# 1.0.5
