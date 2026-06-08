# wiliwili Android

这个目录是 wiliwili 的 Android Gradle 工程。它通过 CMake 编译 wiliwili，使用 borealis 子模块中的 SDL2，通过 libromfs 内嵌资源，导入 mpv-android 的动态库，并静态链接 OpenSSL 用于 HTTPS。

## 构建

在仓库根目录执行：

```sh
bash scripts/android/patch_sdl_ndk27.sh
bash scripts/android/prepare_deps.sh
cd library/borealis
bash build_libromfs_generator.sh
cp -f libromfs-generator ../../android-project/app/jni/borealis/libromfs-generator
cd ../../android-project
./gradlew --no-daemon :app:assembleDebug
```

默认会构建以下架构的 split APK：

- `armeabi-v7a`
- `arm64-v8a`
- `x86`
- `x86_64`

只构建单个 ABI：

```sh
./gradlew --no-daemon :app:assembleDebug -PANDROID_ABIS=arm64-v8a
```

APK 输出目录为 `app/build/outputs/apk/debug/`。

## 依赖

`scripts/android/prepare_deps.sh` 会下载并解压：

- `mpv-android/mpv-android` 的 `2026-04-25` release APK
- `mpv-player/mpv` 的 `v0.41.0` libmpv 头文件
- `PurpleI2P/OpenSSL-for-Android-Prebuilt` 的 OpenSSL 静态库

生成的依赖目录在 `third_party/android/`，不会提交到仓库。
