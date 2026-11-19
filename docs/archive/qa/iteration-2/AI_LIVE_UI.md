# Kamera dan navigasi Android dengan AI nyata

7 November 2026, API 37, 390×844 dp, font 100%. `RemoteAiNavigationTest.cameraAdviceSaveEditDeleteAndLogoutUseRealBackend` lulus pada aplikasi native dengan adapter remote, hot reload mati dan HTTPS Go publik. Ini melengkapi [pengujian model/repository](AI_LIVE_ANDROID.md) dengan interaksi melalui UI aplikasi.

## Jalur yang terbukti

1. Daftar dengan akun uji baru dan baca capability/katalog server.
2. Aktifkan CameraX, ambil foto, buka tinjau foto dan ganti tema melalui Activity recreation tanpa kehilangan foto.
3. Upload foto asli melalui Go ke YOLO: hasil aktual `bottle`, confidence `0.7272477746009827`, band OFFERED. Category dan object key tetap null; pengguna memilih PET secara eksplisit.
4. Kondisi awal menghasilkan NEEDS_CONTEXT. Pengguna melengkapi bekas pangan/minuman lalu meminta kembali; Gemini menghasilkan READY/RECYCLE. Scan dan saran belum menambah catatan atau XP.
5. Pilih gunakan saran: editor terbuka dengan jumlah kosong. Isi 250 g lalu simpan, memperoleh satu catatan dengan provenance AI dan 10 XP.
6. Buka detail, edit catatan yang sama tanpa menambah XP, hapus lalu periksa progres/ringkasan server kembali nol.
7. Keluar dan periksa credential lokal sudah dihapus.

[Log native](ai-live/api37-ui/native.log) mencatat satu metode lulus. [Tujuh checkpoint UI](ai-live/api37-ui/native-fixture.json), [binding APK/sumber](ai-live/api37-ui/build.json), [patch helper capture](ai-live/api37-ui/source.patch) dan [hasil verifikasi database](ai-live/api37-ui/result.json) disimpan bersama. Source aplikasi pada checkpoint ini adalah `4984b55`; hanya helper pengujian screenshot memiliki perubahan lokal, yang ikut dibekukan. Ini mendahului perbaikan label mentah dan streak pada checkpoint berikutnya.

Snapshot Go memverifikasi penerimaan pengguna, PLASTIC/RECYCLE, provider `gemini`, `prototype_only=false` dan model SHA256 `0ebbc80d4a7680d14987a577cd21342b65ecfd94632bd9a8da63ae6417644ee1`. Sesudah verifikasi, akun uji yang dipastikan belum ada sebelum pengujian beserta sessions, refresh tokens, waste logs, AI operations dan metadata-nya dibersihkan; keenam hitungan akhir nol. Konfigurasi display/tema emulator dipulihkan.

## Bukti visual

Ada [15 frame display beserta hash](ai-live/api37-ui/captures.json). Heading dan paket window diperiksa; helper menggulir ke elemen yang terlihat, bukan mengirim delta gulir negatif besar yang dapat memindahkan bottom sheet keluar layar. Frame sebelumnya yang menunjukkan Home saat panel seharusnya terbuka tidak dipakai sebagai bukti.

| Keadaan | Terang | Gelap |
|---|---|---|
| S21 tinjau foto | [PNG](ai-live/api37-ui/S21-photo-light.png) | [PNG](ai-live/api37-ui/S21-photo-dark.png) |
| S22 objek, belum konfirmasi material | [PNG](ai-live/api37-ui/S22-object-light.png) | [PNG](ai-live/api37-ui/S22-object-dark.png) |
| S23 kondisi | [PNG](ai-live/api37-ui/S23-context-light.png) | [PNG](ai-live/api37-ui/S23-context-dark.png) |
| S24 hasil Gemini | [PNG](ai-live/api37-ui/S24-recommendation-light.png) | [PNG](ai-live/api37-ui/S24-recommendation-dark.png) |
| S24 perlu konteks | [PNG](ai-live/api37-ui/S24-needs-context-light.png) | Tidak direkam pada checkpoint ini |
| Editor / simpan / detail / setelah hapus | [Editor](ai-live/api37-ui/S05-ai-prefill-light.png), [tersimpan](ai-live/api37-ui/S06-created-light.png), [detail](ai-live/api37-ui/S08-created-light.png), [profil](ai-live/api37-ui/S14-after-delete-light.png) | Tidak direkam pada checkpoint ini |

## Batas bukti

Kamera emulator memakai imagefile dari [foto PET CC0](../../../../app/src/androidTest/assets/ai/README.md), diperkecil utuh dan diberi margin pada kanvas persegi agar objek tidak terpotong oleh crop kamera. [Manifest input](ai-live/api37-ui/camera-input.json) mengikat ukuran, offset dan checksum. Ini membuktikan jalur CameraX/upload/Go/YOLO/Gemini/core dengan input terkontrol, bukan kamera perangkat fisik atau akurasi model pada populasi foto. Kondisi benda dan akses penerima adalah jawaban fixture, bukan fakta yang disimpulkan dari foto.

Frame kamera sebelum capture tidak menunggu frame video pertama sehingga tidak dipakai untuk menerima kualitas preview video aktif. CONDITIONAL/UNAVAILABLE, gangguan jaringan nyata, seluruh state/kontrol, font besar, TalkBack dan seluruh perangkat belum diterima oleh perjalanan positif tunggal ini. Bukti state terkontrol dan review akhir tetap dipisahkan dari alur produksi yang berhasil.
