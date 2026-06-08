#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SDL_SENSOR_FILE="${ROOT_DIR}/library/borealis/library/lib/extern/SDL/src/sensor/android/SDL_androidsensor.c"

if [[ ! -f "${SDL_SENSOR_FILE}" ]]; then
    echo "Missing SDL Android sensor source: ${SDL_SENSOR_FILE}" >&2
    exit 1
fi

if grep -q "ALooper_pollAll" "${SDL_SENSOR_FILE}"; then
    sed -i 's/ALooper_pollAll/ALooper_pollOnce/g' "${SDL_SENSOR_FILE}"
fi
