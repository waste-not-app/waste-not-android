# Catatan pekerjaan Android

Setiap task yang dikerjakan memiliki catatan perubahan, bukti pengujian, dan pekerjaan tersisa di folder ini. Checkbox issue GitHub mengikuti bukti tersebut; status review tidak berarti fitur sudah terintegrasi atau dirilis.

Bukti lintas task, batas sumber contoh/backend, regresi yang diperbaiki, dan langkah mengulang uji tersedia di [QA Iteration 1](ITERATION_1_QA.md).

| Task | Issue | Catatan | Progres |
|---|---|---|---|
| A01 Fondasi dan environment | [#3](https://github.com/waste-not-app/waste-not-android/issues/3) | [A01](A01-foundation.md) | Implementasi dan startup diperiksa; review PR #88 |
| A02 Model dan parser | [#5](https://github.com/waste-not-app/waste-not-android/issues/5) | [A02](A02-domain-models.md) | Parser/payload eksklusif dipakai create/edit; unit serta denominator dataset contoh diuji layar |
| A03 HTTP, DTO, metadata, error | [#6](https://github.com/waste-not-app/waste-not-android/issues/6) | [A03](A03-network.md) | Transport, metadata, bearer, error dan adapter client tersedia; kontrak server mengikuti integrasi backend |
| A04 Credential dan refresh sesi | [#21](https://github.com/waste-not-app/waste-not-android/issues/21) | [A04](A04-session.md) | Keystore API 24/36, refresh terserialisasi dan identity gate terhubung ke root |
| A06 Tema dan font | [#7](https://github.com/waste-not-app/waste-not-android/issues/7) | [A06](A06-theme.md) | Lima font/OFL, kontras, sistem/terang/gelap dan UI font 200%; lihat matriks QA |
| A07 Komponen form | [#10](https://github.com/waste-not-app/waste-not-android/issues/10) | [A07](A07-form-controls.md) | Kontrol bersama dipakai auth/filter/range/create/edit; keyboard/fokus dan batas kalender diperiksa |
| A09 Root/navigasi | [#28](https://github.com/waste-not-app/waste-not-android/issues/28) | [A09](A09-navigation.md) | Lima stack, filter/range, rotasi, detail/editor, dirty Back dan isolasi identitas diuji |
| A10 Dock/rail | [#33](https://github.com/waste-not-app/waste-not-android/issues/33) | [A10](A10-dock.md) | Dock lima tujuan, action manual dan rail adaptif; font besar tidak diperkecil |
| A11 Startup dan pemulihan sesi | [#34](https://github.com/waste-not-app/waste-not-android/issues/34) | [A11](A11-startup.md) | S01, retry/masuk ulang, root replacement dan larangan profil belum terverifikasi tersedia |
| A12 Masuk | [#35](https://github.com/waste-not-app/waste-not-android/issues/35) | [A12](A12-login.md) | S02, validasi, IME/autofill semantics, loading/error, secret in-memory dan root terhubung |
| A13 Daftar | [#46](https://github.com/waste-not-app/waste-not-android/issues/46) | [A13](A13-registration.md) | S03, validasi/konfirmasi, timezone terlihat dan root diuji gateway fixture |
| A29 Logout | [#40](https://github.com/waste-not-app/waste-not-android/issues/40) | [A29](A29-logout.md) | D02 cancel/confirm, hasil cleanup, pembuangan stack/cache dan hasil lama diuji client |

Seluruh 12 task Android current iteration memiliki catatan di sini. Kriteria layar/perangkat tidak dianggap terpenuhi hanya karena build atau tes fixture berhasil. Arahan pengguna mengizinkan dummy untuk tahap Android: penerimaan client memakai sumber contoh berlabel, sedangkan endpoint, pencabutan sesi server dan persistensi nyata tetap diverifikasi pada task backend/integrasi; tidak diklaim selesai oleh dummy.

Arahan terbaru mengizinkan dummy/placeholder untuk layar yang membutuhkan backend atau AI/ML. UI menandai data contoh dan memakai batas sumber data yang dapat diganti ke API. Bukti dummy tidak diklaim sebagai bukti backend atau inference nyata; integrasi server mengikuti rencana kontrak ketika tersedia.
