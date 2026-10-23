#!/usr/bin/env python3
"""Build and deploy the blog to the production server.

The SSH password is read from .tmp/server-credentials.txt, which is
gitignored. Server-side configuration (.env), database and uploaded files are
never overwritten by this script.
"""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.request
from pathlib import Path

try:
    import paramiko
except ImportError:
    print("paramiko is required: python -m pip install paramiko")
    raise SystemExit(1)

ROOT = Path(__file__).resolve().parents[1]
CREDENTIALS = Path(os.environ.get("SERVER_CREDENTIALS", ROOT / ".tmp" / "server-credentials.txt"))
REMOTE = "/opt/sunny-blog"
DIST_ARCHIVE = ROOT / ".tmp" / "deploy-dist.tar.gz"
JAR = ROOT / "backend" / "target" / "sunny-blog-1.0.0.jar"
DIST = ROOT / "frontend" / "dist"
RUNNER = ROOT / "scripts" / "agent-sandbox-runner.sh"
BENCH = ROOT / "benchmarks" / "agent"
BENCH_ARCHIVE = ROOT / ".tmp" / "benchmarks-agent.tar.gz"


def read_credentials() -> dict[str, str]:
    if not CREDENTIALS.exists():
        raise SystemExit(f"Missing {CREDENTIALS}; see deployment notes.")
    values: dict[str, str] = {}
    for line in CREDENTIALS.read_text(encoding="utf-8").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip("\"'")
    for key in ("host", "username", "password"):
        if not values.get(key):
            raise SystemExit(f"Missing credential field: {key}")
    return values


def run_local(args: list[str], cwd: Path) -> None:
    print("+", " ".join(args))
    env = os.environ.copy()
    env["TEMP"] = str(ROOT / ".tmp")
    env["TMP"] = str(ROOT / ".tmp")
    subprocess.run(args, cwd=cwd, check=True, env=env)


def build() -> None:
    (ROOT / ".tmp").mkdir(parents=True, exist_ok=True)
    mvn = shutil.which("mvn.cmd") or shutil.which("mvn")
    npm = shutil.which("npm.cmd") or shutil.which("npm")
    if not mvn or not npm:
        raise SystemExit("mvn/npm not found in PATH")
    run_local([mvn, "-B", "-DskipTests", "package"], ROOT / "backend")
    run_local([npm, "run", "build"], ROOT / "frontend")
    if not JAR.exists() or not (DIST / "index.html").exists():
        raise SystemExit("Build output is missing")
    DIST_ARCHIVE.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(DIST_ARCHIVE, "w:gz") as tar:
        for path in sorted(DIST.rglob("*")):
            if path.is_file():
                tar.add(path, arcname=str(path.relative_to(DIST)))
    with tarfile.open(BENCH_ARCHIVE, "w:gz") as tar:
        for path in sorted(BENCH.rglob("*")):
            if path.is_file() and "reports" not in path.parts:
                tar.add(path, arcname=str(path.relative_to(BENCH.parent)))
    print(f"dist archive: {DIST_ARCHIVE.stat().st_size / 1024 / 1024:.1f} MB")


def connect(values: dict[str, str]):
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(
        hostname=values["host"],
        port=int(values.get("port", "22")),
        username=values["username"],
        password=values["password"],
        timeout=30,
        banner_timeout=30,
        auth_timeout=30,
    )
    return client


def upload(client, local: Path, remote: str) -> None:
    sftp = client.open_sftp()
    try:
        sftp.put(str(local), remote)
    finally:
        sftp.close()


def deploy(client) -> None:
    client.exec_command(f"mkdir -p {REMOTE}/incoming", timeout=30)[1].read()
    upload(client, JAR, f"{REMOTE}/incoming/sunny-blog.jar")
    upload(client, DIST_ARCHIVE, f"{REMOTE}/incoming/deploy-dist.tar.gz")
    upload(client, BENCH_ARCHIVE, f"{REMOTE}/incoming/benchmarks-agent.tar.gz")
    upload(client, RUNNER, f"{REMOTE}/incoming/sunny-agent-run")

    script = f"""
set -e
cd {REMOTE}
systemctl stop sunny-blog
cp -f app/sunny-blog.jar app/sunny-blog.jar.prev
rm -rf frontend.prev
mv frontend frontend.prev
mkdir -p frontend
tar -xzf incoming/deploy-dist.tar.gz -C frontend
rm -rf {REMOTE}/benchmarks
mkdir -p {REMOTE}/benchmarks
tar -xzf incoming/benchmarks-agent.tar.gz -C {REMOTE}/benchmarks
mv -f incoming/sunny-blog.jar app/sunny-blog.jar
chown -R sunnyblog:sunnyblog {REMOTE}
# Provision the dedicated unprivileged script runner used by systemd-run.
getent group agentrun >/dev/null 2>&1 || groupadd -r agentrun
id sunnyagent >/dev/null 2>&1 || useradd -r -g agentrun -M -s /usr/sbin/nologin sunnyagent
usermod -aG agentrun sunnyblog
install -m 0755 incoming/sunny-agent-run /usr/local/bin/sunny-agent-run
cat > /etc/sudoers.d/sunny-agent <<'SUDOERS'
sunnyblog ALL=(root) NOPASSWD: /usr/local/bin/sunny-agent-run *
SUDOERS
chmod 0440 /etc/sudoers.d/sunny-agent
visudo -cf /etc/sudoers.d/sunny-agent >/dev/null
if grep -q '^NoNewPrivileges=true$' /etc/systemd/system/sunny-blog.service; then
  sed -i 's/^NoNewPrivileges=true$/NoNewPrivileges=false/' /etc/systemd/system/sunny-blog.service
  systemctl daemon-reload
fi
chmod 0600 {REMOTE}/.env
# The deploy chown resets the agent sandbox group; restore it so the
# unprivileged sunnyagent runner can read skills and write artifacts.
chgrp -R agentrun {REMOTE}/data/agent/workspace {REMOTE}/data/agent/skills {REMOTE}/data/agent/artifacts 2>/dev/null || true
chmod -R g+rwX {REMOTE}/data/agent/workspace {REMOTE}/data/agent/skills {REMOTE}/data/agent/artifacts 2>/dev/null || true
find {REMOTE}/data/agent/workspace {REMOTE}/data/agent/skills {REMOTE}/data/agent/artifacts -type d -exec chmod g+s {{}} + 2>/dev/null || true
chmod 0700 {REMOTE}/data/agent/sessions {REMOTE}/data/agent/runs 2>/dev/null || true
systemctl start sunny-blog
ok=0
for i in $(seq 1 30); do
  if systemctl is-active --quiet sunny-blog && curl -fsS http://127.0.0.1:8090/api/site >/dev/null 2>&1; then ok=1; break; fi
  sleep 1
done
if [ "$ok" != "1" ]; then
  echo "health check failed; rolling back" >&2
  mv -f app/sunny-blog.jar.prev app/sunny-blog.jar
  rm -rf frontend
  mv frontend.prev frontend
  systemctl restart sunny-blog || true
  exit 1
fi
rm -rf incoming
echo "deploy ok"
"""
    stdin, stdout, stderr = client.exec_command(script, timeout=180, get_pty=True)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    if out.strip():
        print(out.strip())
    if err.strip():
        print(err.strip())
    status = stdout.channel.recv_exit_status()
    if status != 0:
        raise SystemExit(f"Remote deploy failed with exit code {status}")


def verify(values: dict[str, str]) -> None:
    domain = values.get("domain", "").strip()
    if not domain.startswith("http"):
        domain = "https://www.bluish-white.top"
    domain = domain.rstrip("/")
    try:
        with urllib.request.urlopen(domain, timeout=20) as response:
            print(f"health: {domain} -> {response.status}")
    except Exception as exc:
        raise SystemExit(f"Public health check failed: {exc}")


def main() -> None:
    values = read_credentials()
    build()
    client = connect(values)
    try:
        deploy(client)
    finally:
        client.close()
    verify(values)


if __name__ == "__main__":
    main()
