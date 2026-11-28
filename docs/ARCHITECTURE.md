<div dir="rtl">

# معماری سیمرغ — نسخه ۰.۳.۰

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
│       └── XrayEngine.kt     هسته Xray + پل hev به‌عنوان اولین موتور
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

## ۷. سازگاری لایسنس

- کد سیمرغ: GPL-3.0
- `libv2ray.aar` (AndroidLibXrayLite / Xray-core): LGPL-3.0 — سازگار
- `hev-socks5-tunnel.aar`: MIT — سازگار
- فونت وزیرمتن: OFL
- استفاده **ایده‌محور** از پروژه‌های elham (بدون کپی کد)؛ هرگاه کدی عیناً پورت شود، فایل مبدأ و لایسنس آن در سرآیند ذکر و سازگاری GPL/AGPL رعایت می‌شود.

</div>
