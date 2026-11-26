# معماری سیمرغ — سند یکپارچه‌سازی هفت پروژه به یک اپ واحد

> نسخه: 0.1 (پیش‌نویس تصمیم‌ها) — مبنا: تحلیل عمیق ۷ پروژه مرجع در `elham/` (گزارش‌ها: `docs/research/`)

## ۰. خلاصه اجرایی

**سیمرغ** یک کلاینت اندروید واحد است که موتورهای ضدسانسورِ هفت پروژه مرجع را زیر **یک قرارداد مشترک (Engine API)** جمع می‌کند. کاربر فقط دکمه اتصال را می‌زند؛ «سیاست‌گذار» (SimorghCore) به‌صورت خودکار بهترین موتور را برای شبکهٔ فعلی پیدا می‌کند، سلامت اتصال را با پروب دادهٔ واقعی تأیید می‌کند و در صورت خرابی، بی‌درنگ به موتور بعدی نردبان سوییچ می‌کند.

- **پلتفرم اول:** اندروید (Kotlin + Jetpack Compose)، فارسی‌محور و RTL
- **minSdk هدف:** 24 (هم‌راستا با nova/mlmvpn؛ در فاز ۰ نهایی می‌شود)
- **لایسنس:** **AGPL-3.0** (اجباری — بخش ۲ را ببینید)
- **اصل بنیادین:** «اتصال صادقانه» — متصل یعنی بستهٔ واقعی داده از داخل تونل رد شده، نه اینکه هسته start خورده باشد

## ۱. اصول طراحی

1. **یک اپ، چند موتور.** کاربر «اینترنت» را می‌خواهد نه «پروتکل» را. انتخاب پروتکل یک تنظیمات پیشرفته است، نه جریان اصلی.
2. **اتصال صادقانه (honest connect).** از nova و mlmvpn: هیچ موتوری «متصل» اعلام نمی‌شود مگر با پروب واقعی (HTTPS+دانلود+آپلود از داخل تونل). دست‌کاردن byte-counter به‌عنوان liveness ممنوع.
3. **صفر-کانفیگ اول.** از shroudly و nova: اپ بدون هیچ کانفیگی باید بتواند اینترنت آزاد بدهد (Desync + WARP رایگان). کانفیگ/اشتراک لایهٔ تقویتی است.
4. **فارسی‌محور.** رابط فارسی درجه‌یک، فونت Vazirmatn، آینه‌سازی RTL، مسیریابی هوشمند ترافیک داخلی (Smart-Iran).
5. **متن‌باز و بالادست‌دار.** همهٔ کد تحت AGPL-3.0؛ ذکر منبع اجباری (`NOTICE`).
6. **هر لایه جایگزین‌پذیر.** موتور، TUN و سیاست‌گذار پشت interface های باریک‌اند تا هر قطعه بدون لمس بقیه تعویض/ارتقا یابد.

## ۲. تصمیم لایسنس: AGPL-3.0

چون قصد داریم **کد** (نه فقط ایده) از این پروژه‌ها برداریم، لایسنس اپ واحد با سخت‌ترین لایسنسِ بالادستِ موردِ استفاده گره می‌خورد:

| پروژه | لایسنس | کد قابل استفاده؟ | پیامد |
|-------|--------|------------------|--------|
| msn-guard (Aether Rust) | **AGPL-3.0** | بله | کل اپ را AGPL می‌کند |
| mlmvpn-android | GPL-3.0 + بند §7 (ذکر منبع) | بله با ذکر صریح «mlmvpn» | سازگار با AGPL |
| nova-android | GPL-3.0 | بله | سازگار با AGPL |
| openrung (+mobile) | GPL-3.0 | بله | سازگار با AGPL |
| shroudly | MIT | بله، آزادانه | بدون الزام |
| defyxvpn | MIT (موتور DXcore بسته) | فقط الگو/UX — موتور قابل استفاده نیست | بدون الزام |
| Derafsh (پایه خودمان) | GPL-3.0 | بله | سازگار با AGPL |

**تصمیم:** کل اپ سیمرغ تحت **AGPL-3.0** منتشر می‌شود + فایل `NOTICE` با ذکر mlmvpn (بند §7) و بقیهٔ بالادست‌ها. AGPL برای یک کلاینت (بدون SaaS سمت سرور) عملاً هم‌ارز GPL-3.0 است و همهٔ بالادست‌ها را مجاز می‌کند.

## ۳. چه چیزی از هر پروژه می‌گیریم

| پروژه | چه می‌گیریم | فایل مرجع کلیدی |
|-------|-------------|------------------|
| **Derafsh** (پایه) | هسته Xray (libv2ray.aar)، TUN با hev، UI کامپوز، اشتراک‌ها، تم فارسی/RTL، آبشار تبلیغات داخلی | `V2rayNG/app/…` — کل اپ |
| **mlmvpn** | الگوی `IVpnEngine`، MAE مسیریابی تطبیقی per-app، موتور استراتژی cfnet، Smart-Iran سه‌لایه، ایزوله‌سازی out-of-process هسته، نردبان Psiphon، پریت‌های i18n | `core/warp/IVpnEngine.kt`، `engines/mae/`، `engines/cfnet/`، `core/tunnel/SmartIranRoute.kt` |
| **msn-guard** | رابط `TunEngine` با سه پیاده‌سازی و fallback، کاوش اندپوینت با اعتبارسنجی دیتاپلین، MTU-probe/اسکن خودکار، ECH roster، remote policy بدون ریلیز، playbook بسته‌بندی اندروید | `TunEngine.kt`، `MtuProbe.kt`، `EchEndpointRoster.kt` |
| **nova** | نردبان AUTO هفت‌موتوره با بودجه ۱۲۰ثانیه‌ای و «انتخاب دستی هرگز بی‌صدا عوض نمی‌شود»، SNI-mask با تشخیص رژیم (whitelist/blacklist)، DNS-obfs racing، استتار AWG I1، الگوی ایزوله‌سازی چند هسته Go در یک APK | `ConnectionSelectorPolicy.kt`، `SniMaskPolicy.kt`، `engine/dns_obfs.go`، `nova-xray/` |
| **openrung** | الگوی connectcore: رتبه‌بندی نامزد + failover میان‌جلسه‌ای + epoch شبکه، interface های باریک گوموبایل‌پذیر، کشف سه‌جبهه‌ای کنترل‌پلین + فهرست امضاشده Ed25519، سیستم بیلد graft برای sing-box | `connectcore/engine.go`، `brokerapi/signing.go` |
| **shroudly** | هسته C دی‌سینک DPI با نردبانِ «اول ساده، بعد مسابقه تکنیک‌ها + حافظه per-host»، DoH رِیسینگ ۲۲ مسیره، fake-IP برای دیده‌شدن دامنه‌ها در TUN | `core/src/engine.c`، `electron/doh-forwarder.js` |
| **defyxvpn** | فقط UX: قرارداد کانال هسته (blob + progress-event)، نمایش «گام n از m» هنگام جست‌وجوی کانفیگ، پریت‌های split-tunnel با آیکون، فونت Vazirmatn، تجربهٔ اتصال (اسپیدتست) | `lib/modules/core/vpn_bridge.dart` |

## ۴. نمای لایه‌ای

```
┌────────────────────────────────────────────────────────┐
│  UI — Jetpack Compose (فارسی، RTL، یک‌ضربه)              │
│  orb اتصال | گام‌های AUTO | split-tunnel | تنظیمات       │
├────────────────────────────────────────────────────────┤
│  SimorghCore — سیاست‌گذار (قلب سیمرغ)                    │
│  • نردبان AUTO + بودجه‌بندی (nova)                       │
│  • رتبه‌بندی نامزد + failover خودکار (openrung)          │
│  • اتصال صادقانه با پروب دیتاپلین                        │
│  • (فاز ۴) مسیریابی تطبیقی per-app (MAE از mlmvpn)       │
├────────────────────────────────────────────────────────┤
│  Engine API — قرارداد باریک مشترک                        │
│  Xray | Desync | WARP/MASQUE | Psiphon | Tor | (sing-box)│
├────────────────────────────────────────────────────────┤
│  TunEngine — TUN جایگزین‌پذیر با fallback (msn-guard)    │
│  hev-socks5-tunnel (اصلی) → tun2proxy → tun2socks       │
│  + fake-IP (198.19.0.0/16) تا موتور دامنه ببیند          │
├────────────────────────────────────────────────────────┤
│  Data — پروفایل، اشتراک، remote policy امضاشده           │
│  + Smart-Iran (بایپس سه‌لایه داخلی)                       │
└────────────────────────────────────────────────────────┘
```

## ۵. قرارداد Engine API (پیش‌نویس کاتلین)

```kotlin
enum class EngineId { XRAY, DESYNC, WARP, MASQUE, PSIPHON, TOR }

data class Capabilities(
    val udp: Boolean,               // پشتیبانی UDP
    val exposesSocks: Boolean,      // موتور SOCKS محلی می‌دهد (TunEngine وصل می‌شود)
    val wantsTunFd: Boolean,        // موتور خودش fd تونل می‌خواهد (مثل AWG)
    val zeroConfig: Boolean,        // بدون کانفیگ کار می‌کند (Desync/WARP)
)

interface VpnEngine {
    val id: EngineId
    val capabilities: Capabilities

    /** آماده‌سازی و اعتبارسنجی کانفیگ — بدون عوارض جانبی شبکه */
    suspend fun prepare(profile: Profile): PreparedConfig

    /** اجرای موتور. محیط مشترک (SOCKS، fd، protect، DNS) تزریق می‌شود */
    suspend fun start(env: EngineEnvironment, config: PreparedConfig): EngineSession

    /** سلامت = بستهٔ واقعی داده از داخل تونل (اصل اتصال صادقانه) */
    suspend fun probe(config: PreparedConfig): HealthResult

    suspend fun stop()
}

class EngineEnvironment(
    val protectSocket: (Int) -> Boolean,  // VpnService.protect
    val tunFdProvider: (() -> Int)?,      // برای موتورهای fd-دار
    val dns: DoHRacingResolver,           // از شردلی: ۲۲ مسیر با backoff
    val stats: StatsSink,
)

interface EngineSession {
    val state: Flow<EngineState>          // disconnected → connecting → validating → connected
    val stats: Flow<EngineStats>
    suspend fun stop()
}
```

**قواعد اجرا (از nova/mlmvpn):**
- هر هسته Go در پروسه جدا (`:host-*`) اجرا می‌شود تا panic هسته اپ را نکشد؛ چند gomobile در یک پروسه ممنوع (درس nova: تداخل quic-go و `go.Seq`).
- هنگام تعویض موتور: `stopAll()` تضمین می‌کند فقط یک صاحب TUN وجود دارد (`TunnelExclusion` از mlmvpn).
- انتخاب دستی کاربر هرگز بی‌صدا جایگزین نمی‌شود؛ نردبان AUTO فقط در حالت AUTO فعال است.

## ۶. SimorghCore — سیاست‌گذار

- **نردبان AUTO (فاز ۲):** DESYNC → XRAY(اشتراک) → WARP → MASQUE → PSIPHON؛ هر پله بودجه ۱۲۰ثانیه‌ای؛ برنده در حافظهٔ شبکه (SSID/MCC-MNC) می‌ماند (الگوی `NetworkI1Memory` نووا و `lastconn` ام‌اس‌ان).
- **failover میان‌جلسه‌ای (فاز ۳):** `TunnelStallDetector` نووا + `reladder` اوپن‌رانگ — قطع صدا/ستال → پلهٔ بعدی بدون دخالت کاربر.
- **پروب صادقانه:** `HealthResult = Ok(measured) | Degraded | Dead` بر اساس پروب دیتاپلین (نه byte-counter).

## ۷. لایه TUN

- رابط `TunEngine` از msn-guard: `hev` (اصلی، از درفش) → `tun2proxy` (نووا/راست) → `tun2socks/badvpn` (legacy) با fallback خودکار روی `UnsatisfiedLinkError`.
- **fake-IP** با رنج `198.19.0.0/16` (شردلی) تا موتورهای SOCKS-based دامنهٔ واقعی ببینند — پیش‌نیاز موتور Desync در حالت TUN.
- MTU: مقدار اولیه per-transport + اسکن خودکار هنگام اتصال (`MtuProbe`/`MtuAutoScan` از msn-guard، فاز ۵).
- `specialUse` foreground service با subtype پاک‌شده از playbook اندروید ۱۵ مِس‌ان-گارد.

## ۸. زرادخانهٔ ضد-DPI (اولویت ایران)

| تکنیک | منبع | فاز |
|-------|------|-----|
| نردبان دی‌سینک (tlsrec/split/disorder/fake) با حافظه per-host | shroudly `core/src/engine.c` | ۲ |
| DoH racing چندمسیری + تشخیص block-page | shroudly | ۲ |
| SNI-mask با تشخیص رژیم whitelist/blacklist + لیست ایرانی | nova `SniMaskPolicy.kt` | ۵ |
| DNS-obfs racing (تقسیم ClientHello نسبت به آفست SNI) | nova `engine/dns_obfs.go` | ۵ |
| ECH roster با پروب زنده | msn-guard `EchEndpointRoster.kt` | ۵ |
| استتار AWG I1 (QUIC Initial اصیل) | nova `ProtonQuicInitial.kt` | ۵ |
| uTLS shaping + fragmentation | mlmvpn cfnet + nova tlsshape | ۴ |

**Smart-Iran (فاز ۴، روشن به‌صورت پیش‌فرض):** بایپس سه‌لایهٔ ترافیک داخلی از mlmvpn — اپ‌های ایرانی از تونل مستثنا، CIDR های ایران با `excludeRoute`، دامنه‌های `.ir` و لیست‌های دامنه (به‌روزرسانی روزانه).

## ۹. UI/UX

- **صفحه اصلی = یک orb اتصال** (ادامهٔ `ConnectCircleButton` درفش + الگوی شردلی): حالت‌های idle/connecting/validating/connected/failed با بازخورد زنده.
- **نمایش نردبان AUTO:** «گام ۲ از ۵ — در حال آزمایش WARP…» (UX فلو‌لاین defyxvpn).
- **Split-tunnel سه‌حالته** (`off/exclude/only`) + پریست اپ‌های محبوب با آیکون (defyxvpn) + قواعد دامنه.
- فونت **Vazirmatn** (از defyxvpn)، ترجمه فارسی کامل با پریت string-parity (درس mlmvpn)، تم روشن/تاریک، ویجت + QS tile.
- اسپیدتست داخلی Cloudflare (defyxvpn) و نمودار تأخیر زنده.

## ۱۰. توزیع کانفیگ و لیست

- **اشتراک:** پارسر URI (vless/vmess/trojan/ss) + اشتراک base64 از درفش، QR دوطرفه.
- **remote policy امضاشده (Ed25519):** لیست‌های policy/SNI/اندپوینت بدون ریلیز اپ — ترکیب الگوی msn-guard (`remote/policy.json` + sync workflow) و اوپن‌رانگ (فهرست امضاشده + `not_after`).
- **zero-config:** Desync و WARP بدون سرور و حساب کار می‌کنند (شردلی/nova).

## ۱۱. ساختار ریپو

```
SimorghVPN/
├── app/                     # UI (Compose M3) + platform (VpnService, ویجت، tile)
├── core/
│   ├── simorgh-core/        # سیاست‌گذار + نردبان AUTO + session state
│   ├── engine-api/          # قرارداد VpnEngine/EngineEnvironment/TunEngine
│   ├── engine-xray/         # هسته Xray از درفش (پروسه جدا)
│   ├── engine-desync/       # پورت C شردلی (NDK، JNI)
│   ├── engine-warp/         # Aether Rust (WARP/MASQUE/WG)
│   ├── engine-psiphon/      # نردبان Psiphon
│   └── tun/                 # TunEngine + hev + fake-IP
├── data/                    # پروفایل، اشتراک، remote policy، Smart-Iran
├── elham/                   # اسنپ‌شات مراجع (فریز — فقط مطالعه)
└── docs/
    ├── ARCHITECTURE.md      # همین سند
    └── research/            # ۶ گزارش تحلیل پروژه‌ها
```

## ۱۲. فازبندی

| فاز | محتوا | خروجی قابل نصب |
|-----|-------|----------------|
| **۰ — اسکلت** | ماژول‌بندی Gradle، Engine API، CI، NOTICE/AGPL | بیلد سبز چندماژوله |
| **۱ — MVP** | Xray (مهاجرت درفش) + TunEngine/hev + UI یک‌ضربه + اشتراک/QR | اپ هم‌ارز درفش با معماری جدید |
| **۲ — صفر-کانفیگ** | موتور Desync (پورت شردلی) + fake-IP + نردبان AUTO + پروب صادقانه | اپ بدون کانفیگ وصل می‌شود |
| **۳ — موتورهای آزاد** | WARP/MASQUE (Aether) + Psiphon نردبان + failover میان‌جلسه‌ای | چند مسیر رایگان + مقاومت قطعی |
| **۴ — هوشمند** | MAE per-app + Smart-Iran + cfnet (uTLS/frag/ECH) | مسیریابی تطبیقی |
| **۵ — سخت‌افشاری** | MTU-scan + SNI-pool ایران + DNS-obfs + remote policy امضاشده | پایداری در سخت‌ترین رژیم‌ها |

## ۱۳. ریسک‌ها و تدارک

- **چند هستهٔ Go در یک APK:** تداخل `go.Seq`/quic-go شناخته‌شده است — راه‌حل‌ها: پروسهٔ جدا برای هر هسته (mlmvpn) یا c-shared بدون gomobile (nova) یا graft (openrung). فاز ۱ فقط Xray دارد؛ ریسک از فاز ۳.
- **حجم APK:** ABI split + (بعداً) engine-store امضاشده با دانلود lazy (mlmvpn `StoreEngines`).
- **باینری‌های گمشده:** mlmvpn سورس `.so` های Rust خود را ندارد؛ Aether مستقل از msn-guard بیلد می‌شود. شردلی سورس کامل دارد (MIT).
- **لیست SNI ایرانی:** لیست ۵۹هزارتایی نووا روسی است؛ معادل ایرانی باید به‌صورت عملیاتی و مشارکتی ساخته شود (فاز ۵).
- **AGPL:** پروژه برای همیشه متن‌باز می‌ماند — این تصمیم است، نه محدودیت.
