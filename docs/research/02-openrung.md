# گزارش تحلیل: اکوسیستم OpenRung

> Task 2-b | اسنپ‌شات‌ها: `elham/openrung` + `elham/openrung-mobile-app` | GPL-3.0 | Go 1.25 + RN 0.86

## ۱. پشته فناوری

**openrung (مونوریپوی Go، ~120k LOC):** connectcore (32,774 — موتور سیاست کلاینت)، internal/ (56,675 — بروکر، رله، تونل، punch)، cmd/ (9,845 — بروکر/رله/TUI)، brokerapi (6,862)، wsscore (4,753 — Reality-over-WSS)، desktop/desktop-volunteer (Wails v2 + React)، punchcore (1,855).

**openrung-mobile-app:** React Native 0.86 / TS 5.8؛ Kotlin ~4k؛ Go punchbridge ~9.8k؛ Swift ~11.7k؛ نقشه MapLibre.

## ۲. connectcore چیست؟

**نه پروتکل سیمی — موتور سیاست اتصال کلاینت.** پروتکل سیمی VLESS+REALITY+Vision است که sing-box می‌کارد؛ connectcore همه‌چیزِ بالای تونل را ارکستر می‌کند:

- ماشین حالت `Status{Disconnected,Preparing,Connecting,Connected,Disconnecting,Failed}` در `engine.go`.
- **اتصال = نردبان نامزدها** (`runLadder`): مستقیم اول → خطای typed → جبهه‌های WSS امضاشدهٔ آن رله؛ رتبه‌بندی سمت کلاینت با تأخیر TCP (`ranker.go`).
- **failover میان‌جلسه‌ای**: `monitor.go` (reladder)، epoch های شبکه، fallback خانواده IPv6→IPv4.
- **اینترفیس‌های باریک میزبان** (`interfaces.go`): `EventSink`, `NoticeSink`, `Persistence`, `OSProxy`, `Elevation`, `PunchEstablisher` — عمداً gomobile-bindable.
- `client/singbox_config.go` (804 خط): سازندهٔ کانفیگ sing-box شامل failover DoH، fake-IP rotation، split-tunnel .srs.

## ۳. معماری بروکر

`brokerapi`: URL policy + **تأیید امضای Ed25519 فهرست رله‌ها** + ترنسپورت ضدسانسور: ECH روی فرانت کلادفلر (کانفیگ compile-time)، **SNI-less CloudFront** (Host رمزشده، بدون fallback — fail-closed)، Azure Front Door به‌عنوان آخرین راه. سرور بروکر هرگز در مسیر داده نیست؛ فهرست رتبه‌بندی‌شدهٔ کوتاه‌مدت امضاشده می‌دهد.

## ۴. داوطلبان (volunteer nodes)

VPS: اسکریپت یک‌خطی → کانتینر هاردند + Xray + رجیستر خودکار + چرخش ساعتی UUID با HKDF. خانگی: اپ Wails با دروازه رضایت؛ عبور از NAT با reverse tunnel یا **punch**: بازتابندهٔ دوIP → طبقه‌بندی NAT → simultaneous open با توکن HMAC → **QUIC روی سوکت پانچ‌شده**.

## ۵. اپ موبایل

- ادغام sing-box: نه AAR معمولی نه gomobile ساده — `SINGBOX_VERSION` پین commit، پچ Reality، حذف build-tag ها (Tailscale/WG/naive، صرفه ۱۲MB)، سپس **graft**: بایندینگ‌های خودمون با module path `github.com/sagernet/sing-box/experimental/libbox` داخل پکیج libbox خود sing-box کاشته می‌شود (فقط یک ران‌تایم gomobile مجاز است) + تأیید ABI با javap.
- صفحات: MainScreen (نقشه)، Settings، SplitTunneling، Licenses…؛ حالت Mock-first برای تست بدون بیلد نیتیو.
- ۹ زبان شامل fa و ar؛ نقشه انتخاب exit با نشان کلاس رله.

## ۶. پنج قابلیت قابل انتقال

1. **الگوی connectcore** (نه لزوماً شبکه OpenRung): نردبان + نامزد رتبه‌بندی‌شده + failover پایش‌شده + epoch، پشت اینترفیس‌های باریک — ارزشمندترین دارایی؛ هر موتوری (sing-box/Xray/WG) می‌تواند زیرش بنشیند.
2. **کشف کنترل‌پلین ضدسانسور**: مسابقه سه‌جبهه + دایرکتوری امضاشده با `not_after`.
3. **Reality-over-WSS/CDN** با تیکت صادرهٔ بروکر (`wsscore/`).
4. **NAT punch با پل QUIC** (`punchcore/`).
5. **سیستم بیلد graft** برای هستهٔ گوموبایل کنترل‌شده — دقیقاً مکانیزم لازم برای چند هستهٔ Go در یک APK.

## ۷. موانع

- **GPL-3.0 همه‌جا تأیید** — کلاینت باید GPL/AGPL سازگار باشد (سیمرغ: AGPL ✓).
- تله‌متری سمت کلاینت (`clienttelemetry` — UUID نصب + هارت‌بیم ~۱/دقیقه، پیش‌فرض روشن) — تصمیم محصولی برای بازنگری.
- مسکرید ماژول punchbridge فقط در graft کار می‌کند.
- Xray-core فقط سمت رله (MPL-2.0، بدون آلودگی کلاینت).
