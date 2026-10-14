# Catatan pekerjaan Android

Setiap task yang dikerjakan memiliki catatan perubahan, bukti pengujian, dan pekerjaan tersisa di folder ini. Checkbox issue GitHub mengikuti bukti tersebut; status review tidak berarti fitur sudah terintegrasi atau dirilis.

| Task | Issue | Catatan | Progres |
|---|---|---|---|
| A01 Fondasi dan environment | [#3](https://github.com/waste-not-app/waste-not-android/issues/3) | [A01](A01-foundation.md) | Implementasi dan startup diperiksa; review PR #88 |
| A02 Model dan parser | [#5](https://github.com/waste-not-app/waste-not-android/issues/5) | [A02](A02-domain-models.md) | Model/tes tersedia; wiring layar belum selesai |
| A03 HTTP, DTO, metadata, error | [#6](https://github.com/waste-not-app/waste-not-android/issues/6) | [A03](A03-network.md) | Transport/fixture diuji dan container terhubung; kontrak backend serta wiring layar masih terbuka |
| A04 Credential dan refresh sesi | [#21](https://github.com/waste-not-app/waste-not-android/issues/21) | [A04](A04-session.md) | Keystore diuji perangkat; repository sesi/refresh dan isolasi akun diuji fixture; wiring UI/backend masih terbuka |
| A11 Startup dan pemulihan sesi | [#34](https://github.com/waste-not-app/waste-not-android/issues/34) | [A11](A11-startup.md) | Bootstrap repository diuji; layar S01 dan root replacement belum terhubung |
| A12 Masuk | [#35](https://github.com/waste-not-app/waste-not-android/issues/35) | [A12](A12-login.md) | Validator field dan normalisasi email tersedia; form/loading/autofill/root belum terhubung |
| A13 Daftar | [#46](https://github.com/waste-not-app/waste-not-android/issues/46) | [A13](A13-registration.md) | Aturan registrasi dan fallback timezone tersedia; form serta backend nyata belum terverifikasi |
| A29 Logout | [#40](https://github.com/waste-not-app/waste-not-android/issues/40) | [A29](A29-logout.md) | Cleanup credential/job dan hasil logout diuji; dialog, back stack, dan server nyata belum terverifikasi |

Current iteration juga mencakup A06, A07, A09, dan A10. Catatan implementasinya ditambahkan ketika pekerjaan tersebut dilakukan. Kriteria layar, perangkat minimum, dan backend nyata tidak dianggap terpenuhi hanya karena build atau tes fixture berhasil.
