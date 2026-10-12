#!/bin/sh
# Runs one command of biblio-glutton with the configuration of the image:
#
#   glutton                        the service (the default)
#   glutton crossref --input X     a load, or any other command, with its options
#   glutton --help                 the list of commands
#   glutton crossref --help        the options of one command
#
# The configuration file is the last argument of every command, so it is added here, also after
# --help: given a single argument, the application would start the service instead.
set -e

CONFIG="${GLUTTON_CONFIG:-/app/config/glutton.yml}"

# the reflective access FST and LMDB need on Java 17 and later, as in build.gradle
OPENS="--add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens java.base/java.text=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED \
--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens java.base/java.math=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED \
--add-opens java.base/java.util.concurrent=ALL-UNNAMED --add-opens java.base/java.util.stream=ALL-UNNAMED \
--add-opens java.base/java.time=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED \
--add-opens java.sql/java.sql=ALL-UNNAMED"

mkdir -p "${GLUTTON_STORAGE:-/data/db}" 2>/dev/null || true

COMMAND="${1:-server}"
[ $# -gt 0 ] && shift
# shellcheck disable=SC2086
exec java $JAVA_OPTS $OPENS -jar /app/lib/glutton.jar "$COMMAND" "$@" "$CONFIG"
