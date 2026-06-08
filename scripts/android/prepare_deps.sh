#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPS_DIR="${ROOT_DIR}/third_party/android"
DOWNLOAD_DIR="${DEPS_DIR}/download"
MPV_DIR="${DEPS_DIR}/mpv-android"
OPENSSL_DIR="${DEPS_DIR}/openssl"

MPV_ANDROID_TAG="${MPV_ANDROID_TAG:-2026-04-25}"
MPV_ANDROID_BASE_URL="https://github.com/mpv-android/mpv-android/releases/download/${MPV_ANDROID_TAG}"
OPENSSL_PREBUILT_REF="${OPENSSL_PREBUILT_REF:-master}"
OPENSSL_PREBUILT_URL="https://github.com/PurpleI2P/OpenSSL-for-Android-Prebuilt/archive/${OPENSSL_PREBUILT_REF}.tar.gz"
OPENSSL_FLAVOR="${OPENSSL_FLAVOR:-openssl-1.1.1k-clang}"

ABIS=(armeabi-v7a arm64-v8a x86 x86_64)

download() {
    local url="$1"
    local output="$2"

    if [[ -s "${output}" ]]; then
        return
    fi

    mkdir -p "$(dirname "${output}")"
    curl --fail --location --retry 3 --retry-delay 2 --output "${output}" "${url}"
}

extract_mpv_android() {
    mkdir -p "${DOWNLOAD_DIR}" "${MPV_DIR}/include/mpv"

    for abi in "${ABIS[@]}"; do
        local apk="${DOWNLOAD_DIR}/app-default-${abi}-release.apk"
        download "${MPV_ANDROID_BASE_URL}/app-default-${abi}-release.apk" "${apk}"

        mkdir -p "${MPV_DIR}/${abi}"
        unzip -q -o "${apk}" "lib/${abi}/*.so" -d "${DOWNLOAD_DIR}/mpv-${abi}"
        cp "${DOWNLOAD_DIR}/mpv-${abi}/lib/${abi}/"*.so "${MPV_DIR}/${abi}/"
    done

    local mpv_header_ref="${MPV_HEADER_REF:-v0.41.0}"
    for header in client.h render.h render_gl.h stream_cb.h; do
        download "https://raw.githubusercontent.com/mpv-player/mpv/${mpv_header_ref}/libmpv/${header}" \
            "${MPV_DIR}/include/mpv/${header}"
    done
}

extract_openssl_android() {
    local archive="${DOWNLOAD_DIR}/openssl-for-android-prebuilt-${OPENSSL_PREBUILT_REF}.tar.gz"
    local source_root="${DOWNLOAD_DIR}/OpenSSL-for-Android-Prebuilt-${OPENSSL_PREBUILT_REF}"

    download "${OPENSSL_PREBUILT_URL}" "${archive}"

    if [[ ! -d "${source_root}" ]]; then
        tar -xzf "${archive}" -C "${DOWNLOAD_DIR}"
    fi

    local flavor_root="${source_root}/${OPENSSL_FLAVOR}"
    if [[ ! -d "${flavor_root}" ]]; then
        echo "Missing OpenSSL flavor: ${flavor_root}" >&2
        exit 1
    fi

    mkdir -p "${OPENSSL_DIR}/include"
    cp -R "${flavor_root}/include/." "${OPENSSL_DIR}/include/"

    for abi in "${ABIS[@]}"; do
        local lib_root="${flavor_root}/${abi}/lib"
        if [[ ! -f "${lib_root}/libssl.a" || ! -f "${lib_root}/libcrypto.a" ]]; then
            echo "Missing OpenSSL static libraries for ${abi} under ${lib_root}" >&2
            exit 1
        fi

        mkdir -p "${OPENSSL_DIR}/${abi}/lib"
        cp "${lib_root}/libssl.a" "${OPENSSL_DIR}/${abi}/lib/"
        cp "${lib_root}/libcrypto.a" "${OPENSSL_DIR}/${abi}/lib/"
    done
}

extract_mpv_android
extract_openssl_android

echo "Android dependencies are ready under ${DEPS_DIR}"
