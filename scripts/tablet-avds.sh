#!/usr/bin/env bash
# Create, start, stop and delete the tablet test AVDs (docs/tablet/4-design.md rule 9).
# Works under Git Bash on Windows and on Linux/macOS. Needs ANDROID_HOME (or ANDROID_SDK_ROOT).
#
#   scripts/tablet-avds.sh create  [name|all]
#   scripts/tablet-avds.sh start   <name>      (cold boot, waits until booted)
#   scripts/tablet-avds.sh stop    <name>
#   scripts/tablet-avds.sh delete  [name|all]
#   scripts/tablet-avds.sh portrait|landscape <name>
#   scripts/tablet-avds.sh shot    <name> <file.png>
#   scripts/tablet-avds.sh list
#
# The 13" Chromebook (1600x900 dp) needs no AVD of its own: use the `resizable` device profile
# or the emulator's resizable window and resize it.
set -euo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] || { echo "Set ANDROID_HOME to the Android SDK" >&2; exit 1; }
IMAGE="system-images;android-34;google_apis;x86_64"
NAMES="ce_phone_tmp ce_tablet_8 ce_tablet_10 ce_fold"

exe() { # exe <path without extension> -> the path with .exe/.bat if that is what exists
  local base="$1" ext
  for ext in "" .exe .bat; do
    if [ -f "$base$ext" ]; then echo "$base$ext"; return; fi
  done
  echo "Not found: $base" >&2; exit 1
}
AVDMANAGER="$(exe "$SDK/cmdline-tools/latest/bin/avdmanager")"
EMULATOR="$(exe "$SDK/emulator/emulator")"
ADB="$(command -v adb || exe "$SDK/platform-tools/adb")"

port() {
  case "$1" in
    ce_phone_tmp) echo 5580 ;;
    ce_tablet_8) echo 5582 ;;
    ce_tablet_10) echo 5584 ;;
    ce_fold) echo 5586 ;;
    *) echo "Unknown AVD: $1 (one of: $NAMES)" >&2; exit 1 ;;
  esac
}

serial() { echo "emulator-$(port "$1")"; }

has_device() { "$AVDMANAGER" list device -c | tr -d '\r' | grep -qxF "$1"; }

device_for() {
  case "$1" in
    ce_phone_tmp) echo pixel_6 ;;      # 412x915 dp
    ce_tablet_8) echo small_tablet ;;  # resized to 1024x640 dp in config.ini
    ce_tablet_10) echo pixel_tablet ;; # 1280x800 dp, natural orientation landscape
    ce_fold)
      local d
      for d in pixel_fold pixel_9_pro_fold pixel_10_pro_fold; do
        if has_device "$d"; then echo "$d"; return; fi
      done
      echo "No foldable profile in 'avdmanager list device -c'" >&2; exit 1 ;;
  esac
}

avd_dir() { echo "${ANDROID_AVD_HOME:-$HOME/.android/avd}/$1.avd"; }

set_config() { # set_config <name> <key> <value>
  local f; f="$(avd_dir "$1")/config.ini"
  if grep -q "^$2 *=" "$f"; then sed -i.bak "s|^$2 *=.*|$2 = $3|" "$f"; else echo "$2 = $3" >> "$f"; fi
  rm -f "$f.bak"
}

create_one() {
  local n="$1" d
  d="$(device_for "$n")"
  echo "Creating $n ($d)"
  echo no | "$AVDMANAGER" create avd --force -n "$n" -k "$IMAGE" -d "$d" >/dev/null
  set_config "$n" hw.keyboard yes
  if [ "$n" = ce_tablet_8 ]; then # 1024x640 px at 160 dpi is 1024x640 dp
    set_config "$n" hw.lcd.width 1024
    set_config "$n" hw.lcd.height 640
    set_config "$n" hw.lcd.density 160
  fi
}

delete_one() {
  if "$AVDMANAGER" delete avd -n "$1" >/dev/null 2>&1; then echo "Deleted $1"; else echo "$1 not present"; fi
}

each() { # each <function> <name|all>
  local n
  if [ "$2" = all ]; then
    for n in $NAMES; do "$1" "$n"; done
  else
    port "$2" >/dev/null
    "$1" "$2"
  fi
}

rotate() { # rotate <name> <portrait|landscape>
  # user_rotation 0 is the natural orientation: landscape on the tablets and the unfolded fold
  # (its hinge is then vertical, so tabletop needs portrait), portrait on the phone.
  local rot=0 s
  s="$(serial "$1")"
  case "$1" in
    ce_tablet_8|ce_tablet_10|ce_fold) if [ "$2" = portrait ]; then rot=1; fi ;;
    *) if [ "$2" = landscape ]; then rot=1; fi ;;
  esac
  "$ADB" -s "$s" shell settings put system accelerometer_rotation 0
  "$ADB" -s "$s" shell settings put system user_rotation "$rot"
}

cmd="${1:-}"
shift || true
case "$cmd" in
  create) each create_one "${1:-all}" ;;
  delete) each delete_one "${1:-all}" ;;
  start)
    n="${1:?name}"
    p="$(port "$n")"
    "$EMULATOR" -avd "$n" -port "$p" -no-snapshot -no-boot-anim >/dev/null 2>&1 &
    "$ADB" -s "emulator-$p" wait-for-device
    until [ "$("$ADB" -s "emulator-$p" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
      sleep 3
    done
    echo "$n is up as emulator-$p" ;;
  stop) "$ADB" -s "$(serial "${1:?name}")" emu kill ;;
  portrait | landscape) rotate "${1:?name}" "$cmd" ;;
  shot)
    n="${1:?name}"
    out="${2:?file}"
    export MSYS_NO_PATHCONV=1
    "$ADB" -s "$(serial "$n")" shell screencap -p /data/local/tmp/cap.png
    "$ADB" -s "$(serial "$n")" pull /data/local/tmp/cap.png "$out" >/dev/null
    echo "Saved $out" ;;
  list) "$EMULATOR" -list-avds | tr -d '\r' | grep -xE 'ce_(phone_tmp|tablet_8|tablet_10|fold)' || true ;;
  *) sed -n '2,13p' "$0"; exit 1 ;;
esac
