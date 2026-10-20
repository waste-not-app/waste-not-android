# Bukti baseline prototipe

Tanggal: 20 Oktober 2026. Status: eksperimen lokal, belum release pilot.

## Pembaruan demo kamera

Demo kamera browser ditambahkan setelah baseline awal di bawah. Katalog versi `demo-catalog-03` / mapping `coco-reviewed-objects-03` kini mempunyai 68 pilihan benda/material dan 58 kelas visual COCO yang relevan. Angka/hasil baseline lama tetap menunjukkan cakupan awal; label seperti meja dan piza sekarang dapat dipilih, dengan panduan pencegahan atau penanganan sesuai evidence.

Gemini kini **sudah diuji langsung** memakai konfigurasi lokal pengguna: adapter CLI menghasilkan READY/RECYCLE pada konteks botol kaca. Pengujian browser juga berhasil melalui YOLO nyata → bbox botol → konfirmasi → Gemini langsung → rekomendasi tervalidasi. Pembaruan empat metode juga diuji live di browser: botol kaca → RECYCLE, botol dengan fokus pencegahan → REDUCE, buku layak pakai ulang → REUSE, dan sikat gigi plastik manual tidak layak tanpa akses daur ulang khusus → TRASH. Tiap hasil memiliki satu label, alasan dan tautan sumber yang sesuai. API key tidak dicatat dalam laporan atau dikirim ke browser.

Pengujian Python terbaru: **93 tes lulus** untuk policy, mapping, worker serta API demo lokal, termasuk rotasi key, klasifikasi risiko, koreksi objek berisiko pada CLI dan deadline Gemini. Pengujian browser: **12 pemeriksaan lulus, tanpa error JavaScript**, menggunakan kamera palsu Chromium, termasuk capture yang menghentikan track, file JPEG untuk YOLO nyata, bbox bertumpuk, pilihan objek, empat label Gemini live, perubahan kondisi, peringatan risiko, katalog manual, foto dibersihkan, viewport 390 px dan kamera ditolak. Kamera fisik tetap perlu dicoba oleh pengguna.

Review sebelum publikasi menemukan dan memperbaiki dua masalah: CLI sekarang mempertahankan risiko dari objek deteksi meskipun material/jenis dikoreksi; Gemini kini memiliki deadline keseluruhan yang dapat membatalkan request, dan menolak respons terlambat. Kedua jalur mendapat tes regresi. Smoke worker dengan YOLO nyata, browser dengan Gemini live, dan build wheel beserta resource/exclusion verifier kembali lulus. Workflow AI CI menjalankan tes offline tanpa key/model/foto pengguna.

Policy-03 diverifikasi live melalui browser: gelas kaca dengan integrity=DAMAGED menghasilkan **READY/TRASH**, alasan, sumber pecahan kaca Cornwall, langkah pengemasan tanpa langkah reuse, serta caution penanganan khusus di bawah label. Baterai menghasilkan **READY/RECYCLE** dengan rute pengumpul khusus dan caution. Penanganan khusus tidak lagi mengganti label utama atau menghentikan Gemini. Screenshot lokal: `ai/artifacts/camera-demo-special-classification.png`.

Rotasi lima key diverifikasi dengan transport HTTP palsu: urutan round-robin, request bersamaan, skip key cooldown, Retry-After/RetryInfo, semua key dibatasi, key ditolak 401/403, satu anggaran timeout, tanpa retry untuk timeout/5xx/output rusak, pemilihan env, redaksi doctor dan pembersihan secret worker. Kelima slot lokal masih kosong saat verifikasi. Smoke Gemini live setelah perubahan berhasil READY/RECYCLE menggunakan key tunggal lama; tidak menguji lima kredensial nyata atau sengaja menghabiskan kuota. Hasil lokal: `ai/artifacts/gemini-rotation-legacy-smoke.json`.

Laporan lokal: `ai/artifacts/camera-demo-browser.json`; screenshot: `camera-demo-desktop.png`, `camera-demo-gemini.png`, `camera-demo-mobile.png`. Script reproduksi: `ai/scripts/verify_camera_demo.py --live-gemini` dengan server demo aktif. Foto mentah dan key tidak disertakan dalam Git.

## Lingkungan dan artifact

- Windows 11, Python 3.13.1, Ultralytics 8.4.183, PyTorch 2.14.1+cpu, torchvision 0.29.1.
- GPU fisik RTX 4070 Laptop 8 GB terdeteksi sistem; runtime pengujian memakai CPU, CUDA=False.
- Artifact YOLO11n COCO resmi `v8.3.0/yolo11n.pt`, 5.613.764 byte.
- SHA-256: `0ebbc80d4a7680d14987a577cd21342b65ecfd94632bd9a8da63ae6417644ee1`.
- 80 label aktual; `bottle` pada ID 39. Tidak ada kelas khusus aluminium_can/cardboard/battery.
- Input model 640, EXIF transpose + RGB, preprocessing/letterbox dan postprocessing Ultralytics.

## Inference nyata

Satu sampel publik TACO image_id=0, resolusi 480×640. Sampel digunakan hanya untuk smoke lokal, tidak untuk training/holdout. Metadata sumber tidak mencantumkan lisensi gambar (`license=null`); foto tidak disertakan dalam Git dan belum disetujui untuk redistribusi/training. Sumber: [TACO](https://github.com/pedropro/TACO) dan [metadata](https://raw.githubusercontent.com/pedropro/TACO/master/data/annotations.json). Jangan menganggap lisensi kode sebagai lisensi foto.

SHA-256 sampel: `2107981d12851a3205d008483ab8b5f150183c0939359bd9902f170aecd65dea`.

| Pengamatan | Hasil |
|---|---|
| Deteksi bottle | Score 0,7836598; OFFERED, perlu konfirmasi material |
| Bbox normalized | [0,33422; 0,06429; 0,62478; 0,70669] |
| Model juga mengeluarkan | person, dining table, pizza; semuanya ditolak mapping pilot dan diarahkan manual |
| Latency warm p50 | 90,33 ms |
| Latency warm p95 nearest-rank | 93,41 ms |
| Ukuran pengukuran | Satu gambar, satu warm-up + lima pengulangan; tidak termasuk waktu load model/proses CLI |
| Akurasi pilot / mAP / recall | BELUM DIUKUR |

JSON lengkap hasil lokal berada di `ai/artifacts/detection.json` dan `ai/artifacts/baseline.json`. Timing hanya berlaku untuk sampel/runtime/perangkat ini dan tidak menjanjikan performa endpoint Android.

## Alur lengkap yang sudah dicoba

YOLO nyata → object-3/bottle → konteks uji glass_bottle/CLEAN/INTACT → panduan draf → provider **fixture** → validasi → RECYCLE, evidence `glass-intact-recycle-v1` → menunggu konfirmasi metode/jumlah. Tidak ada waste log atau XP yang dibuat. Konteks adalah input skenario uji, bukan fakta kondisi hasil kamera. Respons `prototype_only=true` dan `provider=fixture`.

`ai/artifacts/pipeline-fixture.json` memuat hasil lokal. Nama object_id mengikuti hasil request; jangan selalu memilih object-1, yang pada sampel ini adalah kelas nonpilot.

## Pengujian otomatis

**52 tes lulus** (`pytest ai/tests -q`, 0,88 detik pada pengujian terakhir). Meliputi batas 0,49/0,50/0,699/0,70, NaN/bbox invalid, taxonomy mismatch, spoof category, draf/expired/no guide, kondisi UNKNOWN, special handling, enum dan evidence palsu, REUSE dengan konteks positif, prosa LLM yang tidak diteruskan, HTTP Gemini/timeout/truncation/missing key/model, batas media/orientasi/EXIF, worker auth/overload/deadline, dan alur tanpa autosave.

`ai/scripts/verify_runtime.py` juga lulus dengan **model YOLO nyata melalui adapter HTTP privat** (TestClient, bukan server publik): auth wajib, deteksi berhasil, satu objek dipilih, rekomendasi fixture READY, saved_log=false. Ringkasan lokal: `ai/artifacts/runtime-smoke.json`.

## Yang belum dibuktikan

- Evaluasi kualitas Gemini pada kumpulan kasus pilot: belum dilakukan. Smoke live berhasil sebagaimana pembaruan di atas; satu hasil sukses tidak membuktikan kualitas pada seluruh kelas.
- Lima kelas target: belum ada dataset pilot berlisensi dan holdout, belum fine-tuning.
- Konten: seluruh 68 panduan masih DRAFT, belum review penanggung jawab lokal.
- Integrasi produksi: auth/sesi/idempotensi/budget/retensi Go, kamera Android dan AT-AI-01–18 belum diuji oleh prototipe CLI ini.

Panduan reproduksi ada di [README](../README.md); pembagian integrasi ada di [HANDOFF](HANDOFF.md).
