#!/bin/bash
# Assina o APK do branch `builds` e publica no branch `releases` (lido pelo botão Atualizar).
# Uso: ./publicar.sh "notas da versão"   (precisa da chave em /mnt/project-files/dynamic-north-keys)
set -euo pipefail
K=/mnt/project-files/dynamic-north-keys
PW=$(grep storePassword "$K/keystore.properties" | cut -d= -f2)
T=$(mktemp -d)
cd "$(dirname "$0")"
git fetch -q origin +builds:refs/remotes/origin/builds
git show origin/builds:out/Ilha-unsigned.apk > "$T/u.apk"
git show origin/builds:out/apksigner.jar > "$T/apksigner.jar"
java -jar "$T/apksigner.jar" sign --ks "$K/ilha-release.jks" --ks-key-alias ilha \
  --ks-pass pass:"$PW" --key-pass pass:"$PW" --out "$T/Ilha.apk" "$T/u.apk"
CODE=$(grep -o 'versionCode = [0-9]*' app/build.gradle.kts | grep -o '[0-9]*')
NAME=$(grep -o 'versionName = "[^"]*"' app/build.gradle.kts | cut -d'"' -f2)
cp "$T/Ilha.apk" /mnt/project-files/Ilha-android.apk
W="$T/rel"; git worktree add -q --detach "$W"
( cd "$W"
  git branch -D releases >/dev/null 2>&1 || true
  git checkout -q --orphan releases && git rm -rf -q --cached . && find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
  cp "$T/Ilha.apk" "Ilha-$NAME.apk"
  python3 -c "import json,sys;print(json.dumps({'versionCode':int(sys.argv[1]),'versionName':sys.argv[2],'apk':'Ilha-'+sys.argv[2]+'.apk','notes':sys.argv[3]},ensure_ascii=False,indent=2))" "$CODE" "$NAME" "${1:-}" > version.json
  git add -A && git commit -q -m "Ilha $NAME" && git push -q -f origin HEAD:releases )
git worktree remove --force "$W"
echo "Publicado Ilha $NAME ($CODE)"
