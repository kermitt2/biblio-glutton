#!/usr/bin/env bash
#
# Dump the identifiers of every ISTEX document (ISTEX id, corpus, DOI, ark, PMID, PII) from the
# ISTEX API, one JSON record per line on the standard output, as `./gradlew istex` reads them:
#
#   scripts/dump-istex-ids.sh | gzip > istexIds.all.gz
#   ./gradlew istex -Pinput=istexIds.all.gz
#
# Only the documents with a DOI are written, the others are not loaded anyway. Needs curl and jq.
#
# The API is read with a scroll, 1,000 documents per request, so a harvest cannot be resumed: a
# request that still fails after the retries ends the script with an error, and it has to be run
# again from the start.

set -euo pipefail

API="https://api.istex.fr/document/"
PAGE_SIZE=1000
# how long the API keeps the scroll between two requests; the retries of one request stay within it
SCROLL=5m

for tool in curl jq; do
    command -v "$tool" > /dev/null || { echo "$tool is needed" >&2; exit 1; }
done

fetch() {
    curl --silent --show-error --fail --compressed \
        --max-time 120 --retry 10 --retry-delay 5 --retry-max-time 240 --retry-all-errors \
        "$1"
}

url="${API}?q=*&output=id,corpusName,doi,ark,pmid,pii&size=${PAGE_SIZE}&scroll=${SCROLL}"
pages=0
seen=0
started=$SECONDS

while [ -n "$url" ]; do
    if ! page=$(fetch "$url"); then
        echo "The ISTEX API did not answer after page $pages ($seen documents): run the script again" >&2
        exit 1
    fi
    if ! jq -e '.hits | type == "array"' <<< "$page" > /dev/null 2>&1; then
        echo "Unexpected answer from the ISTEX API after page $pages:" >&2
        head -c 500 <<< "$page" >&2
        echo >&2
        exit 1
    fi

    jq -c '.hits[]
        | select((.doi // []) | length > 0)
        | {corpusName, istexId: .id, ark: (.ark // []), doi, pmid: (.pmid // []), pii: (.pii // [])}' <<< "$page"

    pages=$((pages + 1))
    seen=$((seen + $(jq '.hits | length' <<< "$page")))
    if [ $((pages % 100)) -eq 0 ]; then
        echo "$seen / $(jq '.total' <<< "$page") documents read in $((SECONDS - started))s" >&2
    fi

    url=$(jq -r 'if .noMoreScrollResults or (.hits | length) == 0 then "" else .nextScrollURI // "" end' <<< "$page")
done

echo "Done: $seen documents read in $((SECONDS - started))s" >&2
