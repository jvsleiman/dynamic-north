# Ilha

App Android com uma ilha preta em volta da câmera frontal que cresce para mostrar
notificações, o player de música e o carregamento. Código próprio, escrito do zero.

- `IslandService` — serviço de acessibilidade que desenha a ilha (overlay) e acompanha a câmera.
- `NotifListener` — lê notificações e a sessão de mídia ativa.
- `IslandUi` — a ilha em Jetpack Compose.
- `MainActivity` — permissões e ajustes.

O GitHub Actions compila um APK sem assinatura (branch `builds`), que é assinado fora do repositório.
