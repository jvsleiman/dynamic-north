# Ilha

App Android com uma ilha preta em volta da câmera frontal que cresce para mostrar
notificações, o player de música e o carregamento. Código próprio, escrito do zero.

- `IslandService` — serviço de acessibilidade que desenha a ilha (overlay) e acompanha a câmera.
- `NotifListener` — lê notificações e a sessão de mídia ativa.
- `IslandUi` — a ilha em Jetpack Compose.
- `MainActivity` — permissões e ajustes.

O GitHub Actions compila um APK sem assinatura (branch `builds`). Com os segredos `KEYSTORE_BASE64` (o arquivo `ilha-release.jks` em base64) e `KEYSTORE_PASSWORD` cadastrados no repositório, ele também assina e publica no branch `releases` quando o `versionCode` muda; a primeira linha de `notas-da-versao.txt` vira as notas da versão. Sem os segredos, a assinatura continua sendo feita fora, com `publicar.sh`.
