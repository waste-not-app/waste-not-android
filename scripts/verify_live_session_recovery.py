"""Drive RemoteSessionRecoveryTest using only the session of its new probe account."""

import argparse
import json
from pathlib import Path
import subprocess
import time
from urllib.request import urlopen
from uuid import UUID, uuid4


def run(*command, stdin=None):
    result = subprocess.run(command, input=stdin, capture_output=True, text=True, timeout=25)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or f"{command[0]} exited {result.returncode}")
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True, help="Dedicated emulator with the debug and test APKs installed")
    parser.add_argument("--ssh", default="cp-bcc")
    parser.add_argument("--ssh-port", type=int)
    parser.add_argument("--identity", type=Path)
    parser.add_argument("--known-hosts", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)

    adb = [args.adb, "-s", args.serial]
    ssh = ["ssh", "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes", "-o", "ConnectTimeout=10"]
    if args.ssh_port:
        ssh += ["-p", str(args.ssh_port)]
    if args.identity:
        ssh += ["-o", "IdentitiesOnly=yes", "-i", str(args.identity)]
    if args.known_hosts:
        ssh += ["-o", f'UserKnownHostsFile="{args.known_hosts}"']
    ssh += ["--", args.ssh, "docker exec -i wastenot-db psql -XAtq -U wastenot -d wastenot -v ON_ERROR_STOP=1"]

    run_id = str(uuid4())
    email = f"android-session-{run_id}@example.test"
    probe = f"normalized_email = '{email}' AND name = 'Android #112 session probe'"
    package = "com.example.wastenot"
    files = f"/sdcard/Android/data/{package}/files/session-recovery/{run_id}"
    evidence = {"run_id": run_id}

    def sql(query):
        return run(*ssh, stdin=query + "\n")

    def owner():
        match = sql(f"SELECT id FROM users WHERE {probe};")
        return str(UUID(match)) if match else None

    def phase(expected, timeout=75):
        deadline = time.monotonic() + timeout
        while process.poll() is None and time.monotonic() < deadline:
            try:
                state = json.loads(run(*adb, "shell", "cat", f"{files}/phase.json"))
            except (RuntimeError, ValueError):
                time.sleep(1)
                continue
            if state.get("phase") == expected and state.get("run_id") == run_id:
                user_id = str(UUID(state["user_id"]))
                if owner() != user_id:
                    raise RuntimeError("Probe owner mismatch")
                return user_id
            time.sleep(1)
        raise RuntimeError(f"Probe did not reach {expected}. See native.log.")

    def session(user_id, session_id=None):
        exact_session = f"AND s.id = '{session_id}'" if session_id else ""
        row = sql(f"""SELECT s.id, s.generation, s.revoked_at IS NOT NULL,
            (SELECT count(*) FROM refresh_tokens WHERE session_id = s.id),
            (SELECT count(*) FROM refresh_tokens WHERE session_id = s.id AND used_at IS NOT NULL)
            FROM sessions s JOIN users u ON u.id = s.user_id
            WHERE u.id = '{user_id}' AND {probe} {exact_session};""")
        sid, generation, revoked, total, used = row.split("|")
        return {"id": str(UUID(sid)), "generation": int(generation), "revoked": revoked == "t",
                "refresh_tokens": int(total), "used_tokens": int(used)}

    if sql(f"SELECT count(*) FROM users WHERE normalized_email = '{email}';") != "0":
        raise RuntimeError("Probe email already exists")
    with urlopen("https://waste-not-api.bccdev.id/health/ready", timeout=15) as ready:
        if not json.load(ready).get("ready"):
            raise RuntimeError("Backend readiness check failed")
        evidence["backend_release"] = ready.headers.get("X-WasteNot-Release")

    process = None
    try:
        with (args.output / "native.log").open("wb") as log:
            process = subprocess.Popen([*adb, "shell", "am", "instrument", "-w", "-e", "verifyLiveSessionRecovery", "true",
                "-e", "fixtureRunId", run_id, "-e", "class",
                "com.example.wastenot.ui.main.RemoteSessionRecoveryTest#refreshAndRevocationAreReflectedAtTheRealAccountRoot",
                f"{package}.test/{package}.WasteNotTestRunner"], stdout=log, stderr=subprocess.STDOUT)

            user_id = phase("awaiting_stale_access", timeout=180)
            original = session(user_id)
            if original["revoked"] or original["refresh_tokens"] != 1 or original["used_tokens"] != 0:
                raise RuntimeError("Unexpected initial probe session")
            session_id = original["id"]
            where = f"s.id = '{session_id}' AND s.user_id = u.id AND u.id = '{user_id}' AND {probe} AND s.revoked_at IS NULL"
            changed = sql(f"""UPDATE sessions s SET generation = s.generation + 1 FROM users u
                WHERE {where} AND s.generation = {original['generation']} RETURNING s.generation;""")
            if changed != str(original["generation"] + 1):
                raise RuntimeError("Expected to invalidate exactly one probe access token")
            run(*adb, "shell", "touch", f"{files}/stale-access")

            phase("awaiting_revocation")
            refreshed = session(user_id, session_id)
            if (refreshed["generation"], refreshed["refresh_tokens"], refreshed["used_tokens"]) != (original["generation"] + 2, 2, 1):
                raise RuntimeError("Expected one refresh rotation after the stale access token")
            changed = sql(f"""UPDATE sessions s SET revoked_at = now() FROM users u
                WHERE {where} AND s.generation = {refreshed['generation']} RETURNING s.id;""")
            if changed != session_id:
                raise RuntimeError("Expected to revoke exactly one probe session")
            run(*adb, "shell", "touch", f"{files}/revoked")
            if process.wait(timeout=75) or "OK (1 test)" not in (args.output / "native.log").read_text(encoding="utf-8"):
                raise RuntimeError("Instrumentation failed. See native.log.")
            final = session(user_id, session_id)
            if final != {**refreshed, "revoked": True}:
                raise RuntimeError("Revoked session changed generation or tokens")
            evidence.update(original=original, refreshed=refreshed, revoked=final)
            for name in ("version-conflict.png", "root-after-refresh.png", "root-after-revocation.png"):
                run(*adb, "pull", f"{files}/{name}", str(args.output / name))
            evidence["passed"] = True
    finally:
        try:
            if process is not None and process.poll() is None:
                try:
                    run(*adb, "shell", "am", "force-stop", package)
                finally:
                    process.terminate()
                    process.wait(timeout=10)
        finally:
            try:
                user_id = owner()
                if user_id:
                    session_ids = [str(UUID(sid)) for sid in sql(f"SELECT id FROM sessions WHERE user_id = '{user_id}';").splitlines()]
                    deleted = sql(f"DELETE FROM users WHERE id = '{user_id}' AND {probe} RETURNING id;")
                    if deleted != user_id:
                        raise RuntimeError("Expected to delete only the probe user")
                    related = ",".join(f"'{sid}'::uuid" for sid in session_ids)
                    counts = {table: int(sql(f"SELECT count(*) FROM {table} WHERE user_id = '{user_id}';"))
                              for table in ("sessions", "waste_logs", "ai_operations", "ai_log_metadata")}
                    counts["refresh_tokens"] = int(sql(f"SELECT count(*) FROM refresh_tokens WHERE session_id IN ({related});")) if related else 0
                    counts["users"] = int(sql(f"SELECT count(*) FROM users WHERE id = '{user_id}';"))
                    evidence["cleanup_counts"] = counts
                    if any(counts.values()):
                        raise RuntimeError("Probe cleanup left related rows")
                evidence["cleanup_complete"] = owner() is None
                if not evidence["cleanup_complete"]:
                    raise RuntimeError("Probe account still exists after cleanup")
            finally:
                (args.output / "result.json").write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
