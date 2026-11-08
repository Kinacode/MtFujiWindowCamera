# Third-party components

This project uses the following components, downloaded by Gradle:

- OpenCV 4.11.0 — Apache License 2.0. See the accompanying LICENSE and COPYRIGHT files, Upstream third-party sources and component licenses: https://github.com/opencv/opencv/tree/4.11.0/3rdparty . https://opencv.org/license/
- AndroidX Activity, CameraX, Core, Lifecycle and ExifInterface — Apache License 2.0. https://source.android.com/docs/setup/about/licenses
- Guava / ListenableFuture — Apache License 2.0. https://github.com/google/guava
- Gradle Wrapper — Apache License 2.0. https://github.com/gradle/gradle/blob/v8.11.1/LICENSE
- AndroidX Test / JUnit (test APK only) — Apache License 2.0 / Eclipse Public License 1.0. https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt

The OpenCV Android AAR includes native code for supported ABIs. It is included by the dependency declaration; this repository does not vendor the AAR. See OpenCV-COPYRIGHT.txt for OpenCV copyright holders and the upstream 3rdparty directory for component-specific licenses.

## On-device neural inpainting (v1.6)

- MI-GAN / MI-GAN-512-Places2 pipeline by Picsart AI Research (PAIR), MIT license. See MI-GAN-WEIGHTS.txt and packaged assets/licenses/MI-GAN-WEIGHTS.txt.
- Official distribution: https://huggingface.co/andraniksargsyan/migan . Revision `406830d0fa60666da0071c342ad2fbc8f30c5c64`, file `migan_pipeline_v2.onnx`, SHA-256 `6f1f3530a1a2324b19752018ce756088b07973cda8d7d890034ace5c8a48c40b`.
- Official implementation: https://github.com/Picsart-AI-Research/MI-GAN . Model licensed for redistribution under MIT. The model is downloaded only at build time and packaged in the APK.
- Microsoft ONNX Runtime Android 1.31.1, MIT license. https://github.com/microsoft/onnxruntime . CPU execution; runtime telemetry is disabled. Android Internet permissions contributed by the dependency are explicitly removed.

## Current preview: LaMa (v1.7)

- LaMa ONNX by Carve-Photos, derived from advimman/lama, Apache-2.0. See LaMa-LICENSE.txt and LaMa-MODEL.txt, also bundled under assets/licenses.
- Pinned Hugging Face revision c3c0c9e468934d62e79c329e35d82dd09ff8c444, lama_fp32.onnx, SHA-256 1faef5301d78db7dda502fe59966957ec4b79dd64e16f03ed96913c7a4eb68d6.
- The 1.7 preview packages LaMa instead of MI-GAN. References above describe the earlier 1.6 weights. Both model downloads occur at build time only; user photos are never uploaded.
