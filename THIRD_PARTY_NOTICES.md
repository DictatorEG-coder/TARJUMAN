# Third-party notices

TARJUMAN's source is original. No source code was copied from the reference projects below.

## Runtime dependencies
| Component | License / terms |
|---|---|
| Kotlin, kotlinx.coroutines | Apache-2.0 |
| AndroidX (core, activity), Jetpack Compose, Material 3 | Apache-2.0 |
| Google ML Kit (text-recognition, -chinese, -japanese, -korean, language-id, translate) | Proprietary SDK, free to use under the ML Kit Terms of Service (not open source). Translation models are downloaded from Google on demand. |
| JUnit 4 (tests only) | EPL-1.0 |

Optional online provider: any LibreTranslate-compatible server chosen by the user (LibreTranslate server is AGPL-3.0; no server code is bundled).

## Reference projects (ideas only, no code copied)
| Project | License | How it informed TARJUMAN |
|---|---|---|
| ciddwd/overlay-translator | Apache-2.0 | Pipeline idea: MediaProjection -> OCR -> translation -> overlay; model download UX; region selection. |
| digitaltrekkerr/jusoor | MIT | Arabic-first RTL UX and an honest per-permission explanation. |
| ismamsha/MuinScreenTranslator | Not verified (LICENSE not inspected) | Idea of OCR split: ML Kit for supported scripts plus a separate engine for Arabic. No code used. |

If code from an Apache-2.0 or MIT project is ever added, its NOTICE/copyright text must be added here first.
