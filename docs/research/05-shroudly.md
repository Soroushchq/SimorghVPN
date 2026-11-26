# گزارش تحلیل: Shroudly

> Task 2-e | اسنپ‌شات: `elham/shroudly` | umutxyp/Shroudly v2.6.90 | ~135k LOC | **MIT** | Codeshare Technology Ltd

اپ ضدسانسور چندسکویی (Win/macOS/Linux/Android/iOS/TV) — **بدون سرور VPN و بدون حساب**.

## ۱. پشته

دسکتاپ: Electron 43 + Next.js static export + React 18. موبایل/TV: بومی کامل (Kotlin Views؛ Swift/SwiftUI). موتور: **C خالص بدون هیچ وابستگی بیرونی** (~24k LOC در `core/src/`). tun2socks: hev v2.18 پین‌شده با tree-hash. یک هسته، سه جاسازی: فرزند پروسه (stdin/JSON-lines)، JNI درپروسه (اندروید)، lib استاتیک در Network Extension (اپل).

## ۲. چگونه بدون سرور کار می‌کند

**VPN نیست و ترافیک را رله نمی‌کند.** ترافیک مستقیم به سرور مقصد می‌رود؛ فقط **بستهٔ اول (ClientHello/Host)** بازشکل می‌شود تا DPI نام‌بنیاد نتواند بخواند + DNS روی DoH. خانواده ByeByeDPI/zapret:

- `desync.c`/`strategy.c` (DSL): tlsrec در آفست‌های SNI آگاه از SLD، split، disorder، بایت urgent TCP، MD5 decoy لینوکس + ۲۴ پریست مرتب.
- DoH ضد‌مسموم: **۱۷–۲۲ مسیر هاردکد از ۶ اپراتور** (کلادفلر/گوگل/Quad9/AdGuard/OpenDNS/DNS.SB + IPv6)؛ مسیرهای by-IP (بدون نام در هندشیک)؛ ۳ مسیر by-name از روی IP های فرانت مشترک کلادفلر؛ مسابقه موج‌دار + backoff؛ رد پاسخ‌های منطبق با ~۱۸۰ fingerprint صفحهٔ بلاک OONI.
- **حالت پیشرفته** (ایران/چین، باینری ممتاز): WinDivert/NFQUEUE بدون کتابخانه — fake packet با TTL خودکار، drop RST جعلی، seq-overlap، **drop Initial کوانتیک** (وادار به TCP)، decoy صدا Discord، intercett شفاف DNS.
- آخرین راه: Tor Snowflake (دانلود Tor hash-تأییدشده + پروکسی وب embed شده). اختیاری: سرور خودتان (socks5/http/ss/vless/trojan). «اشتراک‌گذاری اتصال» P2P: ماشین شما سرور Shadowsocks دوستان می‌شود (UPnP).
- فرانت CDN فقط برای دانلود لیست‌های خود اپ (فرانت‌های Fastly: python.org، pypi.org، ted.com — کلادفلر/Akamai/AWS cross-host را نمی‌پذیرند).

## ۳. هسته — گوهر (قابل انتقال‌ترین دارایی)

`engine.c` (6,208 خط): گوش‌دهنده SOCKS5/4/4a + UDP ASSOCIATE + **«نردبان تلاش»**:

1. تلاش اول هر میزبان ناشناخته **PLAIN** (سربار صفر برای سایت‌های سالم).
2. در صورت رد شدن: مسابقه موازی تا ۲۴ استراتژی («پا»، LEG_MAX 26، بودجه ۱۵s) با بازپخش payload اول روی اتصال‌های تازه.
3. **حافظهٔ تکنیک برنده per-host** + تشدید در بریدن میانی (stall/reset/noreply).

پکیج‌های دیگر: `proto.c` (پارس ClientHello + یافتن آفست SNI)، `fake.c`، `doh.c`، `probe.c`، `adblock.c` (345k دامنه در هسته). پروتکل کنترل متنی: `STATS/PROBE/STOP/…` ورودی؛ رویدادهای JSON خروجی (`bypass|blocked|cut|probe`).

## ۴. UI/UX

صفحهٔ اصلی فقط یک orb قدرت با آمار زنده؛ تور معرفی ۴مرحله‌ای اسپات‌لایت؛ **Autopilot** (تشخیص بلاک per-سرویس و اقدام یک‌خطی)؛ **۳۱ زبان شامل fa با RTL کامل**؛ TV با D-pad؛ ویجت‌ها + QS tile + Control Center.

## ۵. توزیع لیست

لیست مسیریابی سایت‌ها از **داده‌های OONI ۳۰روزه + Citizen Lab** با CI هفتگی؛ adblock هفتگی (GPL داده)؛ کانفیگ ریموت **امضاشده ECDSA P-256** با allow-list آرگومان؛ فرانت Fastly وقتی GitHub بلاک است.

## ۶. پنج قابلیت قابل انتقال

1. **نردبان تلاش با tکنیک آموخته per-host و plain-first** — بهترین طراحی موتور دی‌سینک خوددرمان؛ مستقل از موتور.
2. **DoH رِیسینگ چندمسیری با سلامت/backoff** + مسیرهای by-IP بی‌نام.
3. **Autopilot/تشخیص بلاک per-سرویس + حالت‌های صادقانهٔ «قابل‌حل نیست».**
4. **چرخهٔ حیات system-proxy ضدکرش** + watchdog پروسهٔ جدا.
5. **یک‌هسته/سه‌جاسازی + کانفیگ ریموت امضاشده + فرانت Fastly.**

نکات دیگر: سرور SS درون‌برنامه‌ای برای اشتراک؛ دیتابیس fingerprint صفحهٔ بلاک OONI؛ hev + fake-IP (198.19.0.0/16) تا VPN مبتنی بر TUN همچنان دامنه ببیند.

## ۷. موانع

- **MIT تأیید** — استفادهٔ آزادانه با ذکر.
- بدون blob اختصاصی؛ WinDivert (LGPL) fetch-time با hash پین؛ Tor/Snowflake BSD-3 دانلود lazy.
- دادهٔ adblock مشتق GPL-3.0 — فقط داده، جدا منتشر می‌شود.
- پوستهٔ Electron سنگین است اگر فقط موتور را بخواهی — هسته C و DoH و system-proxy تمیز جداشدنی‌اند.
