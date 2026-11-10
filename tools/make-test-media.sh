#!/usr/bin/env bash
# Generates the synthetic part of the phase 1 test set with FFmpeg, plus index.json for the
# player test app. Formats FFmpeg cannot create (Dolby Vision, HDR10+ dynamic metadata, Atmos,
# DTS-HD MA, DTS:X) are listed in docs/phase1-player-test.md with where to get real samples.
#
# Usage: tools/make-test-media.sh [output-dir]      (default: ./test-media)
#   DURATION=20   seconds per clip
#   QUICK=1       640x360 instead of 4K, for a fast smoke test of the script itself
#
# Then serve it:  cd test-media && python3 -m http.server 8000
set -euo pipefail

OUT="${1:-test-media}"
DUR="${DURATION:-20}"
if [[ "${QUICK:-0}" == "1" ]]; then UHD="640x360"; FHD="640x360"; else UHD="3840x2160"; FHD="1920x1080"; fi
mkdir -p "$OUT"
FF=(ffmpeg -hide_banner -loglevel error -y)
ENCODERS="$(ffmpeg -hide_banner -encoders 2>/dev/null)"
has() { grep -qE "^ [A-Z.]{6} $1 " <<<"$ENCODERS"; }

ITEMS=()
item() { # file title group video audio expect
  ITEMS+=("$(printf '{"file":"%s","title":"%s","group":"%s","video":"%s","audio":"%s","expect":"%s"}' "$1" "$2" "$3" "$4" "$5" "$6")")
}
skip() { echo "  skipped $1: encoder $2 not in this FFmpeg build" >&2; }

# HDR10 static metadata (BT.2020, PQ, 1000-nit mastering display, MaxCLL 1000 / MaxFALL 400).
MD="G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1)"
HDRTAGS=(-color_primaries bt2020 -color_trc smpte2084 -colorspace bt2020nc)
MOTION="testsrc2=size=${UHD}:rate=24000/1001:duration=${DUR}"

echo "Video (${UHD})"
if has libx265; then
  "${FF[@]}" -f lavfi -i "$MOTION" -pix_fmt yuv420p10le -c:v libx265 -preset ultrafast -tag:v hvc1 "${HDRTAGS[@]}" \
    -x265-params "log-level=error:hdr10=1:repeat-headers=1:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:master-display=${MD}:max-cll=1000,400" \
    "$OUT/video-hevc-10bit-hdr10.mp4"
  item video-hevc-10bit-hdr10.mp4 "HEVC 10-bit HDR10" "Video" "HEVC Main10, PQ" "none" "TV switches to HDR10, decoder HARDWARE, 0 dropped frames"
  "${FF[@]}" -f lavfi -i "$MOTION" -pix_fmt yuv420p -c:v libx265 -preset ultrafast -tag:v hvc1 -x265-params log-level=error "$OUT/video-hevc-8bit-sdr.mp4"
  item video-hevc-8bit-sdr.mp4 "HEVC 8-bit SDR" "Video" "HEVC Main" "none" "Plays, decoder HARDWARE"
else skip HEVC libx265; fi

if has libsvtav1; then
  "${FF[@]}" -f lavfi -i "$MOTION" -pix_fmt yuv420p10le -c:v libsvtav1 -preset 12 "${HDRTAGS[@]}" \
    -svtav1-params "mastering-display=${MD//[()]/}:content-light=1000,400" "$OUT/video-av1-10bit-hdr10.mkv" 2>/dev/null \
  || "${FF[@]}" -f lavfi -i "$MOTION" -pix_fmt yuv420p10le -c:v libsvtav1 -preset 12 "${HDRTAGS[@]}" "$OUT/video-av1-10bit-hdr10.mkv"
  item video-av1-10bit-hdr10.mkv "AV1 10-bit HDR10" "Video" "AV1 Main 10-bit, PQ" "none" "TV switches to HDR10, decoder HARDWARE, 0 dropped frames"
elif has libaom-av1; then
  "${FF[@]}" -f lavfi -i "$MOTION" -pix_fmt yuv420p10le -c:v libaom-av1 -cpu-used 8 -row-mt 1 "${HDRTAGS[@]}" "$OUT/video-av1-10bit-hdr10.mkv"
  item video-av1-10bit-hdr10.mkv "AV1 10-bit HDR10" "Video" "AV1 Main 10-bit, PQ" "none" "TV switches to HDR10, decoder HARDWARE, 0 dropped frames"
else skip AV1 "libsvtav1/libaom-av1"; fi

if has libvpx-vp9; then
  "${FF[@]}" -f lavfi -i "$MOTION" -pix_fmt yuv420p10le -c:v libvpx-vp9 -profile:v 2 -deadline realtime -cpu-used 8 -row-mt 1 -b:v 8M "${HDRTAGS[@]}" "$OUT/video-vp9-10bit-hdr10.webm"
  item video-vp9-10bit-hdr10.webm "VP9 profile 2 HDR10" "Video" "VP9 10-bit, PQ" "none" "TV switches to HDR10, decoder HARDWARE"
else skip VP9 libvpx-vp9; fi

if has libx264; then
  "${FF[@]}" -f lavfi -i "testsrc2=size=${FHD}:rate=24000/1001:duration=${DUR}" -pix_fmt yuv420p10le -c:v libx264 -preset ultrafast -profile:v high10 "$OUT/video-h264-hi10.mkv" 2>/dev/null \
    && item video-h264-hi10.mkv "H.264 Hi10 (anime encodes)" "Video" "H.264 High 10" "none" "Usually no hardware decoder: expect SOFTWARE and check for dropped frames" \
    || echo "  skipped H.264 Hi10: this libx264 has no 10-bit support" >&2
else skip "H.264 Hi10" libx264; fi

if has libvvenc; then
  "${FF[@]}" -f lavfi -i "testsrc2=size=${FHD}:rate=24000/1001:duration=${DUR}" -pix_fmt yuv420p10le -c:v libvvenc -preset faster "$OUT/video-vvc-1080p.mkv"
  item video-vvc-1080p.mkv "VVC (H.266) 1080p" "Video" "VVC Main10" "none" "mpv SW only. Pass = plays with few dropped frames"
  "${FF[@]}" -f lavfi -i "$MOTION" -pix_fmt yuv420p10le -c:v libvvenc -preset faster "$OUT/video-vvc-4k.mkv"
  item video-vvc-4k.mkv "VVC (H.266) 4K" "Video" "VVC Main10" "none" "mpv SW only. Expected to fail to keep up; record dropped frames"
else skip VVC libvvenc; fi

echo "Frame rates (${FHD})"
if has libx264; then
  for fr in "24000/1001:23.976" "24:24" "25:25" "30000/1001:29.97" "50:50" "60000/1001:59.94"; do
    rate="${fr%%:*}"; label="${fr##*:}"
    "${FF[@]}" -f lavfi -i "testsrc2=size=${FHD}:rate=${rate}:duration=${DUR}" -pix_fmt yuv420p -c:v libx264 -preset ultrafast "$OUT/fps-${label}.mp4"
    item "fps-${label}.mp4" "Motion at ${label} fps" "Frame rate" "H.264 ${label} fps" "none" "Display shows a ${label}-compatible refresh rate; motion is smooth with no regular stutter"
  done
fi

echo "Audio"
# Channel walk: a tone moves through each speaker in order, one second each (LFE gets 60 Hz).
walk() { # channels layout
  local n=$1 expr="" i f
  for ((i = 0; i < n; i++)); do
    f=440; [[ "$2" == "5.1" && $i == 3 ]] && f=60; [[ "$2" == "7.1" && $i == 3 ]] && f=60
    expr+="${expr:+|}if(eq(mod(floor(t),$n),$i),0.5*sin(2*PI*$f*t),0)"
  done
  echo "aevalsrc='${expr}':c=$2:s=48000:d=${DUR}"
}
BLACK="color=c=black:s=1280x720:r=24:d=${DUR}"
audio() { # file codec-args title audio-desc expect channels layout
  local file=$1 args=$2 title=$3 desc=$4 expect=$5 ch=$6 layout=$7
  # shellcheck disable=SC2086
  if "${FF[@]}" -f lavfi -i "$BLACK" -f lavfi -i "$(walk "$ch" "$layout")" -map 0:v -map 1:a -c:v libx264 -preset ultrafast -tune stillimage $args -shortest "$OUT/$file" 2>/dev/null; then
    item "$file" "$title" "Audio" "black video" "$desc" "$expect"
  else
    echo "  skipped $title: encoder failed" >&2
  fi
}
audio audio-ac3-5.1.mkv "-c:a ac3 -b:a 640k" "Dolby Digital 5.1" "AC-3 5.1 640 kb/s" "Soundbar shows Dolby Digital; tone walks L, R, C, LFE, SL, SR" 6 5.1
audio audio-eac3-5.1.mkv "-c:a eac3 -b:a 768k" "Dolby Digital Plus 5.1" "E-AC-3 5.1" "Soundbar shows Dolby Digital Plus" 6 5.1
audio audio-dts-5.1.mkv "-strict -2 -c:a dca -b:a 1509k" "DTS 5.1 (core)" "DTS core 5.1" "Soundbar shows DTS, or the app reports a fallback (PCM or Dolby Digital 5.1)" 6 5.1
audio audio-truehd-5.1.mkv "-strict -2 -c:a truehd" "Dolby TrueHD 5.1" "TrueHD 5.1 (no Atmos)" "Soundbar shows TrueHD / Dolby Audio, or the app reports a fallback" 6 5.1
audio audio-flac-7.1.mkv "-c:a flac" "FLAC 7.1" "FLAC 7.1 lossless" "Multichannel PCM: tone reaches all 8 positions the soundbar has" 8 7.1
audio audio-pcm-7.1.mkv "-c:a pcm_s24le" "PCM 7.1 24-bit" "LPCM 7.1" "Multichannel PCM: tone reaches every speaker" 8 7.1
audio audio-aac-5.1.mp4 "-c:a aac -b:a 384k" "AAC 5.1" "AAC-LC 5.1" "Decoded to multichannel PCM (or Dolby Digital 5.1 if the bar only takes stereo PCM)" 6 5.1
has libopus && audio audio-opus-5.1.mkv "-c:a libopus -b:a 384k -mapping_family 1" "Opus 5.1" "Opus 5.1" "Decoded to multichannel PCM" 6 5.1

{
  echo '{"version":1,"items":['
  for i in "${!ITEMS[@]}"; do printf '%s%s\n' "${ITEMS[$i]}" "$([[ $i -lt $((${#ITEMS[@]} - 1)) ]] && echo ,)"; done
  echo ']}'
} > "$OUT/index.json"

echo "Wrote ${#ITEMS[@]} files and index.json to $OUT"
echo "Add real Dolby Vision / Atmos / DTS-HD / DTS:X samples to $OUT/extra/ (see docs/phase1-player-test.md); the app lists them from the directory if you delete index.json, or add them to it."
