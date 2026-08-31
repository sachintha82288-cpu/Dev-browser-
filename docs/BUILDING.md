# Building

## Prerequisites
Toolchain laid out by:

```bash
bash tools/setup-toolchain.sh     # offline bootstrap (see docs/TOOLCHAIN.md)
```

## Build
```bash
make apk          # or: bash tools/build.sh
make info         # aapt2 dump badging + apksigner --print-certs
make clean
```

Output: `app/build/apk/DevBrowser-1.0.0.apk` (debug-signed with
`tools/keystore/devbrowser.keystore`, generated on first build; override the
password with `KEYSTORE_PASS`).

## The pipeline (what Gradle would do)
1. `aapt2 compile --dir res` → compiled resource table.
2. `aapt2 link -I android.jar --manifest … --java gen` → `R.java` + unsigned APK.
3. `javac -source 8 -target 8 -cp android.jar` → classes.
   - Important: use `-cp`, **not** `-bootclasspath` — android.jar has no
     `LambdaMetafactory`, so lambdas fail to compile with a bootclasspath; with
     `-cp` they compile and D8 desugars them.
4. D8 `--min-api 24 --lib android.jar` → `classes.dex`.
5. `tools/finalize_apk.py` inserts `classes.dex` and 4-byte-aligns stored
   entries (zipalign equivalent).
6. `apksigner sign` (v2+v3) with a generated keystore; `verify` in the script.

## Installing
```bash
adb install -r app/build/apk/DevBrowser-1.0.0.apk
# or copy the APK to the device and tap it
```

## Adding a real release signature
```bash
keytool -genkeypair -keystore release.keystore -alias devbrowser ...
VERSION=1.0.0 bash tools/build.sh   # then sign with your release key
```
