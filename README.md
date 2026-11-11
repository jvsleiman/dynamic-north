# Ilha

App Android com uma ilha preta em volta da câmera frontal que cresce para mostrar
notificações, o player de música e o carregamento. Código próprio, escrito do zero.

- `IslandService` — serviço de acessibilidade que desenha a ilha (overlay) e acompanha a câmera.
- `NotifListener` — lê notificações e a sessão de mídia ativa.
- `IslandUi` — a ilha em Jetpack Compose.
- `MainActivity` — permissões e ajustes.

O GitHub Actions compila um APK sem assinatura (branch `builds`). Com os segredos `KEYSTORE_BASE64` (o arquivo `ilha-release.jks` em base64) e `KEYSTORE_PASSWORD` cadastrados no repositório, ele também assina e publica no branch `releases` quando o `versionCode` muda; a primeira linha de `notas-da-versao.txt` vira as notas da versão. Sem os segredos, a assinatura continua sendo feita fora, com `publicar.sh`.

O `version.json` do branch `releases` traz `sha256` (hexadecimal do APK já assinado), gravado pelo GitHub Actions e pelo `publicar.sh`. O botão Atualizar confere esse hash antes de abrir o instalador. Um `version.json` antigo, sem `sha256`, continua valendo: o app instala como antes e o Android ainda exige a mesma chave da instalação atual. A chave não fica neste repositório.
