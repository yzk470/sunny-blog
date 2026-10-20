#!/bin/bash
set -euo pipefail

cwd=""
timeout="20"
workspace="/opt/sunny-blog/data/agent/workspace"
skills="/opt/sunny-blog/data/agent/skills"
artifacts="/opt/sunny-blog/data/agent/artifacts"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --cwd) cwd="${2:-}"; shift 2 ;;
    --timeout) timeout="${2:-20}"; shift 2 ;;
    --workspace) workspace="${2:-}"; shift 2 ;;
    --skills) skills="${2:-}"; shift 2 ;;
    --artifacts) artifacts="${2:-}"; shift 2 ;;
    --) shift; break ;;
    *) echo "invalid runner argument: $1" >&2; exit 64 ;;
  esac
done

case "$cwd" in
  /opt/sunny-blog/data/agent/workspace|/opt/sunny-blog/data/agent/workspace/*|\
  /opt/sunny-blog/data/agent/skills|/opt/sunny-blog/data/agent/skills/*) ;;
  *) echo "invalid working directory" >&2; exit 64 ;;
esac

for dir in "$workspace" "$skills" "$artifacts"; do
  case "$dir" in
    /opt/sunny-blog/data/agent/workspace|\
    /opt/sunny-blog/data/agent/skills|\
    /opt/sunny-blog/data/agent/artifacts) ;;
    *) echo "invalid agent directory" >&2; exit 64 ;;
  esac
done

if [ "$#" -lt 1 ]; then
  echo "missing command" >&2
  exit 64
fi

case "$1" in
  /usr/bin/python3|/usr/bin/node|/usr/bin/ls|/usr/bin/cat|/usr/bin/pwd|\
  /usr/bin/find|/usr/bin/head|/usr/bin/tail|/usr/bin/wc) ;;
  *) echo "command not allowed" >&2; exit 64 ;;
esac

unit="sunny-agent-$(head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n')"
exec /usr/bin/systemd-run \
  --quiet --wait --pipe --collect --unit="$unit" \
  --property=User=sunnyagent \
  --property=Group=agentrun \
  --property=ProtectSystem=strict \
  --property=ProtectHome=yes \
  --property=PrivateTmp=yes \
  --property=PrivateDevices=yes \
  --property=NoNewPrivileges=yes \
  --property=ProtectKernelTunables=yes \
  --property=ProtectKernelModules=yes \
  --property=ProtectControlGroups=yes \
  --property=RestrictSUIDSGID=yes \
  --property=LockPersonality=yes \
  --property=RestrictNamespaces=yes \
  --property=MemoryMax=512M \
  --property=TasksMax=64 \
  --property=UMask=0002 \
  --property=RuntimeMaxSec="${timeout}s" \
  --property=PrivateNetwork=yes \
  --property=ReadWritePaths="$workspace" \
  --property=ReadWritePaths="$skills" \
  --property=ReadWritePaths="$artifacts" \
  --property=InaccessiblePaths="/opt/sunny-blog/.env /opt/sunny-blog/app /opt/sunny-blog/frontend /opt/sunny-blog/data/agent/sessions /opt/sunny-blog/data/agent/runs" \
  --setenv=AGENT_WORKSPACE_DIR="$workspace" \
  --setenv=AGENT_SKILLS_DIR="$skills" \
  --setenv=AGENT_ARTIFACTS_DIR="$artifacts" \
  --setenv=HOME=/tmp \
  --working-directory="$cwd" \
  -- "$@"
