# Third-party notices

AURA Lite includes or links against the following third-party software. Their respective licences
govern; full texts are reproduced below where the licence requires it.

## Android SDK / Jetpack

Android / androidx / AndroidX libraries are distributed by Google under the Apache License 2.0 and
the Android Open Source Project terms. `androidx` sources are at `https://android.googlesource.com/platform/frameworks/support`.

## Kotlin

Kotlin and the Kotlin Gradle plugin are distributed by JetBrains under the Apache License 2.0.
`https://kotlinlang.org`

## Gradle

Gradle is distributed under the Apache License 2.0. `https://gradle.org`

## Jetpack Compose & Material 3

Apache License 2.0.

## Hilt / Dagger

Google, Apache License 2.0.

## Room

Google, Apache License 2.0.

## sherpa-onnx (`sherpa-onnx-static-link-onnxruntime`)

Distributed by the k2-fsa project under the Apache License 2.0. The AAR is **not** committed to
this repo; CI (and the fetch task) downloads the pinned release from the k2-fsa/sherpa-onnx
GitHub releases page at build time. `https://github.com/k2-fsa/sherpa-onnx`

## ONNX Runtime (`com.microsoft.onnxruntime`)

Microsoft, MIT License.

## ML Kit Text Recognition

Google ML Kit terms of service apply (`https://developers.google.com/ml-kit`). The recognizer
downloads its language packs from the Play Services model delivery framework.

## Firebase (optional; only if `GOOGLE_SERVICES_JSON_BASE64` is set)

Google Firebase SDKs are distributed under the Apache License 2.0 and Google's Firebase terms.
Firestore rules in `firebase/firestore.rules` are ours (AGPL).

## Backblaze B2 (optional)

No Backblaze SDK is bundled; the app talks to the B2 HTTP API (S3-compatible, SigV4) directly.

## okhttp3 / OkHttp

Square, Apache License 2.0. `https://square.github.io/okhttp/`

## Ktor

JetBrains, Apache License 2.0.

## kotlinx.serialization

JetBrains, Apache License 2.0.

## Bouncy Castle

The Legion of the Bouncy Castle, MIT-style (Bouncy Castle Licence). `https://www.bouncycastle.org/`

## markdown (on-device audit/trace)

None — traces are written with kotlinx.serialization JSON, no markdown library.

## Privileged permissions

The app declares no non-SDK privileged permissions. `QUERY_ALL_PACKAGES` is used for
`lookup_app`/`launch_app` and disclosed in the onboarding flow and `PRIVACY.md`.
