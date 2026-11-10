# Roadmap

The TV app reaches v1.0 in about 16 to 22 working weeks, the Android phone app about 4 weeks after that, and iOS last. Durations are estimates. Phase 1 matters most: it measures on the real TV what plays, and the size of the player work depends on that.

## Decisions so far

| Decision | Choice |
|---|---|
| Platform | Google TV first; Android phone, iOS and others later |
| Distribution | Sideload only (no Play Store) |
| Language | Kotlin Multiplatform: shared core, Compose for TV app; iOS reuses the core |
| Player | Media3 (hardware, HDR, passthrough); libmpv as the second engine; one self-built LGPL FFmpeg shared by Media3's FFmpeg extension and mpv in the product |
| Central database | Supabase project of its own, separate from the attendance system |
| API | Versioned REST on Cloudflare Workers, OpenAPI 3.1 spec, generated clients |
| Test hardware | TCL C6K, soundbar on HDMI eARC |
| Metadata and tracking | TMDB enrichment, IMDb ratings via MDBList, Simkl tracking per profile; all keys server-side ([spec](metadata-and-tracking.md)) |

## Phases

| # | Phase | Weeks | Exit criteria |
|---|---|---|---|
| 0 | Setup: repo, CI APK builds, signing keys, Supabase and Worker projects, developer verification | 0.5 to 1 | CI-built APK installs and shows on the Google TV home screen |
| 1 | Player test on the C6K and soundbar ([plan](phase1-player-test.md)) | 1 to 2 | Pass/fail table; libmpv in or out; default player rules |
| 2 | Database and API v1: accounts, devices, profiles (PIN, kids), versioned settings, addons, library, progress, QR sign-in, app updates, enriched metadata (TMDB, MDBList) and Simkl linking with server-held keys | 1.5 to 2 | Every endpoint tested; full flow works with `curl` |
| 3 | Shared core: addon protocol client, offline database (SQLDelight), sync engine, playback rules, stream ranking | 1.5 to 2 | Sign-in, sync, browse and progress run headless |
| 4 | TV screens: profiles, sign-in, Home, Details, Search, Library, addon manager (QR to a phone page), Settings | 3 to 4 | Browsable alpha; home usable within about 1.5 s of a cold start |
| 5 | Player: controls, track menus, subtitles, frame-rate matching, passthrough with the Dolby Digital 5.1 fallback, auto engine fallback, resume, next episode, Google TV Continue watching row | 3 to 4 | Feature-complete beta; phase 1 files pass at least as well |
| 6 | Hardening: in-app updater, signed per-ABI releases, crash reporting, profiling, 3 to 5 beta users, install guide | 2 to 3 | v1.0 with no open crash or playback blocker |
| 7 | Android phone app, iOS (distribution to decide first), Trakt sync, web dashboard, Apple TV | later | per item |

## Milestones

| Milestone | Week | Proof |
|---|---|---|
| M0 CI-built app on the TV | 1 | APK installs and shows on the home screen |
| M1 Codec and audio table | 3 | Results for the C6K and soundbar |
| M2 API live | 5 | Sign-in and sync work with `curl` |
| M3 Browsable alpha | 11 | Browse, search, details on the TV |
| M4 Beta | 15 | Full player, beta testers onboarded |
| M5 v1.0 | 17 to 22 | Released with the in-app updater |

## Risks

| Risk | Mitigation |
|---|---|
| Google's developer verification reaches Google TV and blocks unverified sideloads (from 2026 in some countries, global 2027; TV scope not announced) | Register early with the release key |
| No 4K software decoding fast enough for AV1/VVC | Phase 1 measures it; the app blocks those streams with a message |
| Dolby Vision profile 7 unsupported | Fall back to the HDR10 base layer and say so |
| Soundbar lacks DTS or lossless formats | Fallback chain: bitstream, multichannel PCM, Dolby Digital 5.1, stereo |
| GPL obligations from the prebuilt libmpv build | Decide before v1.0: publish source, or build LGPL versions in CI |
| Release key lost | Offline backups; cloud sync limits the damage |
