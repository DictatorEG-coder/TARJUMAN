# ترجمان · TARJUMAN
**«كل ما تراه… بلغتك.»** — *Everything you see… in your language.*

> ⚠️ **حالة المشروع / Status:** هذه أول دفعة من الكود المصدري. **لم تُبنَ ولم تُختبر** على جهاز أو محاكٍ. قد يظهر خطأ أو أكثر في أول بناء، ويجب إصلاحه قبل الاعتماد على أي ميزة.
> *First source drop. It has NOT been compiled or run. Expect build errors on the first CI run.*

---

## العربية

### الفكرة
فقاعة عائمة فوق أي تطبيق: تلتقط الشاشة بموافقتك، تستخرج النص (OCR)، تترجمه إلى العربية (أو لغة أخرى) وتعرضه **فوق النص الأصلي** أو في لوحة. الأولوية: الترجمة **إلى العربية** من الإنجليزية واليابانية والصينية والكورية وغيرها.

### المنفَّذ في الكود (غير مُختبَر)
- فقاعة عائمة: سحب، التصاق بالحافة، حجم وشفافية، إخفاء/إظهار، قائمة سريعة، إشعار بأزرار، بلاطة الإعدادات السريعة.
- التقاط شاشة بـ MediaProjection (موافقة واحدة لكل جلسة) — شاشة كاملة أو منطقة بالسحب.
- OCR محلي (ML Kit): لاتيني، صيني، ياباني، كوري.
- ترجمة محلية (ML Kit) بعد تنزيل النماذج، مع اكتشاف اللغة تلقائيًا.
- عرض النتيجة فوق النص الأصلي أو في لوحة، نسخ الأصل/الترجمة، تبديل لغة الهدف من النافذة، إعادة الترجمة.
- شاشة ترجمة نص (تبديل اللغتين، نسخ، مشاركة، نطق TTS)، استقبال نص/صورة من «مشاركة» و«معالجة النص».
- ترجمة صورة من المعرض، وترجمة ملفات TXT وPDF (PDF عبر OCR حتى 30 صفحة) مع تصدير TXT.
- سجل محلي: بحث، مفضلة، حذف، إعادة الترجمة بمحرك آخر.
- إدارة نماذج الترجمة: تنزيل/حذف، Wi-Fi فقط، حزمة أساسية.
- طبقة محركات قابلة للتبديل: ML Kit محلي + خادم متوافق مع LibreTranslate (اختياري) مع بدائل عند الفشل.
- واجهة عربية RTL + إنجليزية، ثيم كحلي وبرغندي، صفحة خصوصية، زر «قناة التطوير» (https://t.me/Dictator_channel2).

### غير مكتمل / غير موجود
- OCR للنص **العربي** (ML Kit لا يدعمه). OCR للهندية والفارسية والعبرية والتايلاندية وغيرها غير مضاف.
- كشف فقاعات المانجا، النص العمودي المتقدم، إعادة رسم الصورة.
- الصوت (تحويل الكلام إلى نص). النطق TTS فقط.
- DOCX، الحفاظ على تنسيق المستندات، الكاميرا المباشرة.
- أساليب الكتابة (رسمي/ودي…) وذاكرة المصطلحات: تحتاج نموذجًا لغويًا ولم تُنفَّذ.
- NLLB / Marian / Argos محليًا: غير مضاف (ML Kit هو المحرك المحلي الوحيد حاليًا).
- تنزيل النماذج يتم بآلية ML Kit داخل التطبيق (لا WorkManager، ولا استئناف مضمون).
- تفضيلات المفاتيح تُخزَّن في SharedPreferences دون تشفير.
- قيود Android: التطبيقات ذات FLAG_SECURE تظهر سوداء (لا تجاوز). من Android 14 يطلب النظام موافقة التقاط عند كل تشغيل للفقاعة.
- الاختبارات: اختبارات وحدة بسيطة فقط (تقطيع النص، تخمين النص). لا اختبارات واجهة ولا محاكي.

### الرفع من Termux وبناء الـAPK
```bash
pkg install -y unzip git gh
unzip TARJUMAN.zip && cd TARJUMAN
git init -b main && git add . && git commit -m "TARJUMAN initial source"
gh auth login                      # سجّل الدخول لمرة واحدة
gh repo create tarjuman --public --source=. --push
```
بعد الرفع: تبويب **Actions ← Android CI ← Run workflow**، وعند النجاح نزّل الـAPK من **Artifacts → TARJUMAN-debug-apk**. إن فشل البناء فانسخ سطور الخطأ من سجل Actions وأرسلها لإصلاحها.

> لا يحتوي المشروع على `gradle-wrapper.jar`؛ سير العمل يثبّت Gradle 8.9 بنفسه. للبناء المحلي: `gradle assembleDebug` (JDK 17 + Android SDK 35).

### الأذونات
العرض فوق التطبيقات (الفقاعة) · الإشعارات (خدمة المقدمة) · موافقة التقاط الشاشة (نافذة النظام) · الإنترنت (تنزيل النماذج أو خادمك الاختياري).

### الخصوصية
لا تُحفظ لقطات الشاشة ولا تُرفع. OCR والترجمة المحلية على الجهاز. لا يخرج نص إلى الإنترنت إلا عبر خادم تضعه أنت. ترجمات الشاشة لا تُحفظ في السجل افتراضيًا.

---

## English
Floating-bubble screen translator for Android (Kotlin + Jetpack Compose). Pipeline: MediaProjection → ML Kit OCR (Latin/Chinese/Japanese/Korean) → ML Kit on-device translation (optional LibreTranslate-compatible server) → overlay over the original text or a panel. Main use case: foreign → Arabic.

**Implemented in code (untested):** bubble + quick menu + notification + QS tile; full/region capture; text, image and TXT/PDF translation; history; model manager; Arabic-first RTL UI; Telegram button; privacy page.
**Missing:** Arabic-script OCR, speech-to-text, DOCX, manga bubble detection, writing styles, glossary, NLLB/Marian/Argos engines, instrumented/UI tests.
**Build:** push to GitHub and run the *Android CI* workflow (Gradle 8.9, JDK 17, SDK 35); download the `TARJUMAN-debug-apk` artifact.
**Test report:** nothing has been compiled or run; only trivial JUnit tests exist (`TextUtilTest`).
See `THIRD_PARTY_NOTICES.md`, `docs/REFERENCE_ANALYSIS.md`, `CHANGELOG.md`.
