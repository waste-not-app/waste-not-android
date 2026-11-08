#!/usr/bin/env python3
"""
scripts/prune_github_artifacts.py: Prune GitHub Actions artifacts, caches, and runs.
Keeps the latest N items (default: 3) and deletes older ones to reclaim storage quota.
"""

import argparse
import json
import re
import subprocess
import sys
from typing import Any, Dict, List, Optional


def run_command(cmd: List[str]) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)


def get_current_repo() -> Optional[str]:
    res = run_command(["git", "config", "--get", "remote.origin.url"])
    if res.returncode != 0 or not res.stdout.strip():
        return None
    url = res.stdout.strip()
    m = re.search(r"github\.com[:/]([^/]+)/([^/\.]+)(?:\.git)?", url)
    if m:
        return f"{m.group(1)}/{m.group(2)}"
    return None


def fetch_paged(endpoint: str, key: str) -> List[Dict[str, Any]]:
    items = []
    page = 1
    while True:
        sep = "&" if "?" in endpoint else "?"
        url = f"{endpoint}{sep}per_page=100&page={page}"
        res = run_command(["gh", "api", url])
        if res.returncode != 0:
            break
        try:
            data = json.loads(res.stdout)
        except Exception:
            break
        page_items = data.get(key, [])
        if not page_items:
            break
        items.extend(page_items)
        if len(page_items) < 100:
            break
        page += 1
    return items


def clean_artifacts(repo: str, keep: int, dry_run: bool) -> int:
    print(f"\n📦 \033[1;34m[Artifacts]\033[0m Memeriksa artifact di \033[1m{repo}\033[0m...")
    artifacts = fetch_paged(f"repos/{repo}/actions/artifacts", "artifacts")
    if not artifacts:
        print("   Tidak ada artifact ditemukan.")
        return 0

    by_name: Dict[str, List[Dict[str, Any]]] = {}
    for a in artifacts:
        by_name.setdefault(a["name"], []).append(a)

    to_delete = []
    for name, group in by_name.items():
        group.sort(key=lambda x: x.get("created_at", ""), reverse=True)
        kept = group[:keep]
        pruned = group[keep:]
        to_delete.extend(pruned)
        print(f"   - Group \033[36m'{name}'\033[0m: total {len(group)} | keep {len(kept)} | delete {len(pruned)}")

    if not to_delete:
        print("   Semua artifact sudah sesuai limit (≤ {} versi per nama).".format(keep))
        return 0

    reclaimed_bytes = 0
    deleted_count = 0
    for a in to_delete:
        aid = a["id"]
        aname = a.get("name", "unnamed")
        asize = a.get("size_in_bytes", 0)
        size_mb = asize / (1024 * 1024)
        if dry_run:
            print(f"   [DRY-RUN] Akan menghapus artifact: #{aid} '{aname}' ({size_mb:.2f} MB)")
            deleted_count += 1
            reclaimed_bytes += asize
        else:
            del_res = run_command(["gh", "api", "--method", "DELETE", f"repos/{repo}/actions/artifacts/{aid}"])
            if del_res.returncode == 0:
                print(f"   \033[32m✔ Terhapus:\033[0m #{aid} '{aname}' ({size_mb:.2f} MB)")
                deleted_count += 1
                reclaimed_bytes += asize
            else:
                print(f"   \033[31m✖ Gagal menghapus:\033[0m #{aid} - {del_res.stderr.strip()}")

    print(f"   \033[1mTotal artifact dihapus: {deleted_count} ({reclaimed_bytes / (1024*1024):.2f} MB dibebaskan)\033[0m")
    return deleted_count


def clean_caches(repo: str, keep: int, dry_run: bool) -> int:
    print(f"\n⚡ \033[1;33m[Actions Caches]\033[0m Memeriksa cache di \033[1m{repo}\033[0m...")
    caches = fetch_paged(f"repos/{repo}/actions/caches", "actions_caches")
    if not caches:
        print("   Tidak ada cache ditemukan.")
        return 0

    caches.sort(key=lambda x: x.get("created_at", ""), reverse=True)
    kept = caches[:keep]
    to_delete = caches[keep:]

    print(f"   Total cache: {len(caches)} | Simpan: {len(kept)} | Hapus: {len(to_delete)}")

    deleted_count = 0
    reclaimed_bytes = 0
    for c in to_delete:
        cid = c["id"]
        ckey = c.get("key", "unnamed")
        csize = c.get("size_in_bytes", 0)
        size_mb = csize / (1024 * 1024)
        if dry_run:
            print(f"   [DRY-RUN] Akan menghapus cache: #{cid} '{ckey[:50]}...' ({size_mb:.2f} MB)")
            deleted_count += 1
            reclaimed_bytes += csize
        else:
            del_res = run_command(["gh", "api", "--method", "DELETE", f"repos/{repo}/actions/caches/{cid}"])
            if del_res.returncode == 0:
                print(f"   \033[32m✔ Terhapus cache:\033[0m #{cid} ({size_mb:.2f} MB)")
                deleted_count += 1
                reclaimed_bytes += csize
            else:
                print(f"   \033[31m✖ Gagal menghapus cache:\033[0m #{cid} - {del_res.stderr.strip()}")

    print(f"   \033[1mTotal cache dihapus: {deleted_count} ({reclaimed_bytes / (1024*1024):.2f} MB dibebaskan)\033[0m")
    return deleted_count


def clean_runs(repo: str, keep: int, dry_run: bool) -> int:
    print(f"\n🚀 \033[1;35m[Workflow Runs]\033[0m Memeriksa workflow runs di \033[1m{repo}\033[0m...")
    runs = fetch_paged(f"repos/{repo}/actions/runs", "workflow_runs")
    if not runs:
        print("   Tidak ada workflow run ditemukan.")
        return 0

    by_wf: Dict[str, List[Dict[str, Any]]] = {}
    for r in runs:
        wfname = r.get("name", "unnamed")
        by_wf.setdefault(wfname, []).append(r)

    to_delete = []
    for wfname, group in by_wf.items():
        group.sort(key=lambda x: x.get("created_at", ""), reverse=True)
        kept = group[:keep]
        pruned = group[keep:]
        to_delete.extend(pruned)
        print(f"   - Workflow \033[36m'{wfname}'\033[0m: total {len(group)} | keep {len(kept)} | delete {len(pruned)}")

    deleted_count = 0
    for r in to_delete:
        rid = r["database_id"] if "database_id" in r else r["id"]
        title = r.get("display_title", r.get("name", ""))
        if dry_run:
            print(f"   [DRY-RUN] Akan menghapus run: #{rid} '{title[:40]}'")
            deleted_count += 1
        else:
            del_res = run_command(["gh", "api", "--method", "DELETE", f"repos/{repo}/actions/runs/{rid}"])
            if del_res.returncode == 0:
                print(f"   \033[32m✔ Terhapus run:\033[0m #{rid}")
                deleted_count += 1
            else:
                print(f"   \033[31m✖ Gagal menghapus run:\033[0m #{rid} - {del_res.stderr.strip()}")

    print(f"   \033[1mTotal run dihapus: {deleted_count}\033[0m")
    return deleted_count


def get_org_repos(org: str) -> List[str]:
    repos = []
    res = run_command(["gh", "api", f"orgs/{org}/repos?per_page=100", "--jq", ".[].full_name"])
    if res.returncode == 0 and res.stdout.strip():
        repos = [line.strip() for line in res.stdout.strip().splitlines() if line.strip()]
    return repos


def main():
    parser = argparse.ArgumentParser(
        description="Bersihkan GitHub Actions artifacts, caches, atau workflow runs lama dan sisakan 2-3 versi terbaru."
    )
    parser.add_argument(
        "--repo",
        type=str,
        default=None,
        help="Target repository dalam format OWNER/REPO (default: repo git saat ini)",
    )
    parser.add_argument(
        "--org",
        type=str,
        default=None,
        help="Target seluruh repository di bawah organization (contoh: waste-not-app)",
    )
    parser.add_argument(
        "--keep",
        type=int,
        default=3,
        help="Jumlah versi/item terbaru yang disimpan (default: 3)",
    )
    parser.add_argument(
        "--caches",
        action="store_true",
        help="Bersihkan GitHub Actions caches juga",
    )
    parser.add_argument(
        "--runs",
        action="store_true",
        help="Bersihkan riwayat workflow runs lama juga",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Bersihkan artifacts, caches, dan runs sekaligus",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Tampilkan apa yang akan dihapus tanpa benar-benar menghapus",
    )

    args = parser.parse_args()

    targets = []
    if args.org:
        print(f"🔍 Mencari repository di organization \033[1m{args.org}\033[0m...")
        targets = get_org_repos(args.org)
        if not targets:
            print(f"Tidak ada repository ditemukan di organization {args.org}.")
            sys.exit(1)
        print(f"Ditemukan {len(targets)} repository: {', '.join(targets)}")
    elif args.repo:
        targets = [args.repo]
    else:
        detected = get_current_repo()
        if not detected:
            print("Error: Tidak berada di repository git dan opsi --repo atau --org tidak diberikan.")
            sys.exit(1)
        targets = [detected]

    clean_art = True
    clean_c = args.caches or args.all
    clean_r = args.runs or args.all

    print("=" * 60)
    print(f"\033[1;32mGitHub Actions Cleanup Tool\033[0m")
    print(f"Target Repos : {', '.join(targets)}")
    print(f"Keep Latest  : {args.keep} item")
    print(f"Clean Types  : Artifacts{' + Caches' if clean_c else ''}{' + Runs' if clean_r else ''}")
    print(f"Mode         : {'\033[33mDRY-RUN (Simulasi)\033[0m' if args.dry_run else '\033[31mEXECUTION (Hapus Nyata)\033[0m'}")
    print("=" * 60)

    for repo in targets:
        if clean_art:
            clean_artifacts(repo, args.keep, args.dry_run)
        if clean_c:
            clean_caches(repo, args.keep, args.dry_run)
        if clean_r:
            clean_runs(repo, args.keep, args.dry_run)

    print("\n\033[1;32m✔ Selesai!\033[0m\n")


if __name__ == "__main__":
    main()
