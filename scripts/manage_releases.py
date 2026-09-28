#!/usr/bin/env python3
"""
WasteNot APK Release & Landing Page Manager
- Scans /var/www/wastenot/apk/ for wastenot-*.apk
- Retains maximum 3 versioned APKs (latest + 2 previous)
- Deletes older APKs
- Syncs latest.apk
- Generates index.html (landing page) and versions.json
"""

import os
import sys
import glob
import json
import hashlib
from datetime import datetime

MAX_VERSIONS = 3

def get_sha256(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def format_size(bytes_size):
    mb = bytes_size / (1024 * 1024)
    return f"{mb:.1f} MB"

def manage_releases(base_dir="/var/www/wastenot"):
    apk_dir = os.path.join(base_dir, "apk")
    os.makedirs(apk_dir, exist_ok=True)

    # Find all versioned apk files (ignore latest.apk)
    all_apks = [
        f for f in glob.glob(os.path.join(apk_dir, "wastenot-*.apk"))
        if not f.endswith("latest.apk")
    ]

    # Sort by modification time descending (newest first)
    all_apks.sort(key=lambda x: os.path.getmtime(x), reverse=True)

    # Enforce retention: keep at most MAX_VERSIONS
    kept_apks = all_apks[:MAX_VERSIONS]
    deleted_apks = all_apks[MAX_VERSIONS:]

    for old_apk in deleted_apks:
        try:
            os.remove(old_apk)
            print(f"[Retention] Pruned old APK: {os.path.basename(old_apk)}")
        except Exception as e:
            print(f"[Retention Error] Failed to delete {old_apk}: {e}", file=sys.stderr)

    # Ensure latest.apk points to the newest kept APK
    latest_apk_path = os.path.join(apk_dir, "latest.apk")
    if kept_apks:
        newest_apk = kept_apks[0]
        try:
            if os.path.islink(latest_apk_path) or os.path.exists(latest_apk_path):
                os.remove(latest_apk_path)
            # Create a hard link or copy
            try:
                os.link(newest_apk, latest_apk_path)
            except OSError:
                import shutil
                shutil.copy2(newest_apk, latest_apk_path)
            print(f"[Sync] Updated latest.apk -> {os.path.basename(newest_apk)}")
        except Exception as e:
            print(f"[Sync Error] Failed to update latest.apk: {e}", file=sys.stderr)

    # Build version metadata
    versions_data = []
    for idx, apk_path in enumerate(kept_apks):
        fname = os.path.basename(apk_path)
        stat = os.stat(apk_path)
        mtime = datetime.fromtimestamp(stat.st_mtime)
        version_label = fname.replace("wastenot-v", "").replace("wastenot-", "").replace(".apk", "")
        
        versions_data.append({
            "version": version_label,
            "filename": fname,
            "size": format_size(stat.st_size),
            "bytes": stat.st_size,
            "date": mtime.strftime("%d %b %Y, %H:%M WIB"),
            "is_latest": (idx == 0),
            "download_url": f"/wastenot/apk/{fname}",
            "sha256": get_sha256(apk_path)[:12]
        })

    # Save versions.json
    json_path = os.path.join(base_dir, "versions.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump({
            "latest": versions_data[0] if versions_data else None,
            "versions": versions_data,
            "max_retained": MAX_VERSIONS,
            "updated_at": datetime.now().isoformat()
        }, f, indent=2)
    print(f"[Metadata] Generated {json_path}")

    # Generate HTML Landing Page
    html_content = generate_html(versions_data)
    html_path = os.path.join(base_dir, "index.html")
    with open(html_path, "w", encoding="utf-8") as f:
        f.write(html_content)
    print(f"[Landing Page] Generated {html_path} with {len(versions_data)} version(s)")

def generate_html(versions):
    latest = versions[0] if versions else None
    
    version_cards_html = ""
    if versions:
        for v in versions:
            badge_class = "badge-latest" if v["is_latest"] else "badge-archive"
            badge_label = "LATEST RELEASE" if v["is_latest"] else "PREVIOUS BUILD"
            version_cards_html += f"""
            <div class="version-item">
                <div class="version-info">
                    <div class="version-title-row">
                        <span class="version-name">v{v['version']}</span>
                        <span class="badge {badge_class}">{badge_label}</span>
                    </div>
                    <div class="version-meta">
                        <span>📅 {v['date']}</span>
                        <span>📦 {v['size']}</span>
                        <span>🔒 SHA-256: <code>{v['sha256']}...</code></span>
                    </div>
                </div>
                <div class="version-action">
                    <a href="{v['download_url']}" class="btn-download-sm" download>
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/>
                        </svg>
                        Unduh APK
                    </a>
                </div>
            </div>
            """
    else:
        version_cards_html = """
        <div class="no-version">
            <p>Belum ada build APK yang tersedia. Build baru akan otomatis muncul setelah pipeline CI/CD dijalankan.</p>
        </div>
        """

    latest_version_str = f"v{latest['version']}" if latest else "v1.0.0"
    latest_meta_str = f"{latest['size']} · {latest['date']}" if latest else "Siap di-build via GitHub Actions"

    return f"""<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>WasteNot Android — Unduh APK Resmi</title>
    <meta name="description" content="Aplikasi pencatatan kebiasaan 3R (Reduce, Reuse, Recycle) dengan AI Waste Detection. Unduh APK resmi untuk Android.">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    <style>
        :root {{
            --pine-900: #123F30;
            --pine-800: #195B3D;
            --pine-700: #226948;
            --pine-100: #E0EFDF;
            --lime-500: #D2EF69;
            --lime-600: #C9EB72;
            --lime-text: #24390E;
            --bg: #F4F7F2;
            --surface: #FFFFFF;
            --surface-variant: #E8EFE7;
            --text-main: #182B20;
            --text-muted: #526457;
            --border: #D9E3D8;
            --radius-lg: 24px;
            --radius-md: 16px;
            --radius-sm: 10px;
        }}

        * {{
            margin: 0;
            padding: 0;
            box-sizing: border-box;
            font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        }}

        body {{
            background-color: var(--bg);
            color: var(--text-main);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            line-height: 1.5;
        }}

        header {{
            background: linear-gradient(135deg, var(--pine-900) 0%, var(--pine-700) 100%);
            color: #FFFFFF;
            padding: 48px 24px 64px 24px;
            text-align: center;
            position: relative;
            overflow: hidden;
        }}

        header::after {{
            content: '';
            position: absolute;
            bottom: 0;
            left: 0;
            right: 0;
            height: 32px;
            background: var(--bg);
            border-radius: 32px 32px 0 0;
        }}

        .brand-badge {{
            display: inline-flex;
            align-items: center;
            gap: 8px;
            background: rgba(210, 239, 105, 0.2);
            border: 1px solid rgba(210, 239, 105, 0.4);
            color: var(--lime-500);
            padding: 6px 14px;
            border-radius: 999px;
            font-size: 0.85rem;
            font-weight: 700;
            margin-bottom: 20px;
            letter-spacing: 0.5px;
        }}

        .logo-container {{
            width: 76px;
            height: 76px;
            background: rgba(255, 255, 255, 0.12);
            backdrop-filter: blur(8px);
            border: 1px solid rgba(255, 255, 255, 0.25);
            border-radius: 22px;
            margin: 0 auto 16px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 38px;
            box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
        }}

        h1 {{
            font-size: 2.5rem;
            font-weight: 800;
            letter-spacing: -0.5px;
            margin-bottom: 12px;
        }}

        .tagline {{
            font-size: 1.15rem;
            color: #E2ECE5;
            max-width: 580px;
            margin: 0 auto 28px;
            font-weight: 400;
        }}

        .hero-action {{
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 12px;
        }}

        .btn-main {{
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 12px;
            background: var(--lime-500);
            color: var(--lime-text);
            text-decoration: none;
            font-size: 1.15rem;
            font-weight: 800;
            padding: 16px 36px;
            border-radius: 999px;
            box-shadow: 0 10px 25px rgba(210, 239, 105, 0.35);
            transition: all 0.2s ease;
        }}

        .btn-main:hover {{
            background: var(--lime-600);
            transform: translateY(-2px);
            box-shadow: 0 14px 28px rgba(210, 239, 105, 0.45);
        }}

        .hero-meta {{
            font-size: 0.85rem;
            color: #C0D6C8;
        }}

        main {{
            flex: 1;
            max-width: 860px;
            width: 100%;
            margin: -10px auto 40px;
            padding: 0 20px;
            display: flex;
            flex-direction: column;
            gap: 32px;
        }}

        .card {{
            background: var(--surface);
            border-radius: var(--radius-lg);
            padding: 28px;
            border: 1px solid var(--border);
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.03);
        }}

        .card-header {{
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 20px;
            padding-bottom: 14px;
            border-bottom: 1px solid var(--border);
        }}

        .card-title {{
            font-size: 1.25rem;
            font-weight: 700;
            display: flex;
            align-items: center;
            gap: 10px;
            color: var(--text-main);
        }}

        .card-subtitle {{
            font-size: 0.85rem;
            color: var(--text-muted);
        }}

        /* Versions List */
        .version-list {{
            display: flex;
            flex-direction: column;
            gap: 14px;
        }}

        .version-item {{
            display: flex;
            align-items: center;
            justify-content: space-between;
            background: var(--surface-variant);
            border: 1px solid var(--border);
            border-radius: var(--radius-md);
            padding: 18px 20px;
            transition: border-color 0.2s;
        }}

        .version-item:hover {{
            border-color: var(--pine-800);
        }}

        .version-title-row {{
            display: flex;
            align-items: center;
            gap: 10px;
            margin-bottom: 6px;
        }}

        .version-name {{
            font-size: 1.15rem;
            font-weight: 800;
            color: var(--pine-800);
        }}

        .badge {{
            font-size: 0.7rem;
            font-weight: 800;
            padding: 3px 8px;
            border-radius: 6px;
            letter-spacing: 0.5px;
        }}

        .badge-latest {{
            background: var(--pine-100);
            color: var(--pine-800);
            border: 1px solid #B8DCB7;
        }}

        .badge-archive {{
            background: #E8EBE6;
            color: var(--text-muted);
        }}

        .version-meta {{
            display: flex;
            flex-wrap: wrap;
            gap: 14px;
            font-size: 0.82rem;
            color: var(--text-muted);
        }}

        .version-meta code {{
            background: rgba(0, 0, 0, 0.05);
            padding: 2px 6px;
            border-radius: 4px;
            font-family: monospace;
        }}

        .btn-download-sm {{
            display: inline-flex;
            align-items: center;
            gap: 6px;
            background: var(--pine-800);
            color: #FFFFFF;
            text-decoration: none;
            padding: 10px 18px;
            border-radius: var(--radius-sm);
            font-size: 0.9rem;
            font-weight: 700;
            transition: all 0.15s ease;
        }}

        .btn-download-sm:hover {{
            background: var(--pine-700);
            transform: translateY(-1px);
        }}

        /* Features */
        .features-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
            gap: 16px;
        }}

        .feature-box {{
            background: var(--surface-variant);
            border: 1px solid var(--border);
            border-radius: var(--radius-md);
            padding: 20px;
        }}

        .feature-icon {{
            font-size: 1.8rem;
            margin-bottom: 10px;
        }}

        .feature-title {{
            font-size: 1rem;
            font-weight: 700;
            margin-bottom: 6px;
            color: var(--pine-900);
        }}

        .feature-desc {{
            font-size: 0.85rem;
            color: var(--text-muted);
        }}

        /* Guide */
        .steps-list {{
            display: flex;
            flex-direction: column;
            gap: 14px;
        }}

        .step-row {{
            display: flex;
            align-items: flex-start;
            gap: 14px;
        }}

        .step-num {{
            width: 32px;
            height: 32px;
            border-radius: 50%;
            background: var(--pine-100);
            color: var(--pine-800);
            display: flex;
            align-items: center;
            justify-content: center;
            font-weight: 800;
            font-size: 0.9rem;
            flex-shrink: 0;
        }}

        .step-text h4 {{
            font-size: 0.95rem;
            font-weight: 700;
            margin-bottom: 2px;
        }}

        .step-text p {{
            font-size: 0.85rem;
            color: var(--text-muted);
        }}

        footer {{
            text-align: center;
            padding: 30px 20px 40px;
            color: var(--text-muted);
            font-size: 0.85rem;
            border-top: 1px solid var(--border);
            background: var(--surface);
        }}

        footer a {{
            color: var(--pine-800);
            text-decoration: none;
            font-weight: 600;
        }}

        @media (max-width: 600px) {{
            h1 {{ font-size: 2rem; }}
            .version-item {{
                flex-direction: column;
                align-items: flex-start;
                gap: 16px;
            }}
            .version-action {{ width: 100%; }}
            .btn-download-sm {{ width: 100%; justify-content: center; }}
        }}
    </style>
</head>
<body>

    <header>
        <div class="brand-badge">🌱 OFFICIAL ANDROID BUILD</div>
        <div class="logo-container">♻️</div>
        <h1>WasteNot Android</h1>
        <p class="tagline">Personal Waste Tracking & AI-Assisted Waste Detection (3R: Reduce, Reuse, Recycle, dan Trash).</p>
        
        <div class="hero-action">
            <a href="/wastenot/download" class="btn-main" id="btn-download-latest">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/>
                </svg>
                Download APK Terbaru ({latest_version_str})
            </a>
            <span class="hero-meta">{latest_meta_str}</span>
        </div>
    </header>

    <main>
        <!-- Available Versions Section (Retention max 3) -->
        <section class="card">
            <div class="card-header">
                <div>
                    <h2 class="card-title">📦 Versi Tersedia</h2>
                    <p class="card-subtitle">Maksimal 3 versi terakhir tersimpan (terbaru + 2 versi sebelumnya). Build terlama otomatis dihapus.</p>
                </div>
                <span class="badge badge-latest">MAX 3 BUILDS</span>
            </div>

            <div class="version-list">
                {version_cards_html}
            </div>
        </section>

        <!-- Feature Highlights -->
        <section class="card">
            <div class="card-header">
                <div>
                    <h2 class="card-title">✨ Fitur Utama Aplikasi</h2>
                    <p class="card-subtitle">Dirancang native dengan Jetpack Compose & Navigation 3</p>
                </div>
            </div>
            
            <div class="features-grid">
                <div class="feature-box">
                    <div class="feature-icon">🌿</div>
                    <h3 class="feature-title">Personal 3R Tracking</h3>
                    <p class="feature-desc">Pencatatan sampah mudah dalam berat (gram) atau jumlah item tanpa konversi spekulatif.</p>
                </div>
                <div class="feature-box">
                    <div class="feature-icon">📷</div>
                    <h3 class="feature-title">AI Waste Detection</h3>
                    <p class="feature-desc">Deteksi jenis objek sampah via kamera dengan YOLO dan panduan penanganan rekomendasi LLM.</p>
                </div>
                <div class="feature-box">
                    <div class="feature-icon">📊</div>
                    <h3 class="feature-title">Statistik & Evaluasi</h3>
                    <p class="feature-desc">Visualisasi grafik mingguan/bulanan dan recycling rate yang terhitung otomatis dan akurat.</p>
                </div>
                <div class="feature-box">
                    <div class="feature-icon">🏆</div>
                    <h3 class="feature-title">Gamifikasi XP & Level</h3>
                    <p class="feature-desc">Peroleh 10 XP untuk setiap catatan aktivitas baru untuk mendorong kebiasaan ramah lingkungan.</p>
                </div>
            </div>
        </section>

        <!-- Installation Instructions -->
        <section class="card">
            <div class="card-header">
                <div>
                    <h2 class="card-title">📲 Panduan Instalasi APK</h2>
                    <p class="card-subtitle">Langkah cepat menginstal aplikasi di perangkat Android</p>
                </div>
            </div>

            <div class="steps-list">
                <div class="step-row">
                    <div class="step-num">1</div>
                    <div class="step-text">
                        <h4>Unduh File APK</h4>
                        <p>Tekan tombol <strong>Download APK Terbaru</strong> di atas atau pilih versi tertentu pada daftar riwayat.</p>
                    </div>
                </div>
                <div class="step-row">
                    <div class="step-num">2</div>
                    <div class="step-text">
                        <h4>Buka File APK yang Diunduh</h4>
                        <p>Buka notifikasi unduhan browser atau cari file APK di folder <code>Downloads</code> perangkat Android kamu.</p>
                    </div>
                </div>
                <div class="step-row">
                    <div class="step-num">3</div>
                    <div class="step-text">
                        <h4>Izinkan Instalasi dari Sumber Tidak Dikenal</h4>
                        <p>Jika muncul peringatan keamanan, pilih <strong>Pengaturan / Settings</strong> lalu aktifkan <em>"Izinkan dari sumber ini" (Allow from this source)</em>.</p>
                    </div>
                </div>
                <div class="step-row">
                    <div class="step-num">4</div>
                    <div class="step-text">
                        <h4>Instal dan Buka WasteNot</h4>
                        <p>Tekan <strong>Install</strong> dan tunggu hingga proses selesai. Buka WasteNot dan mulai catat kebiasaan 3R kamu!</p>
                    </div>
                </div>
            </div>
        </section>
    </main>

    <footer>
        <p><strong>WasteNot Android</strong> &copy; 2026 WasteNot Engineering Team.</p>
        <p style="margin-top: 6px;">
            <a href="https://github.com/waste-not-app/waste-not-android" target="_blank" rel="noopener">GitHub Repository</a> · 
            <a href="/wastenot/download">Direct Download APK</a> · 
            <a href="/wastenot/versions.json">Versions JSON</a>
        </p>
    </footer>

</body>
</html>
"""

if __name__ == "__main__":
    target_dir = sys.argv[1] if len(sys.argv) > 1 else "/var/www/wastenot"
    manage_releases(target_dir)
