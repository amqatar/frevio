# Phase 1: player test on the TCL C6K

Goal: a pass/fail table showing what this TV and soundbar actually play, before any app screens are built. The results decide whether libmpv ships in v1, which formats go to which player, and which formats the app blocks with a clear message.

## 1. One-time TV setup

1. **Developer options:** Settings > System > About, scroll to *Android TV OS build*, press OK on it seven times.
2. **Network debugging:** Settings > System > Developer options, turn on *USB debugging* (on TCL this also allows network ADB; some firmware names it *Network debugging*). Note the TV's IP address under Settings > Network.
3. **Audio:** turn on eARC and HDMI control (T-Link); set digital audio output to *Auto* or *Bitstream/Passthrough* (not PCM); set Surround sound to *Auto*. Menu names vary by firmware.

## 2. Install the test app

Every push builds an APK in GitHub Actions (repo > Actions > Build > latest run > Artifacts). Download and unzip it on a computer with `adb` installed, then:

```sh
adb connect <tv-ip>:5555        # accept the prompt on the TV the first time
adb install -r tv-debug.apk
```

Every test build is signed with the same debug key, so `-r` installs over the previous one and keeps your results. The app appears in the Google TV *Apps* row as **Marquee Player Test**.

## 3. Serve the test files

On a computer on the same network:

```sh
tools/make-test-media.sh            # writes ./test-media (about 19 files, 20 s each)
cd test-media && python3 -m http.server 8000
```

In the app, enter `http://<computer-ip>:8000/` and press **Load**. Or set it from the computer:

```sh
adb shell am start -n com.amqatar.marquee.tv/.MainActivity --es base http://<computer-ip>:8000/
```

### Real-world samples FFmpeg can't generate

Put these in `test-media/extra/` and add them to `index.json`, or delete `index.json` and the app lists every file in the folder instead. The Kodi wiki's [samples page](https://kodi.wiki/view/Samples) links test clips for most of them; check each link still works and that you're allowed to use the file.

| Needed | Why |
|---|---|
| Dolby Vision profile 5 (MP4) | Streaming-style DV |
| Dolby Vision profile 8.1 (MKV/MP4) | DV with HDR10 fallback layer |
| Dolby Vision profile 7 (MKV, from UHD Blu-ray) | Dual-layer; expected to fall back to HDR10 |
| HDR10+ (HEVC with dynamic metadata) | Checks HDR10+ handover |
| E-AC-3 JOC (Dolby Atmos, streaming) | Most common Atmos on a soundbar |
| TrueHD Atmos | Blu-ray Atmos; highest passthrough bandwidth |
| DTS-HD MA 5.1/7.1 | Lossless DTS |
| DTS:X | Object DTS |
| 4K HEVC remux, 60 to 80 Mb/s | Network and decoder headroom |

## 4. Run the tests

For each file, play it with **Media3** first. Use **mpv HW** when Media3 fails, and **mpv SW** for VVC.

The overlay shows the engine, the decoder and whether it is HARDWARE or SOFTWARE, the HDR mode, dropped frames, the display mode, what audio came in and what was sent out (*Bitstream: …* or *Decoded: N-ch PCM*). Press Up to hide it.

Press Back to rate the run. **Look at the soundbar's format indicator first** (display, LEDs or its phone app). The soundbar is the only proof of what arrived.

Settings on the home screen, to repeat runs where they matter:

| Setting | Values | When to change it |
|---|---|---|
| Frame rate | Off / Seamless only / Switch display mode | Run the frame-rate clips under *Switch* and *Off* |
| Tunneling | Off / On | Repeat the 4K HDR clips with it on; it can fix AV sync and judder on TVs |
| mpv audio | Decode / Passthrough / To Dolby Digital 5.1 | For DTS files, run *To Dolby Digital 5.1*. This is the fallback test for a soundbar that lacks DTS |

## 5. Send the results back

Either photograph the Results screen, or pull the report file and attach it to a GitHub issue:

```sh
adb pull /sdcard/Android/data/com.amqatar.marquee.tv/files/report.json
```

The report includes the device panel: what the soundbar advertises over eARC, which formats Android will bitstream right now, display modes and HDR types, RAM and 32/64-bit.

## What the results decide

| Result | Decision |
|---|---|
| Media3 passes everything except VVC | libmpv only for VVC and odd files, or dropped from v1 to save about 60 MB |
| Media3 fails on specific containers or codecs that mpv HW plays | libmpv ships as the automatic fallback |
| DTS fails to bitstream and *To Dolby Digital 5.1* works | Transcoding becomes the default DTS path on Dolby-only soundbars |
| TrueHD fails but E-AC-3 JOC passes | The player prefers the E-AC-3 companion track when a file has one (already in `shared/`) |
| 4K VVC or 4K software AV1 drops frames | The app blocks those streams on this device with a message |
| *Switch display mode* fixes 23.976 judder that *Off* shows | Display mode switching is on by default |

## Licence note

The prebuilt libmpv-android library used here is a GPL-3.0 build. Phase 1 uses Media3 with platform decoders only; Media3's FFmpeg extension returns in the product build, sharing one FFmpeg with mpv. That's fine for an internal test app. Before v1.0 you need to decide between:
- publishing the app's source under GPL-3.0 to everyone who gets the APK, or
- building LGPL versions of FFmpeg and mpv ourselves in CI, which takes more build work but keeps the app's source private.
