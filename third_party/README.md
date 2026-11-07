# Third-party components

This project uses the following components, downloaded by Gradle:

- OpenCV 4.11.0 — Apache License 2.0. See the accompanying LICENSE and COPYRIGHT files, Upstream third-party sources and component licenses: https://github.com/opencv/opencv/tree/4.11.0/3rdparty . https://opencv.org/license/
- AndroidX Activity, CameraX, Core, Lifecycle and ExifInterface — Apache License 2.0. https://source.android.com/docs/setup/about/licenses
- Guava / ListenableFuture — Apache License 2.0. https://github.com/google/guava
- Gradle Wrapper — Apache License 2.0. https://github.com/gradle/gradle/blob/v8.11.1/LICENSE
- AndroidX Test / JUnit (test APK only) — Apache License 2.0 / Eclipse Public License 1.0. https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt

The OpenCV Android AAR includes native code for supported ABIs. It is included by the dependency declaration; this repository does not vendor the AAR. See OpenCV-COPYRIGHT.txt for OpenCV copyright holders and the upstream 3rdparty directory for component-specific licenses.
