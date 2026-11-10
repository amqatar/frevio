# Frevio

Frevio is a lightweight media hub for Google TV: browses movie and series metadata from Stremio-protocol addons, plays video and audio with Media3 and libmpv, and syncs profiles and settings to a central database through a versioned API. Distributed by sideloading only.

**Current phase: 1, player test.** The `tv` app is a test harness, not the product. It measures what the target TV and soundbar play before the real screens are built. See [docs/phase1-player-test.md](docs/phase1-player-test.md).

## Layout

| Path | What |
|---|---|
| `shared/` | Kotlin Multiplatform core shared by every app: audio routing (bitstream, PCM, Dolby Digital 5.1 fallback, stereo), frame-rate mode selection, test and report models. Android and JVM targets; iOS comes later |
| `tv/` | Google TV app (Compose for TV, Media3, libmpv). Currently the player test |
| `tools/make-test-media.sh` | Generates the synthetic test set and `index.json` |
| `docs/` | Roadmap and phase plans |
| `api/`, `supabase/`, `openapi/` | Arrive in phase 2 |

## Build

CI (`.github/workflows/build.yml`) runs the shared tests and builds the debug APK on every push. Download the APK from the run's Artifacts.

Locally, with JDK 17 and the Android SDK:

```sh
./gradlew :shared:jvmTest :tv:assembleDebug
```

## Stack

Kotlin 2.4, Android Gradle Plugin 9.5, Compose for TV (`tv-material` 1.1), Media3 1.11.1, libmpv-android 1.0 (mpv 0.42, FFmpeg 9). Target SDK 36, min SDK 26.

See [docs/roadmap.md](docs/roadmap.md) for the full plan.
