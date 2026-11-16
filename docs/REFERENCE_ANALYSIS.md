# Reference analysis (README-level, nothing was built or run)

| Project | Stack | License | Notes |
|---|---|---|---|
| overlay-translator ("Screen Translator") | Kotlin, Compose, Hilt; MediaProjection/Shizuku; ML Kit, PaddleOCR, manga-ocr; llama.cpp submodule | Apache-2.0 | Closest to TARJUMAN's goal, but aimed at Asian games/manga, UI in Chinese/English, local LLMs of ~1.1-1.3 GB. Used as inspiration only. |
| jusoor | Flutter/Dart | MIT | Arabic-first UI, LLM-only translation through user API keys, one commit, no APK yet. Not suitable as a base. |
| MuinScreenTranslator | Native Android (from search snippet only) | Unknown | ML Kit + Tesseract OCR (ar/ru/en), ML Kit translation, emulator test script. Repository page could not be opened (rate limit). |

Key constraint found: ML Kit text recognition v2 supports Latin, Chinese, Devanagari, Japanese and Korean scripts only; Arabic-script OCR needs another engine (e.g. Tesseract) and is not implemented yet.
