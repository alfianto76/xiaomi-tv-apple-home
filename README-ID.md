# Xiaomi TV ala Apple TV: Home v2 + Adzan

Fork dari [Clean Your TV](https://github.com/Gathin23/cleanyourtv) untuk **Xiaomi TV A Pro 2026 (Google TV 14)**,
dengan tampilan home baru yang dibuat untuk pemakai di Indonesia: sapaan berbahasa Indonesia, jadwal solat,
adzan otomatis, cuaca, dan jadwal pertandingan klub favorit. Semua tanpa root, lewat ADB di Wi-Fi, dan bisa
dikembalikan seperti semula.

> Panduan asli (bahasa Inggris), daftar TV lain, dan semua alatnya ada di [README.md](README.md).

## Isinya

| Bagian | Keterangan |
|---|---|
| **Home v2** | Sapaan besar di tengah ("Selamat Malam, Nama"), tanggal dan jam di kanan atas, panel kaca berisi cuaca + 5 waktu solat (solat berikutnya hijau) + pertandingan berikutnya klub pilihan dengan logo, dock 6 app, dan baris tombol bawah: Semua App, Input, Asisten, Jadwal Solat, Pengaturan |
| **Add-on Adzan** (`addons/adzan`) | Kartu kecil di pojok + suara adzan di setiap waktu solat, di atas app apa pun (Netflix dan sejenisnya otomatis pause). OK/Back untuk berhenti. Diam saat TV standby. Jadwal Kemenag RI dari Aladhan. Juga jadi sumber data cuaca dan bola untuk Home v2 |
| **Suara klik** | Bunyi "tik" lembut setiap pindah ikon (suara orisinal, `launcher/.../res/raw/tick.wav`) |
| **Tema `apple`** | Latar hitam pekat, dock 6 app, gerakan ringan (`reduced_motion`) |
| **Tombol remote** | Tombol YouTube membuka app pilihan; tahan untuk app kedua (`app_keys`) |
| **Daftar debloat** | `examples/xiaomi-tv-a-pro-2026-home-v2/debloat.txt` |

Yang **tidak** disertakan: file suara adzan, foto wallpaper, dan APK pihak ketiga. Pakai file milik kamu
sendiri (caranya ada di bawah).

## Perangkat yang dites

Xiaomi TV A Pro 75" 2026, `MiTV-MZTU0` (river, MediaTek), Google TV 14, firmware
`V816.0.25.1.14.2.URTMIXM`, RAM 1,8 GB. Xiaomi Google TV 2024+ lain kemungkinan mirip, tapi cek dulu nama
paketnya dengan `adb shell pm list packages`.

## Yang dibutuhkan

Mac (atau Linux) dengan:

```bash
brew install openjdk@17 android-commandlinetools android-platform-tools
yes | sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0"
git clone <url repo ini> && cd <folder repo>
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=~/Library/Android/sdk
export TV_HOST=192.168.x.x        # IP TV kamu (Settings > Network)
```

Di TV: **Settings → Device Preferences → About → tekan Build 7x**, lalu di Developer options nyalakan
**USB debugging**. Sambungkan: `adb connect $TV_HOST:5555` dan pilih **Allow** di TV.

## 1. Debloat

**Penting (Google TV 14):** kalau Wi-Fi TV tersimpan waktu setup awal, Wi-Fi itu "milik" setup wizard dan
akan hilang saat wizard dimatikan. Cek dulu:

```bash
adb shell dumpsys wifi | grep -o 'cname=[^ ]*' | sort -u
```

Kalau muncul `setupwraith`: di TV buka **Settings → Network & Internet**, *Forget* Wi-Fi, lalu sambung lagi.
Ulangi sampai hanya `android.uid.system:1000` yang muncul. Setelah itu:

```bash
tools/disable.sh examples/xiaomi-tv-a-pro-2026-home-v2/debloat.txt
```

Buka file daftarnya dulu dan beri tanda `#` di depan app yang masih mau kamu pakai.

## 2. Add-on Adzan

Atur kota, metode, dan klub di `addons/adzan/app/src/main/res/values/config.xml`
(`latitude`, `longitude`, `place`, `method` 20 = Kemenag RI, `team_ids` dari alamat halaman tim di
[thesportsdb.com](https://www.thesportsdb.com), misalnya 139356 = Persib Bandung).

```bash
cd addons/adzan
cp -r ../prayer-times/gradle ../prayer-times/gradlew ../prayer-times/build.gradle ../prayer-times/gradle.properties .
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleRelease && adb install -r app/build/outputs/apk/release/app-release.apk
adb shell appops set id.alfian.tv.adzan SYSTEM_ALERT_WINDOW allow
adb shell pm grant id.alfian.tv.adzan android.permission.READ_MEDIA_AUDIO
adb shell am start -n id.alfian.tv.adzan/.MainActivity
cd ../..
```

**Suara adzan:** siapkan MP3 adzan milikmu sendiri, lalu

```bash
adb shell mkdir -p /sdcard/Adzan
adb push adzan.mp3 /sdcard/Adzan/adzan.mp3     # opsional: subuh.mp3 khusus Subuh
```

Tanpa file, yang bunyi adalah lonceng lembut bawaan. Tombol **Tes Adzan** ada di app Adzan.

## 3. Launcher Home v2 + tema

Ganti nama sapaan di `themes/apple/config.json` (`greeting_name`), lalu:

```bash
INPUT_KEY_SERVICE=1 STOCK_LAUNCHER=com.google.android.apps.tv.launcherx launcher/build-and-install.sh
tools/theme.sh themes/apple
adb shell cmd locale set-app-locales com.gathin.tvhome --locales in-ID    # sapaan & tanggal bahasa Indonesia
```

- **Wallpaper:** taruh foto kamu sebagai `themes/apple/wallpaper.jpg` (1920×1080, di bawah 300 KB) dan set
  `"wallpaper": true`. Catatan: di TV ini wallpaper foto membuat perpindahan ikon sedikit patah-patah
  (diukur dengan `tools/bench.py`: jank 0,2% tanpa wallpaper, sekitar 30% dengan foto). Latar hitam paling mulus.
- **Susun dock:** tahan OK pada ikon → *Move* (butuh `"tile_menu": true`), atau ubah daftar `dock_apps`.
- **Tombol remote:** cari kodenya dengan `adb shell getevent -lt` sambil menekan tombol, lalu isi `app_keys`,
  misalnya `"scan:425:<nama.paket>"` (tekan) dan `"hold:scan:425:<nama.paket>"` (tahan). Di remote Xiaomi ini
  tombol YouTube = 425; tombol Favorit tidak pernah sampai ke Android (dibuang firmware).
- **Tagline** di bawah sapaan: ubah teks `"Santai  ·  Nonton  ·  Jelajahi"` di
  `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java`, lalu build ulang.
- Home v2 bisa dimatikan dengan `HOME_V2 = false` di file yang sama.

## Mengembalikan ke semula

```bash
tools/restore.sh examples/xiaomi-tv-a-pro-2026-home-v2/debloat.txt com.google.android.apps.tv.launcherx
adb uninstall id.alfian.tv.adzan
```

Factory reset juga mengembalikan semuanya.

## Keamanan

Matikan **USB debugging** di TV kalau sudah selesai. ADB lewat Wi-Fi tidak terenkripsi, dan perangkat lain
di jaringan yang sama bisa mencoba tersambung.

## Kredit dan lisensi

- Launcher, alat, dan tema dasar: [Clean Your TV](https://github.com/Gathin23/cleanyourtv) oleh Gathin T., MIT
  (lihat [LICENSE](LICENSE)). Perubahan di fork ini memakai lisensi yang sama.
- Add-on Prayer Times oleh [@majnioui](https://github.com/majnioui) dan Weather oleh
  [@Sumyabazar1](https://github.com/Sumyabazar1), bagian dari repo asli.
- Data: jadwal solat [Aladhan](https://aladhan.com), cuaca [Open-Meteo](https://open-meteo.com), jadwal dan logo
  klub [TheSportsDB](https://www.thesportsdb.com) (kunci API gratis).
- Proyek ini tidak berafiliasi dengan Xiaomi, Google, atau Apple.
