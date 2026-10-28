#!/usr/bin/env bash

set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
server_bin="$repo_dir/server/server"
server_pid=""

cleanup() {
    if [[ -n "$server_pid" ]]; then
        kill "$server_pid" 2>/dev/null || true
        wait "$server_pid" 2>/dev/null || true
    fi
}
trap cleanup EXIT INT TERM

cd "$repo_dir"

(cd server && go build -o "$server_bin" .)

"$server_bin" >/dev/null 2>&1 &
server_pid=$!

ports=(8080 8081 8443 8444 8445 8446 1080 1081 1082 1083 9000 9443)
for _ in {1..100}; do
    ready=true
    for port in "${ports[@]}"; do
        if ! (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then
            ready=false
            break
        fi
    done

    if [[ "$ready" == true ]]; then
        break
    fi
    if ! kill -0 "$server_pid" 2>/dev/null; then
        echo "Server exited before it became ready." >&2
        exit 1
    fi
    sleep 0.1
done

if [[ "$ready" != true ]]; then
    echo "Server startup timed out." >&2
    exit 1
fi

test_status=0
for target in rootJVM rootJS rootNative; do
    if ! sbt "$target/test"; then
        test_status=1
    fi
done

exit "$test_status"

