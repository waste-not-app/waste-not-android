# Pemulihan hasil write yang belum diketahui

Checkpoint `codex/android-write-outcome-recovery`, berbasis `eae6345`, 5 November 2026. Cakupan: snapshot input, guard UUID create, error lanjutan, penutupan/pemulihan draf dan pesan editor S05/S09/D03.

## Perubahan

Sebelumnya NETWORK/TIMEOUT mengunci input, tetapi retry yang mendapat 429/422/STORAGE melepas snapshot berdasarkan error terakhir. Guard create pada ViewModel juga dapat melupakan payload awal. Tiga [regresi awal](write-recovery/recovery-red.xml) gagal: formulir terbuka untuk diedit, pause ditolak, dan payload berubah mencapai store.

`EditorDraft.outcomeUnknown` kini berasal dari snapshot yang masih terkunci serta status penyelesaiannya. Error lanjutan tetap ditampilkan, sementara input awal dipertahankan. Format snapshot 14/21/22 field tetap dapat dibaca; tidak menambah field atau mengganti codec. Pause dan dialog tutup memakai status hasil write, sehingga draf dengan error terakhir 429 tetap bisa disimpan untuk ditinjau.

ViewModel mempertahankan payload/ketidakpastian create melintasi retry dan cancellation. Draf hasil restart mengisi guard sebelum percobaan pertama. Payload yang berubah ditolak sebelum mencapai store. Receipt sukses menyelesaikan guard; cancellation tetap diteruskan. Konflik versi edit tetap membuka pembacaan/rekonsiliasi versi terbaru; idempotency conflict/deleted tetap tidak dapat diretry.

Editor memakai MessageBanner dan token tema yang sama. Pesan membedakan hasil awal yang belum diketahui dengan penolakan percobaan terakhir. Setelah receipt diketahui sukses, banner hasil belum pasti disembunyikan; retry pembersihan draf tidak mengirim write ulang.

## Verifikasi

- Build native API 24, lint debug dan **196 debug unit tests lulus**, nol gagal/error/skip. Enam kasus baru mencakup rantai error, penyimpanan/pemulihan, guard create setelah 429, guard dari draf hasil restart, cancellation, serta rekonsiliasi konflik edit. [Laporan enam kasus](write-recovery/recovery-green.xml).
- **23 eksekusi native, 15 metode unik, nol gagal/skip**: 13 kasus normal; prepare dan restore yang dipisahkan force-stop proses; delapan kasus editor pada font 200%. [Hasil perangkat](write-recovery/native-result.json) dan log berada bersama bukti gambar.
- Restore memakai PID Android berbeda, mempertahankan seluruh field/UUID/error 429 dan readonly input. Retry atas catatan yang sudah commit menghasilkan receipt replay, tanpa catatan/XP tambahan. Pengujian UI ini memakai adapter dummy; lifecycle Go/PostgreSQL terpisah berada pada [QA catatan](BACKEND_RECORDS.md) dan [QA ringkasan](BACKEND_SUMMARIES.md).
- Tema terang/gelap dan font 100%/200% memeriksa banner 429/422 tanpa overflow teks, disabled semantics dan CTA minimal 48 dp yang dapat dibawa ke dalam viewport ber-inset. Scroll tetap diperlukan untuk formulir panjang. Tersimpan 29 PNG pada `write-recovery/phone-normal`, `phone-200` dan `process-death`.
- Pemeriksaan render langsung meliputi banner/CTA pada kedua tema dan dialog tutup. Pola permukaan/field/aksi tetap memakai komponen kit yang ada. Ini bukan penerimaan seluruh screen/control/navbar.
- Font/density perangkat dikembalikan ke baseline 1,0/480; emulator milik tes dihentikan. Tidak mengubah deployment backend, credential pengguna atau adapter default.

Satu percobaan native sebelumnya gagal karena tes mengharapkan banner Beranda setelah menutup editor yang berasal dari Panduan. Tes dikoreksi untuk memeriksa Panduan tetap terpilih, draf tetap paused, kemudian akses lanjut melalui Beranda. Hasil percobaan itu disimpan terpisah dan tidak ditambahkan ke angka lulus di atas.

[Manifest](write-recovery-checkpoint.json) mengikat sumber, APK dan bukti. APK aplikasi dan APK penguji dibekukan sebelum instrumentasi; hasil unit dan native tidak dicampur menjadi satu jumlah tes.

## Batas dan pekerjaan berikutnya

Penerimaan HTTP melalui seluruh screen, refresh/pergantian akun, migrasi DataStore, resize/rotasi dinamis, memory-pressure kill, TalkBack, perangkat fisik/API lebih baru serta seluruh [matriks desain](../../tasks/ITERATION_2_DESIGN.md) masih terbuka. Gate HTTPS publik Android 7 tetap perlu diperbaiki pengelola BCC; recheck host masih menemukan leaf P-384 dan handshake P-256 gagal. Uji dummy/perangkat ini tidak menggantikan gate tersebut.

Progres seluruh backlog dan rencana backend/AI dicatat pada [ledger Android](../../tasks/ANDROID_PROGRESS.md).
