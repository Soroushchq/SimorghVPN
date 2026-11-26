<div dir="rtl">

# معماری سیمرغ — نسخه ۰.۱

این سند نقشه ادغام ۷ پروژه مرجع (`elham/`) در یک اپ واحد را توضیح می‌دهد.

## ۱. تصمیم‌های بنیادین

| موضوع | تصمیم | دلیل |
|---|---|---|
| زبان/UI | Kotlin + Jetpack Compose | الگوی mlmvpn؛ تک‌فایل UI مدرن، RTL بومی، تم دینامیک |
| هسته داده | Xray از طریق `libv2ray.aar` | همان مسیر اثبات‌شده Derafsh؛ VLESS/REALITY/Vision برای ایران حیاتی است |
| tun2socks | hev-socks5-tunnel (aar رسمی) | پیش‌فرض Derafsh و battle-tested؛ TUN داخلی Xray به‌عنوان گزینه دوم |
| فرآیند | تک‌پردازژه (v0.1) | سادگی حالت مشترک UI/سرویس؛ جداسازی `:daemon` در نقشه راه |
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
│   ├── CoreBootstrap.kt   کپی assets جئو + Seq.setContext + initCoreEnv
│   ├── XrayConfigBuilder.kt پروفایل → کانفیگ کامل Xray (JSON)
│   ├── XrayRunner.kt      پل CoreController (startLoop/stopLoop/stats)
│   └── VpnBus.kt          وضعیت مشترک سرویس ↔ UI (StateFlow)
├── vpn/
│   ├── SimorghVpnService.kt  VpnService + TUN + نوتیفیکیشن + آمار
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

1. UI دکمه اتصال → بررسی `VpnService.prepare()` → `startForegroundService(ACTION_START)`
2. سرویس بلافاصله `startForeground` (قانون ۵ ثانیه) → وضعیت `CONNECTING`
3. `CoreBootstrap.ensureInit` → کپی `geoip.dat/geosite.dat` (از assets هسته) → `Libv2ray.initCoreEnv`
4. `XrayConfigBuilder.build()` → کانفیگ کامل (socks + dns + routing + outbound)
5. بر اساس حالت تونل:
   - **hev**: `Builder.establish()` → `XrayRunner.startLoop(cfg, 0)` + `HevTunnel.start(fd)`
   - **xray-tun**: `establish()` → `startLoop(cfg, tunFd)` (کانفیگ شامل inbound تون)
   - **proxy-only**: بدون TUN؛ فقط `startLoop(cfg, 0)`
6. پولر آمار هر ثانیه → `queryAllOutboundTrafficStats()` → سرعت/حجم → `VpnBus` + نوتیفیکیشن

## ۴. کانفیگ Xray (نسخه ۰.۱)

- **inbound socks**: `127.0.0.1:10808`، `udp: true`، sniffing `http,tls,quic`، userLevel 8 (برای stats)
- **DNS**: سرور داخلی با `domains: [geosite:category-ir, domain:ir]` + `expectIPs: [geoip:ir]` → مستقیم؛ سرور راهور از پروکسی؛ `hosts` ثابت برای دامنه‌های Private DNS اندروید؛ قانون `port 53 → dns-out`
- **routing**: `geoip:private + geoip:ir → direct`، `geosite:category-ir + domain:ir → direct`، بقیه → `proxy`
- **policy**: سطح 8 با `statsOutboundUplink/Downlink` برای آمار زنده

## ۵. لایه موتور (مقصد نسخه‌های بعد)

نسخه ۰.۱ موتور Xray را داخل `core/` hard-wire کرده است. در ۰.۲ رابط زیر استخراج می‌شود تا موتورهای جدید (بر پایه الگوی `scanner/engines/` در mlmvpn) بدون دست زدن به UI اضافه شوند:

```kotlin
interface Engine {
    val id: String
    suspend fun prepare(context: Context, config: EngineConfig)
    suspend fun start(tunFd: Int): EngineHandle
    suspend fun stop()
    fun stats(): EngineStats
}
```

موارد نامزد (به‌ترتیب نقشه راه): Aether/MASQUE (msn-guard)، desync ضد-DPI (shroudly)، uTLS rotation (nova)، FLUX racing (mlmvpn)، شبکه رله openrung.

## ۶. سازگاری لایسنس

- کد سیمرغ: GPL-3.0
- `libv2ray.aar` (AndroidLibXrayLite / Xray-core): LGPL-3.0 — سازگار
- `hev-socks5-tunnel.aar`: MIT — سازگار
- فونت وزیرمتن: OFL
- استفاده **ایده‌محور** از پروژه‌های elham (بدون کپی کد)؛ هرگاه کدی عیناً پورت شود، فایل مبدأ و لایسنس آن در سرآیند ذکر و سازگاری GPL/AGPL رعایت می‌شود.

</div>
