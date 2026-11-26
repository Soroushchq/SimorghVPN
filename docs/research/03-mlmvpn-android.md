# گزارش تحلیل: MLMVPN Android

> Task 2-c | اسنپ‌شات: `elham/mlmvpn-android` | mlmvpn/mlmvpn_android v1.2.45 | ~246k LOC Kotlin | GPL-3.0 + §7 (ذکر منبع)

## ۱. پشته

Kotlin 2.1.0، Compose BOM + Material3 (هیبرید با ViewBinding برای ویجت)، minSdk 24 / target 34، AGP 8.13.2، بدون DI (سینگلتون‌های دستی)، OkHttp + Gson، coroutines/StateFlow همه‌جا. فونت IranSansX.

## ۲. ماژول‌ها

`:app` (همه‌چیز: `core/` موتورهای سطح‌پایین + `scanner/` بقیه)، `:TMessagesProj` (فورک تلگرام = پیام‌رسان گره — **در اسنپ‌شات غایب**)، `:mlmchat-core` (Rust، Apache-2.0)، `worker-src/studio/` (سورس worker پنل)، `third_party/`، `store/` (کانال engine-store امضاشده).

## ۳. طراحی چندموتوره (یافته اصلی)

**هیچ انتزاع بزرگ موتور وجود ندارد — دو لایه:**

- **لایه ۱ — `IVpnEngine` باریک** (`core/warp/IVpnEngine.kt`): فقط `start(context, config, localPort): Boolean` + `stop()`. شش پیاده‌کننده که دیتاپلین‌شان به TUN `MyVpnService` می‌چسبد.
- **لایه ۲ — دیسپچ id متنی**: هر متد یک id (`masque`, `warp`, `psiphon`, `tor`, `geph`, `mae`, `flux` و… — ۲۷ عدد در `WidgetMethods.IDS`)؛ رجیستری‌ها روی id؛ سوییچ زمان اجرا `when(method)` در `WidgetConnect`.

**دو VpnService + ماهواره‌ها:**
- `MyVpnService` (1,778 خط) — خانواده Xray: دیسپچ روی «شکل کانفیگ» (JSON خام Xray / aether / gst / hybrid(TCP-via-VLESS+UDP-via-WARP) / softether / AmneziaWG / URI) → `XrayJsonGenerator` (1,569 خط).
- `TunnelVpnService` (7,220 خط) — استک ترنسپورت: MASQUE/WG/WoW با هسته Rust (`libtunnelcore.so`)، **Psiphon با نردبان تشدید A–E و Psiphon-over-WARP**، Tor+بریج‌ها، Geph/Lantern/URnetwork (exec)، WARP خالص Kotlin (`WarpCrypto`/`TweetNaclFast`).
- `OpenVpnService`، AmneziaWG در پروسه `:awg`، tun2proxy در `:tun`؛ `VpnSwitch.stopAll` + `TunnelExclusion` = فقط یک صاحب TUN.
- **هسته‌های Xray همه out-of-process در `:host`** (`core/warp/XrayHosts.kt`) تا panic اپ را نکشد؛ fork اar با `registerProcessFinder(UidProcessFinder)` → قواعد `process` روی UID اندروید.

## ۴. MAE — موتور تطبیقی (۵,546 LOC در `engines/mae/`)

- **رجیستری سرویس** (`assets/mae/services.json`): بسته‌ها/دامنه‌ها/IPها/پروب‌ها/هینت‌ها per سرویس؛ قابل به‌روزرسانی ریموت («داده نه کد»).
- **یادگیری per-شبکه**: EWMA RTT/okRate/throughput + هیسترزیس 0.15 (چالش‌بَر باید واضح بهتر باشد — بدون flapping) + TTL اعتماد ۲–۱۰h.
- **پروب‌ها با همان Xray**: هر پروب یک کانفیگ واقعی با یک SOCKS per outbound است — «همان ترنسپورتی که ترافیک را می‌برد، اندازه‌گیری می‌شود».
- **سوییچ زنده**: **gRPC Xray (RoutingService/StatsService) روی Unix socket در filesDir** — تعویض مسیر بدون قطع اتصال؛ در غیر این صورت recompile+reconnect با throttle.
- **پایش**: LiveCanary هر ۵ دقیقه + یادگیری منفعل از شمارنده‌های Xray هر ۳۰ ثانیه + CircuitBreaker + RepairLadder.
- **egress خارجی**: ساخت خروجی US روی حساب Cloudflare خود کاربر برای سرویس‌های geo-locked.

## ۵. cfnet — موتور استراتژی آنفیلترینگ کلادفلر (`engines/cfnet/`)

کاتالوگ `TunnelShape` (uTLS personas، frag+finalMask، ECH، XHTTP h2/h11/stream/frag، masque_h2، warp، chain_tor/psiphon) × `AddressPolicy` (clean-IP/CF-edge/IPv6-twin) + `StrategyEngine` خالص (Detect→Test→Validate→Select→Persist→Reuse→Monitor→Recover) با ذخیره اطمینان per-شبکه و خوددرمانی. ابزارهای خواهر: `cfdoctor` (خط کجا می‌شکند)، `scout` (همه متدها را روی خط شما می‌آزماید)، `arena` (مسابقه نود).

## ۶. Smart-Iran (`core/tunnel/SmartIranRoute.kt`)

بایپس سه‌لایه: (۱) اپ‌های ایرانی مستثنا از VPN، (۲) CIDR های ایران با `excludeRoute` (فقط رنج‌های بزرگ ~2,225 تا از فروپاشی SystemUI جلوگیری شود)، (۳) قواعد دامنه Xray → direct برای `.ir` + ~57k دامنه (لیست‌های Chocolate4U، رفرش روزانه). روی همهٔ موتورها کار می‌کند.

## ۷. UI

صفحه خانگی iPhone-styles با ~45 کاشی، پوشه/داک/درگ؛ ناوبری استک modal سفارشی؛ **پریت رشته‌ای en/fa اجباری** (`checkStringParity` — 5,609=5,609)؛ همیشه‌تاریک؛ ۲۷ ویجت per-متد؛ QS tile.

## ۸. کانفیگ/اشتراک

URI های vless/vmess/trojan/ss (+پسوندهای PattNG: cs/fm/ech)، اشتراک base64 با پارس `Subscription-Userinfo`، `.ovpn`، AmneziaWG `[Interface]`، هویت aether/gst، QR دوطرفه، بکاپ مهندسی‌شده.

## ۹. بیلد

**بدون CI.** باینری‌ها: `.so`/`.aar` از LFS (در اسنپ‌شات غایب — ۱۱ فایل per-ABI)؛ **engine-store ران‌تایم با امضای minisign** و exec با `/system/bin/linker64` (دور زدن W^X) + قرنطینه کرش. geoip/geosite از استور در filesDir.

## ۱۰. پنج قابلیت برتر قابل انتقال

1. **MAE مسیریابی تطبیقی per-app با سوییچ زندهٔ بالانسر Xray** — گوهر تاج.
2. **cfnet استراتژی‌اندازه‌گیر برای آنفیلترینگ CF** — خالص و تست‌پذیر.
3. **ایزوله‌سازی out-of-process هسته + engine-store امضاشده.**
4. **اتوماسیون حساب کلادفلر از داخل شبکه سانسور** (OkHttp از مسیر WARP) + استودیوی پنل چندکاربره.
5. **نردبان Psiphon + ترنسپورت‌های زنجیره + FLUX warm-start racing** («متصل فقط بعد از حرکت بایت واقعی»).

## ۱۱. موانع

- **GPL-3.0 + بند §7**: هر استفاده باید «mlmvpn» را به‌طور مرئی ذکر کند (منبع/کامپایل).
- هسته Rust تونل و `libmhrv_rs.so` **سورس در ریپو ندارند**؛ `librstaspoof.so` بدون repo؛ AAR Xray فورک خاص.
- در اسنپ‌شات: `chat/`، `scripts/`، `native/`، `app/libs/`، jniLibs غایب — غیرقابل بیلد.
- اندپوینت‌های زیرساخت خودم redacted شده‌اند.
