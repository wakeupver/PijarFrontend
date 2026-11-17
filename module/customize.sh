# Di-source oleh installer Magisk / KernelSU / APatch saat modul dipasang.
ui_print "- Pijar Frontend"
ui_print "- APK dipasang sebagai system app: /system/app/Pijar"
ui_print "- Aplikasi Home diatur sekali pada boot pertama setelah pemasangan"
ui_print "- Gestur sistem tetap memakai Trebuchet/Quickstep bawaan ROM"

if [ ! -f "$MODPATH/system/app/Pijar/Pijar.apk" ]; then
  abort "! APK tidak ditemukan di dalam modul"
fi

# /system/app (bukan priv-app): aplikasi ini tidak memakai izin privileged, jadi tidak perlu
# daftar privapp-permissions dan tidak ada risiko bootloop karena izin yang tak diizinkan.
set_perm_recursive "$MODPATH/system/app/Pijar" 0 0 0755 0644 u:object_r:system_file:s0
