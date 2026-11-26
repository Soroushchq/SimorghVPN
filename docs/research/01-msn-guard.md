# گزارش تحلیل: MSN-GUARD

> Task 2-a | اسنپ‌شات: `elham/msn-guard` | mbm110/MSN-GUARD | v3.2.1 | ~282MB | AGPL-3.0

کلاینت اندروید ضدسانسور با موتور Rust «Aether» (vendored از CluvexStudio) و ۷ ترنسپورت: WireGuard, MASQUE, WoW (WARP-on-WARP), Psiphon, Tor, Foxy, SHARD.

## ۱. پشته فناوری

| جزء | زبان | LOC | مسیر |
|---|---|---|---|
| اپ اندروید | Kotlin | ~52,000 | `app/src/main/java/com/msnguard/vpn/` |
| موتور Aether | Rust | 30,682 | `core/aether/src/` |
| quiche (fork کلادفلر، vendored) | Rust | 135,330 | `core/quiche/` |
| badvpn + lwIP tun2socks | C | 98,929 | `app/src/main/cpp/badvpn/` |
| hev-socks5-tunnel | C | 146,335 | `app/src/main/cpp/hev/` |
| AnyTLS sidecar | Go | ~2K | `anytls/` |

minSdk 26، target/compileSdk 36، AGP 8.13.2، Kotlin 2.3.0، NDK 26.3. بدون Compose (Views + Canvas سفارشی).

## ۲. ماژول‌ها

تک‌ماژول `:app` + همراه‌های غیرگریدلی: `core/` (Rust)، `anytls/` (Go)، `remote/` (policy های runtime)، `tools/` (بیلد Tor و geoip)، `tests/` (تست‌های قرارداد پایتونی روی سورس)، `docs/` (شامل architecture.md فارسی).

## ۳. یکپارچه‌سازی موتور (Rust ↔ Android)

- **C-ABI دست‌نویس + JNI** (نه uniFFI): `core/aether/src/ffi.rs` توابع `aether_*` را export می‌کند؛ پل C++ در `app/src/main/cpp/aether_jni.cpp` → `NativeCore.kt`.
- **جریان کانفیگ با env-var** (نه JSON): `CoreConfig.env()` نقشه `AETHER_*` می‌سازد → `NativeCore.setEnv` → `setenv` در JNI.
- **محافظت سوکت**: کال‌بک `aether_set_socket_protector` هر fd موتور را به `VpnService.protect()` می‌رساند.
- **بیلد**: تسک گریدلی `buildRustCore<Abi>` → `core/build-android.sh` → `jniLibs/<abi>/libaether.so`.
- **محل پروتکل‌ها در Rust**: `wireguard.rs`، `masque.rs`/`quic.rs` (H3)، `masque_h2.rs` (+تقسیم ClientHello در `fragment.rs`)، WoW/gool، `psiphon.rs`، `tor.rs`، `prober.rs`، `tunnelping.rs`، `netstack.rs` (smoltcp)، `account.rs` (WARP identity)، `stats.rs`.
- **سمت Kotlin**: Psiphon به‌صورت AAR درپروسه؛ Tor با exec؛ Foxy خالص Kotlin/Netty در `foxy/`؛ SHARD = exec `libxray.so` + AnyTls؛ Zeptun (Zig).

## ۴. سرویس VPN

- یک سرویس `MsnGuardVpnService` (7,639 خط) با `foregroundServiceType="specialUse"` و subtype VPN برای دور زدن محدودیت ۶ساعته Android 15.
- ساخت TUN per-protocol (۸ نقطه establish).
- **سه موتور TUN قابل‌تعویض** پشت اینترفیس `TunEngine`: `LegacyEngine` (badvpn)، `HevEngine` (libhev)، `ZeptunEngine` — با fallback خودکار. بینش کلیدی: **هسته Rust خودش TUN ندارد؛ SOCKS5 :1819 می‌دهد و پل TUN به آن وصل می‌شود.**
- آمار: اتمیک‌های Rust → `aether_stats_snapshot()` → poller یک‌هرتزی → نوتیفیکیشن + broadcast.

## ۵. کاوش اندپوینت و MTU

- `prober.rs` (خانواده‌های CIDR مَسک)، `wg_prober.rs` (پروب هندشیک WG)، `tunnelping.rs` (**اعتبارسنجی دیتاپلین — هندشیک تنها کافی نیست**)، `lastconn.rs`.
- حالت‌های اسکنر: normal / ptech-ipv4 / ptech-ipv6 با پریفیکس‌ها و پورت‌های خاص.
- `EchEndpointRoster.kt`: پروب زندهٔ جفت‌های ECH DoH (کوئری واقعی type-65)، کش ۳۰دقیقه‌ای.
- `MtuProbe.kt`: باینری‌سرچ ICMP با `-M do` بین 1200–1500، سربار per-transport، حاشیه ایمنی، کف 1280. `MtuAutoScan.kt` هنگام اتصال.

## ۶. UI

بدون Compose؛ الگوی page-stack با FrameLayout؛ کنترل قهرمان `OrbitDialView` (867 خط Canvas)؛ تم تیره ثابت؛ فونت Vazirmatn (fa) + Noto SC؛ زبان‌ها en/fa/zh/ru.

## ۷. پروفایل‌ها و بکاپ

۴ پروفایل با key-prefixing در یک SharedPreferences؛ بکاپ JSON؛ ولت هویت WARP در TOML؛ اشتراک SHARD با پارسر vless/trojan/ss/hysteria2/wireguard در `shard/ShardImport.kt` و سازنده outbound در `shard/ShardOutboundBuilder.kt`؛ policy ریموت در `remote/policy.json`.

## ۸. بیلد و CI

ABI split + universal؛ `useLegacyPackaging` (برای exec شدن libtor/libxray)؛ امضا v1+v2+v3؛ ورک‌فلوهای build/tor-binaries/xray-binary/anytls-binary/psiphon-aar/shard-nodes-sync (کرون ۴ساعته).

## ۹. پنج قابلیت برتر قابل انتقال

1. **انتزاع TunEngine با fallback در زمان اجرا** — تمیزترین درز برای اپ چندموتوره.
2. **MTU اندازه‌گیری‌شده**: پروب per-method + اسکن خودکار هنگام اتصال.
3. **کاوش اندپوینت با اعتبارسنجی دیتاپلین** — گیت‌وی فقط بعد از عبور ترافیک واقعی معتبر است.
4. **ECH roster با پروب زنده و جانشینی fail-safe.**
5. **remote policy + استخر نود بدون ریلیز اپ** (`RemotePolicy.kt`، `ShardSubscription.kt` + sync workflows).

نکات دیگر: Foxy خالص‌Kotlin؛ ولت هویت با رجیستر از مسیر ECH؛ playbook بسته‌بندی اندروید (specialUse FGS + امضای سه‌گانه + legacyPackaging).

## ۱۰. موانع استفاده مجدد

- **AGPL-3.0 تأیید** (ریشه + core/LICENSE) — استفاده از کد Aether کل اپ را AGPL می‌کند.
- `anytls/` بالادست بدون لایسنس + وابستگی GPLv3 (sing) — «قبل از ریلیز عمومی حل شود».
- `psiphon.aar` (35MB) از fork خاص؛ `libxray.so` از fork patterniha با قابلیت‌های غیرآپستریم (finalmask و...).
- 195MB باینری prebuilt در jniLibs؛ رله هویت نیازمند سکریت بیلد (`MSN_IDENTITY_RELAY_HMAC`).
- قطعات permissive قابل استفاده آزاد: badvpn (BSD-3)، lwIP، hev (MIT)، quiche (BSD-2)، Tor (BSD-3).
