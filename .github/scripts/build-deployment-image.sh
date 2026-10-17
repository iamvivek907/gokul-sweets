#!/usr/bin/env bash
set -euo pipefail

# Both registries distribute the same Temurin images used by the deployment Dockerfile.
# A registry quota must not hide Docker's exit code behind tee or skip verification.
registries=(public.ecr.aws/docker/library docker.io/library public.ecr.aws/docker/library)
build_log=$(mktemp)
trap 'rm -f "$build_log"' EXIT
for index in "${!registries[@]}"; do
  registry=${registries[$index]}
  if docker build --pull --no-cache --progress=plain \
      --build-arg "JAVA_BUILD_IMAGE=$registry/eclipse-temurin:21-jdk" \
      --build-arg "JAVA_RUNTIME_IMAGE=$registry/eclipse-temurin:21-jre" \
      --tag gokul-render-ci backend 2>&1 | tee "$build_log"; then
    exit 0
  else
    status=$?
  fi
  # Only a base-image registry rate limit permits a retry/fallback. Preserve other failures.
  if [ "$index" -eq 2 ] || ! grep -Eq 'failed to resolve source metadata.*(429 Too Many Requests|toomanyrequests)' "$build_log"; then
    exit "$status"
  fi
  echo "Base-image registry rate limited the build; trying ${registries[$((index + 1))]} (attempt $((index + 2))/3)."
  sleep "$(((index + 1) * 15))"
done
