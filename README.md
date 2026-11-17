# Pijar — frontend emulator Android bergaya Material 3 Expressive

Frontend emulator sekaligus aplikasi Home (launcher): beranda dengan carousel platform, pustaka game dengan
sampul, laci aplikasi, dan peluncuran game ke emulator lewat **data terbuka Daijishō** (122 platform,
1.023 definisi pemutar). UI ditulis dengan Jetpack Compose + Material 3 Expressive (tema `MaterialExpressiveTheme`,
gerak pegas `MotionScheme.expressive()`, bentuk cookie/clover untuk lencana platform, tombol yang berubah bentuk saat ditekan).

> **Status v0.1 — baca dulu.** Lingkungan tempat proyek ini dibuat tidak punya akses ke Google Maven, jadi
> **kompilasi Gradle/Compose penuh belum pernah dijalankan**. Yang sudah diverifikasi dan yang belum ada di bagian
> [Verifikasi](#verifikasi). Build pertama di GitHub Actions mungkin butuh perbaikan kecil; kirim lognya dan saya perbaiki.

## Dua hal yang perlu jelas

1. **Ini bukan salinan UI/fungsi Daijishō.** Aplikasi Daijishō closed-source (README repo resminya menyatakan
   "currently closed-source"); repo `TapiocaFox/Daijishou` hanya berisi aset, dokumen, dan data platform (lisensi MIT).
   Pijar memakai **data**-nya apa adanya, sedangkan UI dan logika aplikasinya ditulis sendiri.
2. **Ini bukan fork Trebuchet.** Trebuchet `lineage-20.0` dibangun lewat Soong (`Android.bp`, `platform_apis`, `privileged`);
   berkas Gradle di dalamnya peninggalan lama dan merujuk folder yang tidak ada, jadi Quickstep (mesin gestur) tidak bisa
   dibangun mandiri di GitHub Actions. Gestur sistem tetap ditangani Trebuchet bawaan ROM — lihat [Gestur sistem](#gestur-sistem).

## Build di GitHub Actions

1. Buat repositori GitHub baru, lalu unggah **seluruh isi folder ini** (termasuk folder tersembunyi `.github/` dan
   `gradle/wrapper/gradle-wrapper.jar`).
2. Buka tab **Actions**. Workflow *Build* jalan otomatis saat push (atau klik *Run workflow*).
3. Setelah hijau, unduh artifact **Pijar-build**: `Pijar-v0.1.0-release.apk`, `Pijar-v0.1.0-debug.apk`,
   dan `Pijar-module-v0.1.0.zip` (modul KernelSU/Magisk, opsional).
4. Beri tag `v0.1.0` untuk membuat GitHub Release otomatis dengan berkas yang sama.
5. Bila gagal: salin log langkah yang merah atau unduh artifact **build-reports**.

APK rilis ditandatangani kunci debug kecuali Anda mengisi rahasia repositori `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`.

Build lokal: JDK 17 + Android SDK 36, lalu `./gradlew :core:test` dan `./gradlew :app:assembleDebug`.
Versi: Gradle 8.13, AGP 8.11.1, Kotlin 2.2.0, Compose UI 1.9.0, Material 3 1.4.0, minSdk 26, targetSdk 35.

## Fitur v0.1

- **Beranda**: kartu "Lanjutkan bermain", carousel platform (`HorizontalMultiBrowseCarousel`), rak terakhir dimainkan dan favorit.
- **Pustaka**: filter platform/favorit, pencarian, urutan (A–Z / terbaru / terakhir dimainkan), sampul lokal otomatis
  (`<nama-rom>.png|jpg|webp` di samping ROM atau di `media/`, `images/`, `boxart/`, `covers/`).
- **Detail game** (bottom sheet): favorit, tombol Main, statistik, dan pilihan pemutar yang cocok dengan berkas.
- **Platform**: tambah dari 122 platform bawaan, pilih folder ROM (pemilih folder atau ketik path), pindai otomatis,
  pemutar bawaan per platform, impor JSON platform sendiri, tebakan folder otomatis (`ROMs/psx`, `Roms/Sony PlayStation`, ...).
- **Aplikasi**: laci semua aplikasi dengan pencarian; tekan lama untuk info aplikasi.
- **Pengaturan**: warna dinamis atau 8 warna aksen, wallpaper di balik UI, jadikan aplikasi Home, akses semua berkas.
- Bahasa: Inggris dan Indonesia (`values/`, `values-in/`).

## Kompatibilitas data Daijishō

| Didukung | Belum |
| --- | --- |
| Berkas platform JSON dan regex nama berkas | Scraper/DSESS, metadata dan sampul online |
| Argumen pemutar gaya `am start`: `-a -d -t -c -n -p -f`, semua `--e*` (string/boolean/int/long/float/uri/array), `--activity-*`, `--grant-*` | RetroAchievements |
| Template `{file.path}`, `{file.uri}`, `{file.mime}`, `{tags.*}` (+ `{file.name}`, `{file.basename}`, `{file.ext}`, `{file.dir}`) | Widget, koleksi, tema/wallpaper Daijishō |
| Berkas `.dpt` (Player Template) untuk `{tags.*}` | Cadangan/pemulihan pustaka |
| `killPackageProcesses`, banyak pemutar per platform, pemutar bawaan per platform | Pemantauan folder (hanya pindai manual/otomatis saat tambah) |

`{file.uri}` menghasilkan URI `content://` dari `FileProvider` milik Pijar dengan izin baca/tulis untuk emulator tujuan.

## Gestur sistem

Navigasi gestur Android 10+ (swipe ke Home, tombol kembali, recents) dikendalikan **penyedia recents** sistem —
di LineageOS 20 itu Trebuchet Quickstep (`config_recentsComponentName`, aplikasi sistem berizin istimewa). Pijar sengaja
**tidak** menggantikannya:

- Selama Trebuchet tetap terpasang, Pijar berperan sebagai aplikasi Home biasa dan gestur sistem terus berfungsi
  (secara desain Quickstep memakai `RecentsActivity` cadangan bila Home bawaan bukan Trebuchet; **belum diuji di perangkat**).
- Sisi Pijar: tata letak edge-to-edge, kartu yang bisa digeser tidak ditaruh di zona gestur tepi (`WindowInsets.systemGestures`),
  predictive back (`enableOnBackInvokedCallback` + `BackHandler`), dan `launchMode=singleTask` + `onNewIntent`
  sehingga gestur/tombol Home saat sudah terbuka kembali ke Beranda.
- Yang **tidak** bisa dari APK biasa: animasi swipe-to-home khas Quickstep. Itu mensyaratkan Pijar menjadi penyedia recents,
  yang hanya mungkin bila dibangun di dalam tree ROM (Soong) dengan izin istimewa. Bila ROM Anda menghapus Trebuchet,
  perlu penyedia recents lain.

## Modul KernelSU / Magisk / APatch (opsional)

`Pijar-module-*.zip` memasang APK sebagai **system app** (`/system/app/Pijar`, bukan `priv-app`, karena Pijar tidak memakai izin
istimewa sehingga tak perlu allowlist dan tak ada risiko bootloop karena izin) dan menjadikannya aplikasi Home **sekali** pada
boot pertama (`cmd role add-role-holder`). Pilihan Home yang Anda ubah sesudahnya tidak ditimpa lagi. Modul ini tidak diperlukan
untuk gestur; fungsinya hanya membuat Pijar tak bisa dicopot dan otomatis jadi Home.

## Struktur proyek

```
core/   Kotlin/JVM murni: JSON mini, parser data Daijishō, parser argumen `am start`, template, .dpt,
        pemindai ROM, status aplikasi. Diuji di JVM (28 uji, termasuk seluruh data platform asli).
app/    Aplikasi Android (Compose): data/ (Repository, Storage, Apps), launch/ (GameLauncher), ui/ (layar, sheet, tema).
        assets/daijishou/  salinan data platform Daijishō (MIT) + LICENSE.MD
module/ Templat modul KernelSU/Magisk (APK disisipkan oleh CI)
.github/workflows/build.yml
```

Mengganti nama/ID paket: ubah `namespace` dan `applicationId` di `app/build.gradle.kts`, nama paket folder `app/src/main/kotlin`,
`PKG` di `module/service.sh`, dan `app_name` di `strings.xml`.

## Verifikasi

Sudah (di lingkungan pembuatan):

- `core`: 28 uji unit lulus. Termasuk parsing 122 platform / 1.023 pemutar dari data asli, setiap argumen diterjemahkan ke Intent
  tanpa peringatan, indeks konsisten dengan berkas.
- Lapisan Android non-Compose (`GameLauncher`, `Storage`, `Apps`, `SystemUi`, `Repository`, `MainViewModel`) dikompilasi terhadap
  `android.jar` API 35 asli tanpa galat tipe.
- XML (manifest, resource, ikon), YAML workflow, skrip shell, katalog versi Gradle, dan semua rujukan `R.string.*`/resource diperiksa otomatis.
- Pemeriksa impor lintas-berkas pada seluruh kode Kotlin: tidak ada impor yang kurang.

Belum:

- Kompilasi Gradle/AGP/Compose dan menjalankan di perangkat. **Risiko terbesar**: API Material 3 Expressive masih eksperimental
  (`MaterialExpressiveTheme`, `MotionScheme.expressive()`, `MaterialShapes` + `toShape()`, `LoadingIndicator`); bila nama atau
  tanda tangannya berbeda di versi yang terpasang, kompilasi gagal di berkas `ui/` dan perbaikannya kecil.
- Perilaku emulator tertentu terhadap URI `content://`, serta gestur di ROM Anda.

## Langkah berikutnya yang masuk akal

Perbaiki hasil build pertama → uji peluncuran di beberapa emulator → pemantauan folder, metadata/sampul online, widget →
bila butuh gestur Quickstep sungguhan, patch Trebuchet di dalam tree ROM.
