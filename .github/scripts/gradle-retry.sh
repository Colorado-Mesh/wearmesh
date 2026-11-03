#!/usr/bin/env bash
# Runs ./gradlew with the arguments given, its output in build.log. If it fails while
# downloading (Maven Central's "429 Too Many Requests", a timeout, a reset connection, or
# Robolectric fetching an Android jar), it waits and tries once more; any other failure is
# reported at once. Used by the CI and Release workflows.
set -o pipefail

network_failure() {
  grep -qE 'Received status code (429|5[0-9][0-9])|Could not (GET|HEAD|download)|Connection reset|Read timed out|connect timed out|MavenArtifactFetcher|UnknownHostException' build.log
}

./gradlew "$@" 2>&1 | tee build.log
status=$?
if [ $status -ne 0 ] && network_failure; then
  echo "::warning title=Download failed::A download failed (see above); trying again in 60 s."
  sleep 60
  ./gradlew "$@" 2>&1 | tee build.log
  status=$?
fi
exit $status
