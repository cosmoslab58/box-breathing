#!/usr/bin/env bash
# Build a signed release APK into dist/. The keystore password lives in ~/.secrets/box-breathing.env
# and the key in ~/.secrets/box-breathing.jks; both are created on first build and must be kept,
# or later builds won't install over this one.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
secrets=$HOME/.secrets
if [ ! -f "$secrets/box-breathing.env" ]; then
  umask 077; echo "KEYSTORE_PASSWORD=$(openssl rand -hex 24)" > "$secrets/box-breathing.env"
fi
set -a; . "$secrets/box-breathing.env"; set +a
docker build -q -t box-breathing-build "$here" >/dev/null
if [ ! -f "$secrets/box-breathing.jks" ]; then
  docker run --rm -u "$(id -u):$(id -g)" -v "$secrets:/keys" box-breathing-build \
    keytool -genkeypair -keystore /keys/box-breathing.jks -alias boxbreathing -keyalg RSA -keysize 3072 \
      -validity 36500 -storepass "$KEYSTORE_PASSWORD" -keypass "$KEYSTORE_PASSWORD" \
      -dname "CN=Box Breathing, O=Cosmos Lab"
  chmod 600 "$secrets/box-breathing.jks"
fi
mkdir -p "$here/dist"
# Docker creates the cache volume root-owned; the build runs as the invoking user.
docker run --rm -v box-breathing-gradle:/gradle box-breathing-build chown "$(id -u):$(id -g)" /gradle
# The source is copied inside the container so build output never lands in the tree.
docker run --rm -u "$(id -u):$(id -g)" \
  -e HOME=/tmp -e GRADLE_USER_HOME=/gradle -e KEYSTORE_PASSWORD \
  -e KEYSTORE_FILE=/keys/box-breathing.jks \
  -v "$here:/src:ro" -v "$secrets/box-breathing.jks:/keys/box-breathing.jks:ro" \
  -v box-breathing-gradle:/gradle -v "$here/dist:/out" box-breathing-build \
  bash -c 'cp -r /src /tmp/w && cd /tmp/w && gradle -q --no-daemon assembleRelease && cp app/build/outputs/apk/release/app-release.apk /out/box-breathing.apk'
ls -l "$here/dist/box-breathing.apk"
