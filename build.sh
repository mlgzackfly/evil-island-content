#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
API_VERSION="${PAPER_API_VERSION:-1.20.4-R0.1-SNAPSHOT}"
REPO="https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api"
LIB="$ROOT/lib/paper-api-$API_VERSION.jar"
ENGINE_ROOT="${RPG_ENGINE_DIR:-$ROOT/../persistent-rpg-engine}"
ENGINE_JAR="$ENGINE_ROOT/build/persistent-rpg-engine.jar"

if [[ ! -x "$ENGINE_ROOT/build.sh" ]]; then
  echo "Persistent RPG Engine not found at $ENGINE_ROOT" >&2
  echo "Set RPG_ENGINE_DIR to the engine checkout path." >&2
  exit 1
fi

"$ENGINE_ROOT/build.sh"
CP="$LIB:$ENGINE_JAR"

mkdir -p "$ROOT/lib" "$ROOT/build/classes"

download_dep() {
  local group="$1"
  local artifact="$2"
  local version="$3"
  local base="${4:-https://repo.maven.apache.org/maven2}"
  local path="${group//.//}/$artifact/$version/$artifact-$version.jar"
  local out="$ROOT/lib/$artifact-$version.jar"
  if [[ ! -f "$out" ]]; then
    curl -fsSL "$base/$path" -o "$out"
  fi
  CP="$CP:$out"
}

if [[ ! -f "$LIB" ]]; then
  META="$(curl -fsSL "$REPO/$API_VERSION/maven-metadata.xml")"
  SNAPSHOT="$(printf '%s' "$META" | tr '\n' ' ' | sed -E 's#.*<snapshotVersion>[[:space:]]*<extension>jar</extension>[[:space:]]*<value>([^<]+)</value>.*#\1#')"
  if [[ -z "$SNAPSHOT" ]]; then
    echo "Cannot resolve Paper API snapshot for $API_VERSION" >&2
    exit 1
  fi
  curl -fsSL "$REPO/$API_VERSION/paper-api-$SNAPSHOT.jar" -o "$LIB"
fi

download_dep com.google.guava guava 32.1.2-jre
download_dep com.google.guava failureaccess 1.0.1
download_dep com.google.code.gson gson 2.10.1
download_dep net.md-5 bungeecord-chat '1.20-R0.2-deprecated+build.18' https://repo.papermc.io/repository/maven-public
download_dep org.yaml snakeyaml 2.2
download_dep org.joml joml 1.10.5
download_dep com.googlecode.json-simple json-simple 1.1.1
download_dep it.unimi.dsi fastutil 8.5.6
download_dep org.apache.logging.log4j log4j-api 2.17.1
download_dep org.slf4j slf4j-api 2.0.9
download_dep org.xerial sqlite-jdbc 3.46.1.0
download_dep org.checkerframework checker-qual 3.33.0
download_dep net.kyori adventure-api 4.16.0
download_dep net.kyori adventure-key 4.16.0
download_dep net.kyori adventure-text-minimessage 4.16.0
download_dep net.kyori adventure-text-serializer-gson 4.16.0
download_dep net.kyori adventure-text-serializer-json 4.16.0
download_dep net.kyori adventure-text-serializer-legacy 4.16.0
download_dep net.kyori adventure-text-serializer-plain 4.16.0
download_dep net.kyori adventure-text-logger-slf4j 4.16.0
download_dep net.kyori examination-api 1.3.0
download_dep net.kyori examination-string 1.3.0

rm -rf "$ROOT/build/classes" "$ROOT/build/EvilIsland.jar"
mkdir -p "$ROOT/build/classes"
cp -R "$ENGINE_ROOT/build/classes/." "$ROOT/build/classes/"

javac -Xlint:deprecation -encoding UTF-8 --release 17 -cp "$CP" -d "$ROOT/build/classes" $(find "$ROOT/src/main/java" -name '*.java')
cp -R "$ROOT/src/main/resources/." "$ROOT/build/classes/"

# SQLite JDBC is shaded so the Paper server does not need a separate library plugin.
(
  cd "$ROOT/build/classes"
  jar xf "$ROOT/lib/sqlite-jdbc-3.46.1.0.jar"
  rm -f META-INF/MANIFEST.MF META-INF/*.SF META-INF/*.RSA META-INF/*.DSA
  find META-INF -name module-info.class -delete
)

if [[ -d "$ROOT/src/test/java" ]]; then
  rm -rf "$ROOT/build/test-classes"
  mkdir -p "$ROOT/build/test-classes"
  javac -encoding UTF-8 --release 17 -cp "$ROOT/build/classes:$CP" \
    -d "$ROOT/build/test-classes" $(find "$ROOT/src/test/java" -name '*.java')
  java -ea -cp "$ROOT/build/classes:$ROOT/build/test-classes:$CP" tw.zack.evilisland.model.FormulaPathTest
  java -ea -cp "$ROOT/build/classes:$ROOT/build/test-classes:$CP" tw.zack.evilisland.model.WeaponSpeciesRulesTest
  java -ea -cp "$ROOT/build/classes:$ROOT/build/test-classes:$CP" tw.zack.evilisland.model.PatrolScalingTest
  java -ea -cp "$ROOT/build/classes:$ROOT/build/test-classes:$CP" tw.zack.evilisland.model.WorldEventStateTest
  java -ea -cp "$ROOT/build/classes:$ROOT/build/test-classes:$CP" tw.zack.evilisland.model.CampaignRulesTest
  java -ea -cp "$ROOT/build/classes:$ROOT/build/test-classes:$CP" tw.zack.evilisland.model.NpcRosterRulesTest
  java -ea -cp "$ROOT/build/classes:$ROOT/build/test-classes:$CP" tw.zack.evilisland.persistence.DatabaseIntegrationTest
  java -ea -cp "$ROOT/build/classes:$ROOT/build/test-classes:$CP" tw.zack.evilisland.persistence.DatabaseMigrationTest
fi

(
  cd "$ROOT/build/classes"
  jar cf "$ROOT/build/EvilIsland.jar" .
)

echo "$ROOT/build/EvilIsland.jar"
