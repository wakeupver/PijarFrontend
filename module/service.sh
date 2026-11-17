#!/system/bin/sh
# Menjadikan Pijar aplikasi Home SEKALI saja per pemasangan modul,
# supaya pilihan Home yang diubah pengguna kemudian tidak ditimpa tiap boot.
MODDIR=${0%/*}
PKG=com.wakeupver.pijar
MARK="$MODDIR/.home_applied"

[ -f "$MARK" ] && exit 0

until [ "$(getprop sys.boot_completed)" = "1" ]; do
  sleep 5
done
sleep 10

i=0
while [ "$i" -lt 6 ]; do
  if pm path "$PKG" > /dev/null 2>&1; then
    if cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" 0 > /dev/null 2>&1; then
      touch "$MARK"
      exit 0
    fi
  fi
  i=$((i + 1))
  sleep 10
done
exit 0
