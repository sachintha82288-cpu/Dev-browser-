# Toolchain

DevBrowser builds **without Gradle and without the official Android SDK**, using
only four binaries plus a JDK. This matters because development sandboxes (and
many build hosts) have no access to `dl.google.com`, `services.gradle.org` or apt
mirrors.

| Component          | Binary                                                     | Source (offline)                                  |
|--------------------|------------------------------------------------------------|---------------------------------------------------|
| JDK 17 (javac, jar, jarsigner, keytool) | `Tools/jdk-17`                       | npm `javajre-linux-64` (Amazon Corretto 17.0.8)   |
| aapt2 2.20         | `Tools/aapt2/package/bin/x64/linux/aapt2`                  | npm `aaptjs3`                                     |
| android.jar (API 34) | `Tools/platforms/android-34/android.jar`                 | git `Reginer/aosp-android-jar` (sparse checkout)  |
| d8/r8 3.3.20       | `Tools/build-tools/lib/d8.jar`                             | git blob, `cyber-Ray/CCN` `build-tools/33.0.2/lib/d8.jar` |
| apksigner 33.0.2   | `Tools/build-tools/lib/apksigner_33_0_2.jar`               | git blob, `patrickfav/uber-apk-signer` `src/main/resources/lib/` |
| media tools        | `Tools/venv` (bpy, ffmpeg, Pillow, matplotlib, trimesh, PySide6) | PyPI (bpy wheel = headless Blender)          |

`tools/setup-toolchain.sh` automates all of the above.

### Why no Gradle?
`gradle-*-bin.zip` and every Maven repo (maven.google.com, repo.maven.apache.org,
plugins.gradle.org) are unreachable in restricted networks, and GitHub *release
assets* (where most binaries live) are blocked too. The only reliable channels
are: git protocol / codeload archives, the GitHub contents+blob API, npm and PyPI.
Gradle is therefore not a hard dependency for this project — the build pipeline
invokes the same tools Gradle would (aapt2 → javac → D8 → zip → apksigner).
If you have normal internet, copy the project into Android Studio or run
`gradle wrapper` and everything works unchanged; the module layout is
Gradle-compatible (see `docs/ARCHITECTURE.md`).

### Note on the sandbox environment
- Blender (`bpy`) is installed, but import needs `libGL.so.1`/`libXi` etc.
  (x86_64 Mesa), which this container lacks. On a desktop Linux with Mesa it
  works. In-sandbox 3D/2D generation uses the CPU renderers: matplotlib/trimesh
  (3D) and PySide6 offscreen (2D) + ffmpeg (encoding) — see
  `tools/demo_3d.py` and `tools/demo_2d.py`.
- `Qt` on this box requires the tiny stub `.so`s in `Tools/stubs`; those are
  only needed because the container has no GL/DBus. `tools/env.sh` sets
  `LD_LIBRARY_PATH` when rendering.
