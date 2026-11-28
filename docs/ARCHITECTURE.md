<div dir="rtl">

# معماری سیمرغ — نسخه ۰.۴.۰

این سند نقشه ادغام ۷ پروژه مرجع (`elham/`) در یک اپ واحد را توضیح می‌دهد.

## ۱. تصمیم‌های بنیادین

| موضوع | تصمیم | دلیل |
|---|---|---|
| زبان/UI | Kotlin + Jetpack Compose | الگوی mlmvpn؛ تک‌فایل UI مدرن، RTL بومی، تم دینامیک |
| هسته داده | Xray از طریق `libv2ray.aar` | همان مسیر اثبات‌شده Derafsh؛ VLESS/REALITY/Vision برای ایران حیاتی است |
| tun2socks | hev-socks5-tunnel (aar رسمی) | پیش‌فرض Derafsh و battle-tested؛ TUN داخلی Xray به‌عنوان گزینه دوم |
| فرآیند | دوعمردازه از ۰.۲.۱ | سرویس VPN در پردازه `:daemon` — مرگ UI تونل را نمی‌اندازد؛ ارتباط با Messenger IPC (الگوی mlmvpn/nova) |
| داده | JSON (kotlinx-serialization) + DataStore | بدون وابستگی سنگین؛ پروفایل‌ها قابل export |
| لایسنس | GPL-3.0 | سازگار با تمام موتورهای GPL/AGPL که در فازهای بعد اضافه می‌شوند |

## ۲. ساختار کد

```
com.simorgh.vpn
├── SimorghApp.kt          Application + کانال نوتیفیکیشن + بوت‌استرپ هسته
├── MainActivity.kt        میزبان Compose + مجوزها + import از deep-link
├── data/
│   ├── Profile.kt         مدل پروفایل (@Serializable) + اشتراک
│   ├── ProfileRepo.kt     ذخیره JSON + CRUD + جریان StateFlow
│   ├── SubscriptionRepo.kt دریافت/به‌روزرسانی اشتراک (HTTP)
│   ├── SettingsStore.kt   DataStore (مسیریابی، تونل، DNS، ...)
│   └── ShareLinkParser.kt پارس vless/vmess/trojan/ss (و base64)
├── core/
│   ├── CoreBootstrap.kt   کپی assets جئو + Seq.setContext + initCoreEnv (اتمیک)
│   ├── XrayConfigBuilder.kt پروفایل → کانفیگ کامل Xray (JSON)
│   ├── XrayRunner.kt      پل CoreController (startLoop/stopLoop/stats)
│   ├── VpnBus.kt          وضعیت مشترک سرویس ↔ UI + marshalling باندل IPC
│   └── engine/            رجیستری موتور (الگوی mlmvpn) — ۰.۲.۱
│       ├── VpnEngine.kt      رابط موتور (start/stop/stopAwait/stats)
│       ├── EngineRegistry.kt ثبت id → factory؛ انتخاب با settings.engineId
│       ├── XrayEngine.kt     هسته Xray + پل hev به‌عنوان اولین موتور
│       └── WarpEngine.kt     تونل رایگان کلادفلر (۰.۳.۱) — wireguard outbound بومی Xray
├── vpn/
│   ├── SimorghVpnService.kt  VpnService (در پردازه :daemon) + TUN + IPC سرور
│   ├── DaemonIpc.kt      پل Messenger سمت UI — آینه‌ی وضعیت/لاگ در پردازه اصلی
│   └── HevTunnel.kt       پوشش hev.htproxy.TProxyService (YAML + fd)
├── ui/
│   ├── theme/             پالت سیمرغ (شعله/طلایی + فیروزه‌ای) + وزیرمتن
│   ├── AppRoot.kt         Scaffold + NavHost + نوار پایین
│   ├── components/        دکمه اتصال سیمرغ، چیپ‌ها، کارت‌ها
│   └── home/ profiles/ settings/ logs/
└── util/
    ├── LogBuffer.kt       حافظه حلقوی لاگ + StateFlow
    └── Fmt.kt             فرمت بایت/مدت/اعداد فارسی
```

## ۳. جریان اجرای اتصال

از ۰.۲.۱ سرویس در پردازه‌ی جدا `:daemon` اجرا می‌شود. UI فقط intent می‌فرستد و وضعیت را از IPC آینه می‌کند:

1. UI دکمه اتصال → بررسی `VpnService.prepare()` → `startForegroundService(ACTION_START)` + `DaemonIpc.bind()`
2. سرویس در `:daemon` بلافاصله `startForeground` (قانون ۵ ثانیه) → وضعیت `CONNECTING` → پخش به کلاینت‌های IPC
3. تنظیمات از `SettingsMirror` (اسنپ‌شات JSON اتمیک که پردازه‌ی اصلی با هر تغییر می‌نویسد — DataStore تک‌پردازژه است) + خواندن تازه پروفایل‌ها از دیسک
4. `CoreBootstrap.ensureInit` → `EngineRegistry.create(settings.engineId)` → `XrayEngine.start(tun)`
5. بر اساس حالت تونل:
   - **hev**: `Builder.establish()` → هسته Xray (socks) + `HevTunnel.start(fd)`
   - **xray-tun**: `establish()` → `startLoop(cfg, tunFd)` (کانفیگ شامل inbound تون)
   - **proxy-only**: بدون TUN؛ فقط هسته socks
6. پولر آمار هر ثانیه → سرعت/حجم → `VpnBus` (داِیمن) → پیام IPC → `VpnBus` (UI) + نوتیفیکیشن

## ۴. کانفیگ Xray (نسخه ۰.۱)

- **inbound socks**: `127.0.0.1:10808`، `udp: true`، sniffing `http,tls,quic`، userLevel 8 (برای stats)
- **DNS**: سرور داخلی با `domains: [geosite:category-ir, domain:ir]` + `expectIPs: [geoip:ir]` → مستقیم؛ سرور راهور از پروکسی؛ `hosts` ثابت برای دامنه‌های Private DNS اندروید؛ قانون `port 53 → dns-out`
- **routing**: `geoip:private + geoip:ir → direct`، `geosite:category-ir + domain:ir → direct`، بقیه → `proxy`
- **policy**: سطح 8 با `statsOutboundUplink/Downlink` برای آمار زنده

## ۵. لایه موتور (از ۰.۲.۱ فعال)

رابط موتور در `core/engine/VpnEngine.kt` استخراج شد و Xray به‌عنوان اولین موتور در `EngineRegistry` ثبت شده است؛ انتخاب موتور با `settings.engineId`. موتورهای جدید (بر پایه الگوی `scanner/engines/` در mlmvpn) بدون دست زدن به سرویس یا UI اضافه می‌شوند:

```kotlin
interface VpnEngine {
    val id: String
    val isRunning: Boolean
    suspend fun start(context: Context, profile: Profile, settings: SimorghSettings, tun: ParcelFileDescriptor?): Boolean
    fun stop()
    suspend fun stopAwait(timeoutMs: Long = 3000)
    fun stats(): Pair<Long, Long>
}
```

موارد نامزد (به‌ترتیب نقشه راه): Aether/MASQUE (msn-guard)، desync ضد-DPI (shroudly)، uTLS rotation (nova)، FLUX racing (mlmvpn)، شبکه رله openrung.

## ۶. لایه ضدسانسور (از ۰.۳.۰ فعال)

سه مکانیزم در `core/anticensor/`، همگی در پردازه‌ی `:daemon`:

- **TlsFragmentProfile** — فرگمنت‌سازی ClientHello روی `streamSettings.sockopt.fragment` بومی هسته (الگوی msn-guard): سه پروفایل ایران «ملایم/متوسط/شدید» با بازه‌های تصادفی طول/فاصله. فقط برای اتصال‌های TLS/REALITY اعمال می‌شود.
- **FingerprintRotator** (الگوی nova) — چرخش uTLS بین `chrome/firefox/safari/edge/ios/android/randomized`؛ فینگرپرینتی که probe واقعی را رد کرد «چسبنده» می‌شود و اتصال بعدی از همان‌جا ادامه می‌دهد. فینگرپرینت‌های فقط-TLS1.3 (android/qq/360) طبق سازگاری nova از چرخش حذف شده‌اند — به‌علاوه فینگرپرینت ثابت پروفایل با `settings.fingerprintOverride` (فیلد گذرا، هرگز ذخیره نمی‌شود) در هر تلاش بازنویسی می‌شود.
- **FallbackPlanner + DataPlaneProbe** (الگوی nova/msn-guard) — هر «وصل» = حداکثر ۳ پروفایل × ۳ فینگرپرینت. بعد از هر روشن‌شدن موتور، یک درخواست HTTP واقعی از داخل تونل (SOCKS محلی، resolve در سمت سرور) فرستاده می‌شود؛ فقط جواب 2xx یعنی تونل زنده است. با هر دو سوییچ خاموش، پلن دقیقاً به یک تلاش ساده‌ی v0.2 فرو می‌ریزد. برای هر تلاش TUN تازه establish می‌شود (هسته‌ی Go مالک fd می‌شود — الگوی v2rayNG).

## ۶ب. موتور WARP بدون سرور + گاوصندوق هویت (از ۰.۳.۱ فعال)

در `core/warp/` — الگوی Aether/msn-guard با شاسیِ بدون کد بومی:

- **Curve25519** (`core/crypto/`) — پیاده‌سازی اختصاصی RFC 7748 (نردان مونتگومری روی میدان TweetNaCl ۱۶-لَمبی، ثابت-زمان) برای ساخت کلید X25519 روی خود گوشی؛ فقط کلید عمومی هرگز از گوشی خارج می‌شود. با تمام بردارهای تستی RFC صحت‌سنجی شده است.
- **WarpApi** — ثبت‌نام ناشناس رایگان در api.cloudflareclient.com (جریان wgcf/warp-plus): اول مستقیم، در صورت شکست از داخل SOCKS تونلِ روشن.
- **WarpIdentityVault** — گاوصندوق هویت: JSON هویت‌ها (تا ۳ عدد، با هویت فعال) با AES-256/GCM زیر کلید غیرقابل‌خروج Android Keystore (`simorgh_warp_vault`) مهر می‌شود؛ فایل اتمیک است تا UI و :daemon همزمان بخوانند. هویت‌ها هرگز وارد بکاپ نمی‌شوند.
- **WarpEngine + WarpConfigBuilder** — موتور دوم در `EngineRegistry`: همان شاخه‌ی SOCKS/hev، اما outbound = پروتکل `wireguard` بومی Xray با کلید خصوصی گاوصندوق، آدرس‌های تخصیص‌یافته، `reserved` سه‌بایتی WARP و MTU ۱۲۸۰. اندپوینت‌ها مستقیم IP شماره می‌شوند تا حلقه‌ی مرغ‌و‌تخم‌مرغ DNS پیش نیاید؛ دامنه‌های cloudflareclient.com در مسیریابی direct می‌مانند.
- **WarpPlanner** — پلن تلاش مخصوص WARP با همان ارزِ `FallbackPlanner.Attempt`: حداکثر ۶ تلاش روی ترکیب IP آنیکست × پورت‌های جایگزین WireGuard (۲۴۰۸/۸۹۴/۹۴۳/۵۰۰/…) با probe دیتا-پلن بعد از هر تلاش؛ فیلدهای گذرا `warpIpOverride/warpPortOverride` از دیمون مقدار می‌گیرند.

## ۷. هوش تطبیقی و اشتراک (از ۰.۴.۰ فعال)

چهار مکانیزم جدید:

- **FLUX** (`core/flux/` — الگوی mlmvpn) — حافظه‌ی شبکه: برای هر شبکه (wifi/cell) و هر مسیر (پروفایل×فینگرپرینت یا اندپوینت WARP) نرخ موفقیت EWMA (α=۰.۲)، نمونه‌های RTT و خنکای قطعی‌کننده (بریکر ۳ شکست پیاپی، بک‌آف نمایی تا ۶ ساعت، قرنطینه‌ی ۲۴ ساعته برای گره‌های مرده) در `flux-memory.json` نگه داشته می‌شود. قبل از هر اتصال، پلن با حافظه مرتب و یک مسابقه‌ی TCP موازی (۱۲ اتصال همزمان، ضرب‌الاجل ۲.۵ ثانیه) روی اندپوینت‌ها اجرا می‌شود — برنده‌های سریع مسابقه اول می‌آیند. نتیجه‌ی هر تلاش (موتور/probe واقعی) به حافظه برمی‌گردد؛ چون RST جعلی ممکن است، فقط تلاش کامل حکم می‌نویسد. مرگ موتور وسط جلسه = failover خودکار (حداکثر ۲ راند، بدون دخالت کاربر) و بعد رفتار v0.2 (کیل‌سوییچ/خطا).
- **MAE** (`core/mae/` — الگوی mlmvpn) — مسیریابی تطبیقی هر-سرویس: رجیستری ۱۸ سرویس پرکاربرد ایران (دامنه‌ها + URL پروب کوچک و امن). یادگیرنده از داخل خود اپ (که مستثنی از tun است، پس سوکتش همیشه مستقیم است) یک GET کوچک به هر سرویس می‌زند: جواب آمد ⇒ direct (سریع‌تر، بدون مصرف تونل)، جواب نیامد ⇒ پروکسی. نتیجه در `mae-state.json` و به‌صورت قواعد field به Xray (قبل از لیست‌های geo) تزریق می‌شود؛ override دستی هر سرویس (خودکار/تونل/مستقیم) از UI ممکن است.
- **درگاه اشتراک بدون روت** (`gateway/` — الگوی nova) — توزیع شفاف بدون روت ناممکن است (پکت فورواردشده UID ندارد؛ قوانین مسیریابی اندروید همه `iif lo` دارند). به‌جایش گوشی درگاه می‌شود: یک شنونده روی آدرس اینترفیس هات‌اسپات که HTTP CONNECT + absolute-form و SOCKS5 را روی یک پورت صحبت می‌کند؛ بالا‌دست = socks محلی هسته، پس ترافیک کلاینت با شرایط ترافیک خود اپ وارد تونل می‌شود. اینترفیس اشتراک با نبودِ `NET_CAPABILITY_INTERNET` (بدون هاردکد زیرشبکه) پیدا می‌شود؛ پورتال فارسی + PAC روی پورت جدا، رمز فقط به کلاینت‌های همان اینترفیس؛ سیاست عدم-نشت: تونل پایین ⇒ ۵۰۳ فارسی / SOCKS 0x02. جزئیات: `docs/GATEWAY.md`.
- **کانال پیکربندی امضاشده** (`core/config/` — الگوی msn-guard/shroudly) — پارامترهای ضدسانسور (پروفایل فرگمنت/فینگرپرینت‌ها/اندپوینت‌های WARP/منابع کانفیگ) بدون انتشار APK به‌روز می‌شوند: فایل JSON کوچک + امضای Ed25519 (کتابخانه eddsa، بدون ثبت provider)، کلید عمومی در `RemoteConfigChannel.PUBLIC_KEY_B64` سنجاق شده است. هر خرابی = ادامه با تنظیمات فعلی؛ آخرین کانفیگ تأییدشده در دیسک مانده و در هر شروع اپ دوباره اعمال می‌شود. انتشار با ورک‌فلو `remote-config.yml` و Secret `REMOTE_CONFIG_SIGNING_KEY`. جزئیات: `docs/REMOTE-CONFIG.md`.

## ۸. سازگاری لایسنس

- کد سیمرغ: GPL-3.0
- `libv2ray.aar` (AndroidLibXrayLite / Xray-core): LGPL-3.0 — سازگار
- `hev-socks5-tunnel.aar`: MIT — سازگار
- `eddsa` (net.i2p.crypto): MIT — سازگار
- فونت وزیرمتن: OFL
- استفاده **ایده‌محور** از پروژه‌های elham (بدون کپی کد)؛ هرگاه کدی عیناً پورت شود، فایل مبدأ و لایسنس آن در سرآیند ذکر و سازگاری GPL/AGPL رعایت می‌شود.

</div>
