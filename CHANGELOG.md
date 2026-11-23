# Changelog

## 0.1.1 (Chinese translation compatibility update)
- Clarify the supported source as Simplified Chinese (zh), matching Jianying / CapCut China UI text.
- Normalize Chinese locale tags (zh-CN, zh-Hans, zh-SG, zh-TW, zh-Latn) to ML Kit's supported zh translation model.
- Apply language-code normalization consistently to automatic detection, offline model management, and the optional LibreTranslate provider.
- Expand Han-character fallback detection and add unit tests for Chinese locale normalization and CJK Extension A.
- Keep Arabic (ar) as the default target; the floating-bubble OCR → translation → overlay pipeline remains the primary workflow.
- Build and device verification are pending the GitHub Actions run.

## 0.1.0 (initial source drop)
- Floating bubble (drag, edge snap, size/opacity, hide/show, quick menu), notification actions, Quick Settings tile.
- MediaProjection capture (one consent per session) -> ML Kit OCR (Latin/Chinese/Japanese/Korean) -> on-device ML Kit translation.
- Full-screen or region capture; result shown over the original text or in a panel; copy original/translation; quick target-language switch.
- Text translator, image translator (picker + share target), TXT/PDF translator (PDF via OCR).
- Local history (search, favourites, delete, retranslate with another engine), offline model manager (download/delete).
- Pluggable engine layer (ML Kit offline, LibreTranslate-compatible online with fallback).
- Arabic-first RTL UI with English; dark navy + burgundy theme; Telegram channel button; privacy page.
- GitHub Actions: unit tests, lint, debug APK.
