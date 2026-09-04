# Releasing OpenGuitarTuner

Releases are cut by pushing a `v*` tag. `.github/workflows/release.yml` builds a signed, R8-shrunk APK and
attaches it to a GitHub Release. This page is the one-time setup plus the recurring three-line ritual.

## 1. Generate a signing keystore (once, ever)

The keystore is the app's identity. Android will only install an update over an existing install when both APKs are
signed by the same key, so **if you lose this file, users must uninstall and lose their saved tunings.** Back it up
somewhere that is not this repository.

```sh
export JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"
"$JAVA_HOME/bin/keytool" -genkeypair -v \
  -keystore release.jks \
  -alias openguitartuner \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=ntainy, OU=OpenGuitarTuner, O=OpenGuitarTuner, C=EE"
```

`keytool` prompts for the store password and then the key password; using the same value for both is fine and keeps
the secrets simpler. `-validity 10000` is ~27 years — a key that expires is a key you cannot ship updates with.

`*.jks`, `*.keystore` and `keystore.properties` are git-ignored, but keep `release.jks` outside the repo anyway.

## 2. Base64-encode it for GitHub

Secrets are text, so the keystore travels as base64. `-w 0` (no line wrapping) is GNU; macOS `base64` does not wrap
by default:

```sh
base64 -i release.jks | pbcopy          # macOS: straight to the clipboard
base64 -w 0 release.jks > release.b64   # Linux
```

## 3. Create the four repository secrets

**Settings → Secrets and variables → Actions → New repository secret** on
<https://github.com/ntainy/open-guitar-tuner>:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | the base64 blob from step 2 |
| `ANDROID_KEYSTORE_PASSWORD` | the store password from step 1 |
| `ANDROID_KEY_ALIAS` | `openguitartuner` |
| `ANDROID_KEY_PASSWORD` | the key password from step 1 |

The release job fails loudly rather than publishing an unsigned APK if `ANDROID_KEYSTORE_BASE64` is missing, and it
runs `apksigner verify` on the result before creating the Release.

## 4. Cut a release

```sh
git tag v0.2
git push --tags
```

The tag drives everything: `versionName` is the tag minus its leading `v` (`v0.2` → `0.2`) and `versionCode` is the
workflow's `github.run_number`, so it always climbs. Release notes are generated from the commits since the last
tag. The APK lands on the Release as `OpenGuitarTuner-0.2.apk`.

To undo a release, delete the Release and the tag (`git push --delete origin v0.2`), then tag again.

## Building a signed release locally

Optional — CI is the normal path. Point the same four variables at your keystore, either as environment variables
or as lines in `local.properties` (git-ignored):

```sh
export JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_KEYSTORE_PATH="$HOME/keys/release.jks"
export ANDROID_KEYSTORE_PASSWORD=...
export ANDROID_KEY_ALIAS=openguitartuner
export ANDROID_KEY_PASSWORD=...
./gradlew assembleRelease -PversionName=0.2 -PversionCode=2
```

With **no** keystore configured `assembleRelease` still succeeds and writes an unsigned
`app/build/outputs/apk/release/app-release-unsigned.apk`, which is useful for checking R8 output without holding the
signing key. A configured build writes `app-release.apk` instead — the file name is how you tell them apart.

## R8

The release build runs R8 (`optimization { enable = true }` in `app/build.gradle.kts`), which shrinks, obfuscates
and shrinks resources. Keep rules live in `app/src/main/keepRules/`, which AGP merges automatically — there is no
`proguard-rules.pro` in AGP 9.

kotlinx.serialization ships its own consumer keep rules, so `@Serializable` classes need nothing hand-written. The
rules in `rules.keep` guard the one thing those do not cover: enum *constant names*. `TunerSettings` and `Tuning`
persist as JSON and write enum values by name, so if R8 ever renamed the constants an existing `settings.json`
would fail to parse and the `ReplaceFileCorruptionHandler` would silently reset the user's settings and custom
tunings. Measured on AGP 9.4, R8 keeps those names even without the rules — they are insurance against a future R8,
not a current fix.

Because R8 output is never exercised by the unit tests, after changing anything in `data/model/` install the
release APK over an existing install and confirm the saved tunings and settings survive.

The deobfuscation mapping for a build is at `app/build/outputs/mapping/release/mapping.txt`; keep the one that
matches a published APK if you ever need to read a stack trace from it.
