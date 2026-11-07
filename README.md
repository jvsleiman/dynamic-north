# Ilha

App Android com uma ilha preta em volta da câmera frontal que cresce para mostrar
notificações, o player de música e o carregamento. Código próprio, escrito do zero.

- `IslandService` — serviço de acessibilidade que desenha a ilha (overlay) e acompanha a câmera.
- `NotifListener` — lê notificações e a sessão de mídia ativa.
- `IslandUi` — a ilha em Jetpack Compose.
- `MainActivity` — permissões e ajustes.

O GitHub Actions compila um APK sem assinatura (branch `builds`), que é assinado fora do repositório com `publicar.sh`.

O `version.json` do branch `releases` pode trazer `sha256` (hexadecimal do APK já assinado). O botão Atualizar confere esse hash antes de abrir o instalador. Um `version.json` antigo, sem `sha256`, continua valendo: o app instala como antes e o Android ainda exige a mesma chave da instalação atual. A chave não fica neste repositório.
