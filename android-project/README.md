# wiliwili Android

This project wraps the wiliwili CMake build in an Android Gradle project. It uses SDL2 from the borealis submodule, embeds resources with libromfs, imports mpv-android shared libraries, and links OpenSSL statically for HTTPS.

## Build

From the repository root:

```sh
bash scripts/android/patch_sdl_ndk27.sh
bash scripts/android/prepare_deps.sh
cd library/borealis
bash build_libromfs_generator.sh
cp -f libromfs-generator ../../android-project/app/jni/borealis/libromfs-generator
cd ../../android-project
./gradlew --no-daemon :app:assembleDebug
```

By default Gradle builds split APKs for:

- `armeabi-v7a`
- `arm64-v8a`
- `x86`
- `x86_64`

To build one ABI:

```sh
./gradlew --no-daemon :app:assembleDebug -PANDROID_ABIS=arm64-v8a
```

The APKs are written to `app/build/outputs/apk/debug/`.

## Dependencies

`scripts/android/prepare_deps.sh` downloads and extracts:

- mpv-android release APKs from `mpv-android/mpv-android`, tag `2026-04-25`
- libmpv headers from `mpv-player/mpv`, tag `v0.41.0`
- OpenSSL static libraries from `PurpleI2P/OpenSSL-for-Android-Prebuilt`

The generated dependency tree lives under `third_party/android/` and is intentionally not committed.
