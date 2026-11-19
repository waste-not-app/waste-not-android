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
    print(f"\n📦 \033[1;34m[Artifacts]\033[0m Checking artifacts in \033[1m{repo}\033[0m...")
    artifacts = fetch_paged(f"repos/{repo}/actions/artifacts", "artifacts")
    if not artifacts:
        print("   No artifacts found.")
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
        print("   All artifacts are within the limit (≤ {} versions per name).".format(keep))
        return 0

    reclaimed_bytes = 0
    deleted_count = 0
    for a in to_delete:
        aid = a["id"]
        aname = a.get("name", "unnamed")
        asize = a.get("size_in_bytes", 0)
        size_mb = asize / (1024 * 1024)
        if dry_run:
            print(f"   [DRY-RUN] Would delete artifact: #{aid} '{aname}' ({size_mb:.2f} MB)")
            deleted_count += 1
            reclaimed_bytes += asize
        else:
            del_res = run_command(["gh", "api", "--method", "DELETE", f"repos/{repo}/actions/artifacts/{aid}"])
            if del_res.returncode == 0:
                print(f"   \033[32m✔ Deleted:\033[0m #{aid} '{aname}' ({size_mb:.2f} MB)")
                deleted_count += 1
                reclaimed_bytes += asize
            else:
                print(f"   \033[31m✖ Failed to delete:\033[0m #{aid} - {del_res.stderr.strip()}")

    print(f"   \033[1mArtifacts deleted: {deleted_count} ({reclaimed_bytes / (1024*1024):.2f} MB freed)\033[0m")
    return deleted_count


def clean_caches(repo: str, keep: int, dry_run: bool) -> int:
    print(f"\n⚡ \033[1;33m[Actions Caches]\033[0m Checking caches in \033[1m{repo}\033[0m...")
    caches = fetch_paged(f"repos/{repo}/actions/caches", "actions_caches")
    if not caches:
        print("   No caches found.")
        return 0

    caches.sort(key=lambda x: x.get("created_at", ""), reverse=True)
    kept = caches[:keep]
    to_delete = caches[keep:]

    print(f"   Total cache: {len(caches)} | Keep: {len(kept)} | Delete: {len(to_delete)}")

    deleted_count = 0
    reclaimed_bytes = 0
    for c in to_delete:
        cid = c["id"]
        ckey = c.get("key", "unnamed")
        csize = c.get("size_in_bytes", 0)
        size_mb = csize / (1024 * 1024)
        if dry_run:
            print(f"   [DRY-RUN] Would delete cache: #{cid} '{ckey[:50]}...' ({size_mb:.2f} MB)")
            deleted_count += 1
            reclaimed_bytes += csize
        else:
            del_res = run_command(["gh", "api", "--method", "DELETE", f"repos/{repo}/actions/caches/{cid}"])
            if del_res.returncode == 0:
                print(f"   \033[32m✔ Deleted cache:\033[0m #{cid} ({size_mb:.2f} MB)")
                deleted_count += 1
                reclaimed_bytes += csize
            else:
                print(f"   \033[31m✖ Failed to delete cache:\033[0m #{cid} - {del_res.stderr.strip()}")

    print(f"   \033[1mCaches deleted: {deleted_count} ({reclaimed_bytes / (1024*1024):.2f} MB freed)\033[0m")
    return deleted_count


def clean_runs(repo: str, keep: int, dry_run: bool) -> int:
    print(f"\n🚀 \033[1;35m[Workflow Runs]\033[0m Checking workflow runs in \033[1m{repo}\033[0m...")
    runs = fetch_paged(f"repos/{repo}/actions/runs", "workflow_runs")
    if not runs:
        print("   No workflow runs found.")
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
            print(f"   [DRY-RUN] Would delete run: #{rid} '{title[:40]}'")
            deleted_count += 1
        else:
            del_res = run_command(["gh", "api", "--method", "DELETE", f"repos/{repo}/actions/runs/{rid}"])
            if del_res.returncode == 0:
                print(f"   \033[32m✔ Deleted run:\033[0m #{rid}")
                deleted_count += 1
            else:
                print(f"   \033[31m✖ Failed to delete run:\033[0m #{rid} - {del_res.stderr.strip()}")

    print(f"   \033[1mRuns deleted: {deleted_count}\033[0m")
    return deleted_count


def get_org_repos(org: str) -> List[str]:
    repos = []
    res = run_command(["gh", "api", f"orgs/{org}/repos?per_page=100", "--jq", ".[].full_name"])
    if res.returncode == 0 and res.stdout.strip():
        repos = [line.strip() for line in res.stdout.strip().splitlines() if line.strip()]
    return repos


def main():
    parser = argparse.ArgumentParser(
        description="Clean up old GitHub Actions artifacts, caches, or workflow runs, keeping the newest 2 or 3."
    )
    parser.add_argument(
        "--repo",
        type=str,
        default=None,
        help="Target repository as OWNER/REPO (default: the current git repository)",
    )
    parser.add_argument(
        "--org",
        type=str,
        default=None,
        help="Target every repository in an organization (for example waste-not-app)",
    )
    parser.add_argument(
        "--keep",
        type=int,
        default=3,
        help="How many of the newest versions or items to keep (default: 3)",
    )
    parser.add_argument(
        "--caches",
        action="store_true",
        help="Also clean up GitHub Actions caches",
    )
    parser.add_argument(
        "--runs",
        action="store_true",
        help="Also clean up old workflow runs",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Clean up artifacts, caches, and runs together",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would be deleted without deleting anything",
    )

    args = parser.parse_args()

    targets = []
    if args.org:
        print(f"🔍 Looking for repositories in organization \033[1m{args.org}\033[0m...")
        targets = get_org_repos(args.org)
        if not targets:
            print(f"No repositories found in organization {args.org}.")
            sys.exit(1)
        print(f"Found {len(targets)} repositories: {', '.join(targets)}")
    elif args.repo:
        targets = [args.repo]
    else:
        detected = get_current_repo()
        if not detected:
            print("Error: not inside a git repository, and neither --repo nor --org was given.")
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
    print(f"Mode         : {'\033[33mDRY-RUN (simulation)\033[0m' if args.dry_run else '\033[31mEXECUTION (deleting for real)\033[0m'}")
    print("=" * 60)

    for repo in targets:
        if clean_art:
            clean_artifacts(repo, args.keep, args.dry_run)
        if clean_c:
            clean_caches(repo, args.keep, args.dry_run)
        if clean_r:
            clean_runs(repo, args.keep, args.dry_run)

    print("\n\033[1;32m✔ Done!\033[0m\n")


if __name__ == "__main__":
    main()
