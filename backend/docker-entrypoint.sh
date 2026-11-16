#!/bin/sh
set -eu
memory_limit=0
for limit_file in /sys/fs/cgroup/memory.max /sys/fs/cgroup/memory/memory.limit_in_bytes; do
    if [ -r "$limit_file" ]; then
        candidate=$(cat "$limit_file")
        case "$candidate" in ''|*[!0-9]*) continue ;; esac
        memory_limit=$candidate
        break
    fi
done

# Bound the small container's native allocator and accepted sockets during bursts.
if [ "$memory_limit" -gt 0 ] && [ "$memory_limit" -le 536870912 ]; then
    : "${MALLOC_ARENA_MAX=2}"
    : "${GOKUL_HTTP_MAX_CONNECTIONS=128}"
    : "${GOKUL_HTTP_ACCEPT_COUNT=2048}"
    export MALLOC_ARENA_MAX GOKUL_HTTP_MAX_CONNECTIONS GOKUL_HTTP_ACCEPT_COUNT
fi

# Operator settings take precedence over automatic heap sizing.
if [ "${JAVA_TOOL_OPTIONS+x}" != x ]; then
    # Unknown/unlimited memory falls back to the small profile.
    if [ "$memory_limit" -ge 2147483648 ] && [ "$memory_limit" -le 17179869184 ]; then
        JAVA_TOOL_OPTIONS='-Xms128m -Xmx768m -XX:+UseG1GC -XX:ReservedCodeCacheSize=96m -XX:MaxDirectMemorySize=64m -XX:+ExitOnOutOfMemoryError'
    else
        JAVA_TOOL_OPTIONS='-Xms64m -Xmx192m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=64m -XX:MaxDirectMemorySize=32m -XX:+ExitOnOutOfMemoryError'
    fi
    export JAVA_TOOL_OPTIONS
fi
# Opt-in cause/pause timestamps; keeps automatic heap sizing and operator options intact.
if [ "${GOKUL_GC_DIAGNOSTICS_ENABLED:-false}" = true ]; then
    exec java '-Xlog:gc*,safepoint=info:stdout:time,uptime,level,tags' -jar /app/app.jar "$@"
fi
exec java -jar /app/app.jar "$@"
