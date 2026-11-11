#!/usr/bin/env bash
# Manual-only: this builds benchmark APKs in an isolated package, never a production release.
set -euo pipefail
case "${BENCHMARK_MODE:-generate}" in generate|compare) ;; *) echo 'Choose generate or compare'; exit 1 ;; esac
./gradlew --no-daemon --max-workers=1 :startup-benchmark:connectedBenchmarkAndroidTest \
  -PstartupBenchmark=true \
  -Pandroid.testInstrumentationRunnerArguments.class=app.tgdrive.benchmark.StartupProfileGenerator
python3 scripts/import_startup_profiles.py --from-dir startup-benchmark/build/outputs/connected_android_test_additional_output
if [[ "${BENCHMARK_MODE:-generate}" == compare ]]; then
  ./gradlew --no-daemon --max-workers=1 :startup-benchmark:connectedBenchmarkAndroidTest \
    -PstartupBenchmark=true -PstartupMeasure=true \
    -Pandroid.testInstrumentationRunnerArguments.class=app.tgdrive.benchmark.StartupFrameBenchmarks
fi
