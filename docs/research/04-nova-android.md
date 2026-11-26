# گزارش تحلیل: Nova-Android

> Task 2-c → 2-d | اسنپ‌شات: `elham/nova-android` | confeden/Nova-Android | com.brent.nova v1.35.2 | ~108k LOC Kotlin + 21k Go | GPL-3.0

کلاینت ضدسانسور روسی، مهندسی‌شده علیه TSPU/DPI روسیه — هم‌ارز چالش ایران.

## ۱. پشته

Kotlin (Views کلاسیک، بدون Compose)؛ Go: nova-core 21,195 + nova-proxy (Mihomo/Clash.Meta) + nova-xray (پوشش c-shared روی فورک Xray، 148k LOC vendored)؛ minSdk 24 / target 34؛ NDK 27.2 پین‌شده؛ tun2proxy Rust v0.7.21.

## ۲. لایه‌بندی ماژول‌ها

- **nova-core**: موتور اصلی Go (gomobile → AAR): AmneziaWG userspace (نوشتن مستقیم fd تونل)، MASQUE/CONNECT-IP، DNS intercept/obfuscation، ترنسپورت‌های Tor، تلگرام شفاف WSS، استتار بسته WARP. + پکیج‌های خالص `tlsshape/` (persona های uTLS) و `cfws/` (امضای هندشیک WSS).
- **nova-xray**: پوشش c-shared دور Xray — چون (الف) دو AAR گوموبایل تداخل `libgojni`/`go.Seq` دارند، (ب) quic-go نسخه‌ها تداخل دارند. پل C-ABI + JNI.
- **nova-proxy**: پروسه کمکی جدا با Mihomo (پارس/اجرای کانفیگ‌های Clash/Hysteria2/TUIC/AnyTLS).
- الگو: *هسته‌های fd-دار Go درپروسه؛ هسته‌های SOCKS-family → tun2proxy؛ هلپرها out-of-process.*

## ۳. تکنیک‌های ضد-DPI (ارزش اصلی)

1. **SNI-mask با چرخش/یادگیری/تشخیص رژیم** (`SniMaskPolicy.kt` + `traffic_mask_russia.sni` ۵۹,۱۱۲ خطی دست‌چین + `white.sni` مشتق از تحلیل لیست‌سفید TSPU): رژیم WHITLIST = فقط نام‌های اثبات‌شده؛ چرخش قطعی per-نود؛ نام‌های ترجیحی آموخته‌شده per-ترنسپورت.
2. **ضدmeasure بلاک SNI API وارپ**: رجیستر WARP از داخل کانال زنده (پروکسی اپرا محلی) + رله سوئد؛ ECH با uTLS به‌عنوان راه‌حل بلندمدت.
3. **uTLS shaping** (`tlsshape/shape.go`): چرخش Chrome→Firefox→Android فقط هنگام هندشیک بی‌پاسخ (۲۰s) + بازنویسی جراحیکی ALPN («JA3 شکل را حفظ می‌کند؛ JA4 یک فیلد می‌بیند»).
4. **REALITY fingerprint repair**: جانشینی fp های TLS-1.2-only شکستنی با `chrome`.
5. **استتار AWG I1**: جعل **QUIC Initial اصیل RFC 9001** (کلید/هدرپروتکشن درست) به‌عنوان بسته junk اول + چرخش خاموش SNI + حافظه per-شبکه (Wi-Fi=هش SSID، سلولار=MCC+MNC) (`ProtonQuicInitial.kt`, `AwgI1Adaptation.kt`, `NetworkI1Memory.kt`).
6. **DNS obfs racing**: تقسیم ClientHello در آفست‌های نسبیِ SNI (سگمنت TCP/رکورد TLS)، مسابقه (آدرس×استراتژی)، stagger 250ms، برنده memoize.
7. **MASQUE mimicry**: نردبان پورت تجربی (8095/8443/4443 قبل 443)، burst کوانتیک شبیه دسکتاپ، `trackedPacketConn` برای دور زدن recvmmsg.
8. **نجات شفاف تلگرام**: تشخیص رنج DC تلگرام در لایه TUN (پارس SYN) → مسیردهی شفاف به WSS روی Workers با امضای HMAC و **SNI خنثی** (نام واقعی در Host نه SNI).
9. **کاوش اندپوینت WARP**: CIDR های CF × ۵۴ پورت، seed های تأییدشدهٔ همراه.
10. **bind-over-protect** برای CONNECT-IP.

## ۴. نردبان AUTO و تاب‌آوری

`NovaVpnService` (29,749 خط، پروسه `:vpn`): نردبان **NOVA→WARP→MASQUE→OPERA→PROTON→TOR→DNS** با بودجه ۱۲۰s پله‌ای؛ **انتخاب دستی هرگز بی‌صدا جایگزین نمی‌شود**؛ liveness با پروب واقعی نه شمارنده بایت؛ `TunnelStallDetector` (آستانه بالای نویز هندشیک)؛ بازیابی پس از تغییر شبکه.

## ۵. توزیع کانفیگ

داخل‌سازی zero-config: WARP/MASQUE/Opera/Proton با ثبت on-device از رله سوئد (فقط ثبت API، 443، allowlist hostname، کلید چرخشی per-ریلیز). فید نود GitHub با ۳ میرور، صفحه‌بندی، تأیید دوبار روزانه با HTTPS-204 واقعی از همان هسته. پارسر استریم اشتراک‌های عظیم (dedupe بر اساس fingerprint).

## ۶. UI

روسی منبع + ترجمه ران‌تایم به EN بدون recreation؛ تم‌های «liquid glass»؛ TV/leanback + D-pad؛ ویجت + QS tile؛ split-tunnel با لیست سخت‌کد «اپ‌های مستقیم روسی» (بانک‌ها و…) — قرینهٔ مستقیم اپ‌های بانکی ایرانی.

## ۷. پنج قابلیت قابل انتقال برای ایران

1. **ماشین SNI-mask + تشخیص رژیم + حافظه per-شبکه** — نگاشت ۱:۱ روی «شبکه ملی» ایران؛ لیست روسی به‌عنوان قالب.
2. **نردبان AUTO چندموتوره + انتخاب صادق + liveness با پروب.**
3. **الگوی ایزوله‌سازی چند هسته Go در یک APK** (c-shared + JNI + پروسه کمکی).
4. **استتار AWG I1** — WG خام در ایران throttle می‌شود؛ این روش نجاتش می‌دهد.
5. **DNS racing + intercett DNS در TUN** + تونل DNS آخرین‌راه.

نکات دیگر: نجات شفاف تلگرام؛ استک «اندپوینت آزاد» با ثبت on-device؛ پارسر اشتراک استریمی؛ اشتراک‌گذاری گیت‌وی با سرور WG یوزراسپیس روی iface تتر.

## ۸. موانع

- **GPL-3.0 تأیید**؛ Xray فایل‌ها MPL-2.0 (per-file).
- ۶ پچ اجباری روی وابستگی‌ها + فورک vendored؛ باینری‌های prebuilt (AAR 37MB و…).
- زیرساخت: رله سوئد + فید نود و آپدیت hardwired به `confeden/nova_updates`؛ سکریت TG WSS در بیلد.
- بدون تله‌متری؛ لاگ دیاگنوستیک ماسک IP.
- زبان/فرهنگ روسی‌محور؛ لیست SNI باید معادل ایرانی بگیرد.
