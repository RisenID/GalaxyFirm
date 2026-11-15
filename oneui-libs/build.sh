#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK_DIR:-$HERE/../.oneui-src}"
REPO="${MAVEN_REPO:-$HOME/.m2/repository}"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 25 installation}"
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"

SESL_ANDROIDX=3305971d6c54d91d35c686e766be2d7beade42d0
SESL_MATERIAL=d7cb65d134be4dada94d9775d085f39b751f9597
ONEUI_DESIGN=1befedd193025f2271f741e10fd12bc0f101b17e

fetch() {
    local name=$1 rev=$2
    if [ ! -d "$WORK/$name/.git" ]; then
        git init -q "$WORK/$name"
        git -C "$WORK/$name" remote add origin "https://github.com/tribalfs/$name.git"
    fi
    git -C "$WORK/$name" fetch -q --depth 1 origin "$rev"
    git -C "$WORK/$name" checkout -q -f "$rev"
    git -C "$WORK/$name" clean -q -fdx -e local.properties
    git -C "$WORK/$name" apply "$HERE/patches/$name.patch"
    echo "sdk.dir=$ANDROID_HOME" > "$WORK/$name/local.properties"
}

gradle() {
    local dir=$1; shift
    (cd "$WORK/$dir" && ./gradlew --no-configuration-cache -q \
        -PghUsername=x -PghAccessToken=x \
        -Dmaven.repo.local="$REPO" \
        -Porg.gradle.java.installations.paths="$JAVA_HOME" \
        -Pkotlin.jvm.target.validation.mode=warning \
        "$@" -x lint)
}

mkdir -p "$WORK"
fetch sesl-androidx "$SESL_ANDROIDX"
fetch sesl-material-components-android "$SESL_MATERIAL"
fetch oneui-design "$ONEUI_DESIGN"

ANDROIDX_MODULES=(core customview coordinatorlayout drawerlayout appcompat recyclerview preference fragment
    viewpager viewpager2 swiperefreshlayout slidingpanelayout indexscroll picker-basic picker-app)

tasks=()
for m in "${ANDROIDX_MODULES[@]}"; do tasks+=(":$m:publishGprPublicationToMavenLocal"); done
gradle sesl-androidx -PuseLocalModules=true "${tasks[@]}"
gradle sesl-material-components-android -Dorg.gradle.configureondemand=true :lib:publishReleasePublicationToMavenLocal
gradle sesl-androidx -PuseLocalModules=true :picker-color:publishGprPublicationToMavenLocal
gradle oneui-design :oneui-design:publishMavenJavaPublicationToMavenLocal

echo "Published to $REPO"
