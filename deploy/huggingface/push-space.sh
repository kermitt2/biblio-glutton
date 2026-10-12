#!/bin/sh
# Creates or updates a Hugging Face Space running biblio-glutton, from this checkout.
#
#   deploy/huggingface/push-space.sh <user-or-org>/<space-name> [--public]
#
# Needs the hf command (pip install -U huggingface_hub) and a token with write access
# (hf auth login). The Space is private unless --public is given. It builds the Dockerfile of
# this repository, from the files it needs only, with the registries (ROR and the Crossref funder
# registry) loaded at start: see deploy/huggingface/README.md for what the Space serves.
set -e

SPACE="$1"
[ -n "$SPACE" ] || { echo "usage: $0 <user-or-org>/<space-name> [--public]" >&2; exit 2; }
VISIBILITY="--private"
[ "$2" = "--public" ] && VISIBILITY="--no-private"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

cd "$ROOT"
cp -r Dockerfile docker gradlew gradle settings.gradle build.gradle gradle.properties config "$STAGE"/
mkdir -p "$STAGE/src" && cp -r src/main "$STAGE/src/"
cp deploy/huggingface/README.md "$STAGE/README.md"
# a Space has neither the room for the bibliographical records nor an Elasticsearch: it serves the
# registries, loaded when it starts (its disk does not outlive a restart) and every month after
cat >> "$STAGE/Dockerfile" <<'SPACE'

# --- added for the Hugging Face Space
ENV REGISTRIES_UPDATE_ENABLED=true \
    JAVA_OPTS="-Xmx6g"
SPACE

hf repos create "$SPACE" --repo-type space --space-sdk docker $VISIBILITY --exist-ok
hf upload "$SPACE" "$STAGE" . --repo-type space \
    --commit-message "biblio-glutton $(git describe --tags --always 2>/dev/null || echo build)"
echo "Space: https://huggingface.co/spaces/$SPACE (building; the logs are on its page)"
