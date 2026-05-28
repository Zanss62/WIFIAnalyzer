# WiFi Analyzer - Panduan Build APK

## Persyaratan
- Android Studio Hedgehog (2023.1.1) atau lebih baru
- JDK 17+
- Android SDK 34 (Android 14)
- Gradle 8.4

---

## Cara Build APK

### Metode 1: Android Studio (Direkomendasikan)

1. **Buka Project**
   - Buka Android Studio
   - File → Open → Pilih folder `WiFiAnalyzer`
   - Tunggu Gradle sync selesai (1-3 menit pertama kali)

2. **Build APK Debug**
   - Menu: Build → Build Bundle(s)/APK(s) → Build APK(s)
   - Atau tekan `Ctrl+F9` (Windows/Linux) / `Cmd+F9` (Mac)
   - APK tersimpan di: `app/build/outputs/apk/debug/app-debug.apk`

3. **Build APK Release**
   - Menu: Build → Generate Signed Bundle/APK
   - Pilih APK → Next
   - Buat/pilih keystore → Next
   - Pilih release → Finish

---

### Metode 2: Command Line (Gradle)

```bash
# Masuk ke folder project
cd WiFiAnalyzer

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# APK tersimpan di:
# debug:   app/build/outputs/apk/debug/app-debug.apk
# release: app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## Izin yang Diperlukan

Aplikasi memerlukan izin berikut saat dijalankan pertama kali:

| Izin | Alasan |
|------|--------|
| `ACCESS_FINE_LOCATION` | **WAJIB** di Android 10+ untuk membaca SSID WiFi |
| `ACCESS_WIFI_STATE` | Membaca daftar jaringan WiFi |
| `CHANGE_WIFI_STATE` | Memulai proses scan WiFi |
| `ACCESS_NETWORK_STATE` | Cek jaringan yang sedang aktif |

> ⚠️ **Catatan**: Android mengharuskan izin Lokasi untuk scan WiFi bukan untuk melacak lokasi GPS, tapi karena SSID bisa digunakan untuk menentukan lokasi secara kasar.

---

## Struktur Project

```
WiFiAnalyzer/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml          # Permission & Activity declarations
│       ├── java/com/wifianalyzer/
│       │   ├── MainActivity.kt          # Layar utama + permission handling
│       │   ├── adapter/
│       │   │   └── WiFiAdapter.kt       # RecyclerView adapter
│       │   ├── model/
│       │   │   └── WiFiNetwork.kt       # Data model WiFi
│       │   └── ui/
│       │       ├── WiFiViewModel.kt     # Logika scan + LiveData
│       │       └── DetailActivity.kt   # Halaman detail jaringan
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml    # Layout utama
│           │   ├── activity_detail.xml  # Layout detail
│           │   └── item_wifi_network.xml # Item RecyclerView
│           ├── drawable/               # Ikon & background
│           ├── values/
│           │   ├── colors.xml          # Palet warna dark mode
│           │   ├── strings.xml         # String resources
│           │   └── themes.xml          # Material Design 3 theme
│           └── anim/                   # File animasi XML
```

---

## Fitur Aplikasi

### Tampilan Utama
- 📶 Daftar WiFi diurutkan dari sinyal terkuat
- 🎨 Warna ikon berbeda sesuai kekuatan sinyal:
  - 🟢 Hijau: ≥75% (Sangat Kuat)
  - 🟡 Kuning-hijau: ≥50% (Kuat)
  - 🟡 Kuning: ≥25% (Sedang)
  - 🔴 Merah: <25% (Lemah)
- 🔒 Ikon kunci berbeda untuk jaringan aman vs open
- ✅ Badge "Connected" untuk jaringan aktif

### Kontrol
- 🔄 **Auto Refresh**: Setiap 3 detik otomatis
- 👆 **Manual Refresh**: Tombol FAB atau swipe ke bawah
- 🔍 **Search**: Filter berdasarkan nama WiFi
- 📊 **Filter Sinyal Kuat**: Tampilkan hanya ≥50%

### Detail Jaringan (klik item)
- SSID & BSSID (MAC address AP)
- Kekuatan sinyal (%, dBm, kualitas)
- Frekuensi, channel, band (2.4/5 GHz)
- Tipe keamanan + penjelasan
- Raw capabilities string

---

## Catatan Keamanan

✅ **Aman digunakan**: Aplikasi ini HANYA membaca informasi publik yang tersedia dari Android API resmi (`WifiManager.getScanResults()`).

❌ **Tidak dilakukan**:
- Membaca password WiFi siapapun
- Mengakses traffic/data jaringan orang lain
- Melakukan koneksi ke jaringan tanpa izin user
- Menyimpan data ke internet/server

---

## Kompatibilitas

| Android Version | API Level | Status |
|----------------|-----------|--------|
| Android 10     | API 29    | ✅ Didukung (minSdk) |
| Android 11     | API 30    | ✅ Didukung |
| Android 12     | API 31    | ✅ Didukung |
| Android 13     | API 33    | ✅ Didukung |
| Android 14     | API 34    | ✅ Didukung (targetSdk) |

---

## Troubleshooting

**Q: Scan tidak menemukan WiFi apapun**
- Pastikan WiFi perangkat aktif
- Berikan izin Lokasi (Fine Location)
- Di Android 12+, pastikan lokasi sistem aktif (bukan hanya izin app)

**Q: SSID tampil sebagai "<Hidden Network>"**
- Jaringan tersebut memang disembunyikan (hidden SSID)
- Ini behavior normal Android

**Q: Build gagal dengan error "minSdk"**
- Pastikan targetSdk = 34 dan minSdk = 29 di build.gradle.kts

**Q: Gradle sync gagal**
- Cek koneksi internet (untuk download dependencies)
- File → Invalidate Caches and Restart
