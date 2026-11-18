#!/bin/sh
# Regenerates the application icon of the packaged editor:
#   AnimatorFX.png          1024 px master, drawn by IconGenerator.java (Java2D)
#   macos/AnimatorFX.icns   built from the master with sips and iconutil (macOS only)
#   ../main/resources/com/dlsc/animatorfx/editor/app-icon.png
#                           256 px copy shown in the About dialog (macOS only: sips)
# Usage: sh animatorfx-editor/src/package/make-icons.sh     (needs a JDK 11+ on the PATH or in JAVA_HOME)
set -e
cd "$(dirname "$0")"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

"$JAVA" -Djava.awt.headless=true IconGenerator.java AnimatorFX.png

if command -v iconutil >/dev/null 2>&1 && command -v sips >/dev/null 2>&1; then
    SET=AnimatorFX.iconset
    rm -rf "$SET"
    mkdir "$SET"
    for size in 16 32 128 256 512; do
        sips -z $size $size AnimatorFX.png --out "$SET/icon_${size}x${size}.png" >/dev/null
        double=$((size * 2))
        sips -z $double $double AnimatorFX.png --out "$SET/icon_${size}x${size}@2x.png" >/dev/null
    done
    sips -Z 256 AnimatorFX.png --out ../main/resources/com/dlsc/animatorfx/editor/app-icon.png >/dev/null
    mkdir -p macos
    iconutil -c icns "$SET" -o macos/AnimatorFX.icns
    rm -rf "$SET"
    echo "Wrote macos/AnimatorFX.icns"
else
    echo "sips/iconutil not found: macos/AnimatorFX.icns was not rebuilt"
fi
