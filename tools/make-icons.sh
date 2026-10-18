#!/usr/bin/env bash
# Regenerate the launcher icons from the DeepSeek whale (tools/whale.svg).
#
# White background + black whale, matching the app's black/white theme.
# Produces legacy square/round mipmaps and the adaptive-icon layers used on
# Android 8+ (foreground PNGs + a flat background colour).
#
# Requires ImageMagick. Run from the repository root:
#   tools/make-icons.sh
set -euo pipefail

cd "$(dirname "$0")/.."
SVG=tools/whale.svg
RES=app/src/main/res
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

# Master render: black whale on transparency.
# png32 forces RGBA: without it ImageMagick flattens the transparency to white.
magick -background none -density 1200 "$SVG" -resize 1024x1024 png32:"$WORK/whale.png"

# Legacy icons: whale at 72% of the canvas, white background.
gen_legacy() { # dir size scale
    local dir=$1 size=$2 scale=$3
    local inner
    inner=$(python3 -c "print(int($size * $scale))")
    magick -size "${size}x${size}" xc:white \
        \( "$WORK/whale.png" -resize "${inner}x${inner}" \) \
        -gravity center -composite "$RES/$dir/ic_launcher.png"
    # Round variant: white disc on transparency with the whale drawn inside it.
    # 0.68 keeps the art's corners (half-diagonal = 0.68 * sqrt(2) / 2 = 0.48 of
    # the canvas) inside the 0.5 radius disc.
    local round_inner
    round_inner=$(python3 -c "print(int($size * 0.68))")
    magick -size "${size}x${size}" xc:none -fill white \
        -draw "circle $((size / 2)),$((size / 2)) $((size / 2)),1" \
        \( "$WORK/whale.png" -resize "${round_inner}x${round_inner}" \) \
        -gravity center -composite \
        png32:"$RES/$dir/ic_launcher_round.png"
}

# Adaptive foreground: 108dp canvas, whale inside the 66dp safe zone.
gen_adaptive() { # dir size scale
    local dir=$1 size=$2 scale=$3
    local inner
    inner=$(python3 -c "print(int($size * $scale))")
    mkdir -p "$RES/$dir"
    magick -size "${size}x${size}" xc:none \
        \( "$WORK/whale.png" -resize "${inner}x${inner}" \) \
        -gravity center -composite png32:"$RES/$dir/ic_launcher_foreground.png"
}

gen_legacy mipmap-mdpi 48 0.72
gen_legacy mipmap-hdpi 72 0.72
gen_legacy mipmap-xhdpi 96 0.72
gen_legacy mipmap-xxhdpi 144 0.72
gen_legacy mipmap-xxxhdpi 192 0.72

gen_adaptive mipmap-mdpi 108 0.60
gen_adaptive mipmap-hdpi 162 0.60
gen_adaptive mipmap-xhdpi 216 0.60
gen_adaptive mipmap-xxhdpi 324 0.60
gen_adaptive mipmap-xxxhdpi 432 0.60

echo "icons written to $RES/mipmap-*"
