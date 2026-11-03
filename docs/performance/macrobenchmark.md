# AgentDock Android Macrobenchmark

This benchmark measures Compose frame timing while a long draft is entered into the Home screen text field.

## Build

The benchmark target is a non-debuggable, minified `benchmark` app variant signed with the local debug key. It is profileable for shell tracing, and its Compose test tags are exposed as resource IDs only in this build type.

Build the target and instrumentation APKs:

```bash
./gradlew :app:assembleBenchmark :macrobenchmark:assembleBenchmark
```

The app and test module must both use the `benchmark` build variant. The app variant is non-debuggable, minified, signed with the local debug key, and marked `profileable`.

## Run on a physical Android device

Connect an Android device with USB debugging enabled, unlock it, keep it cool, and avoid running other heavy workloads during the measurement.

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

Run this command with a connected physical Android device. Do not run the Macrobenchmark suite from the generic emulator UI-test task: CI intentionally invokes only `:app:connectedDebugAndroidTest` so benchmark tests are never executed against a debuggable app variant on an emulator. Macrobenchmark can reject emulator or low-battery measurements because they do not represent stable end-user device performance.

## Results

Macrobenchmark writes frame-timing metrics, JSON output, and Perfetto traces beneath:

```
macrobenchmark/build/outputs/connected_android_test_additional_output/
```

Inspect the frame-duration and frame-overrun distributions, then open the generated Perfetto trace to identify whether the time is spent in UI thread work, rendering, scheduling, or other system activity.

A successful build or emulator UI test is not evidence of an FPS improvement. Record the device model, Android version, refresh rate, battery state, thermal state, app commit, and benchmark iteration results when comparing revisions.
