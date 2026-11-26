# گزارش تحلیل: defyxVPN و درفش

> Task 2-f | اسنپ‌شات‌ها: `elham/defyxvpn` (UnboundTechCo، MIT) + `Derafsh-Vpn-main` (پایه خودی، GPL-3.0)

## الف) defyxvpn — فقط پوسته، موتور بسته

### واقعیت باز/بسته

- **باز:** کل UI بیزینسی Flutter + چسب نیتیو (MainActivity/VpnService کاتلین، PacketTunnel سوئیفت، runner های C++ ویندوز/لینوکس).
- **بسته:** `DXcore` در همهٔ پلتفرم‌ها غایب — `libs/DXcore.aar` فقط `.gitkeep`؛ xcframework نیست؛ DLL دیده نمی‌شود. **بیلد اندروید در resolution وابستگی می‌شکند.** اپ قابل‌ساخت به‌عنوان VPN نیست؛ فقط الگو/UX.

### سطح API کانال (طراحی ارزشمند — قابل تقلید)

MethodChannel `com.defyx.vpn`: `startVPN(flowLine, pattern, …)`, `connect(mode, rules, apps)`, `getFlowLine(token)`, `decodeAndVerifyFlowline` … — هسته همه‌چیز را می‌فهمد، اپ blob مات + الگو پاس می‌دهد. **split-tunnel = ۳ payload**: `mode(off|exclude|only)` + `rules(domain:x, geoip:y)` + `apps`. EventChannel پیشرفت با پروتکل پیشوند-رشته‌ای: `Data: Config index: N` / `Config label: X` / `Token expired`.

### سه ایده UX برتر

1. **fallback چندکانفیگی با پیشرفت شفاف** — «اندیس ۲ از ۵» + برچسب متد فعال. مدل ذهنی عالی برای نردبان AUTO.
2. **split-tunnel سه‌حالته + پیکر اپ‌های نصب‌شده با آیکون واقعی + پریست‌های ~۳۰ سرویس محبوب با SVG.**
3. **بستهٔ تجربهٔ اتصال:** اسپیدتست Cloudflare، بازی داینو حین اتصال، هاپتیک/صدا، easter egg؛ آبشار تبلیغ تحریم‌آگاه (داخلی برای ایران/AdMob بقیه).

### فونت و فارسی

Vazirmatn همراه + تعویض فونت بر اساس locale + جبران سایز هدلاین فارسی؛ زبان‌ها en/fa/ru/zh.

## ب) درفش — پایه خودمان (فورک V2rayNG)

### وضعیت نسبت به آپستریم

applicationId به `com.derafsh.vpn` تغییر کرده ولی namespace هنوز `com.v2ray.ang`. تم کاویانی، شروع کار (onboarding)، منبع‌های کانفیگ داخلی (`DerafshSourceManager`)، **محدودیت نشست ۳۰دقیقه‌ای با تمدید تبلیغ جایزه‌ای** (MMKV چندپروسه‌ای)، اتصال به سریع‌ترین سرور، مسیریابی مستقیم داخلی، آبشار house→Tapsell→AdMob.

### اتصال Xray

AAR آماده (`libs/libv2ray.aar` — گوموبایل، نسخه = تگ ساب‌ماژول AndroidLibXrayLite؛ هسته Xray 26.10.31)؛ چرخه حیات در `CoreServiceManager` (god-object: کنترلر + SoftReference + broadcast + تایمر نشست). کانفیگ Xray در Kotlin دست‌ساز (`CoreConfigManager` 1,354 خط + DTO 467 خط — منبع drift با هر ارتقای Xray).

### TUN

`CoreVpnService` تونل می‌سازد → fd خام به `TProxyService` → JNI `libhev-socks5-tunnel.so` (`TProxyStartService(configPath, fd)`) → hev پمپ TUN ⇄ SOCKS5 Xray. سه مود اجرا (VPN/فقط‌پروکسی/روت).

### UI

**100% Compose Material 3** (BOM جدید)؛ MainScreen با ConnectCircleButton/glow/rings + SessionProgressRing؛ ۲۲ Activity از آپستریم حفظ شده (ادیتورهای ۱۰ پروتکل، WebDav، Tasker و…) — **تودهٔ میراث برای اپ تک‌ضربه‌ای**. فارسی کامل ولی **بدون فونت فارسی همراه**.

### بیلد

NDK 30، JDK 21، `compile-hevtun.sh` (۴ ABI، 16KiB صفحه)؛ AAR از ریلیز تگ ساب‌ماژول؛ flavor های fdroid/playstore + ABI split؛ **ریسک بهداشتی: پسورد کی‌استور fallback در سورس**؛ تست 1,928 LOC (JUnit/Mockito) به‌عنوان دروازهٔ اعتبارسنجی.

### پنج درد معماری که سیمرغ باید حل کند

1. **God-object ها و بدون DI** (CoreServiceManager/MainViewModel/MmkvManager) → Hilt + ViewModel های کوچک + `VpnEngine` per موتور.
2. **IPC پیام-رشته‌ای و MMKV به‌عنوان باس** (race های مستند خود کد) → یک `ConnectionRepository` تک‌منبع حقیقت (StateFlow).
3. **تودهٔ میراث آپستریم** → تفکیک ماژولی `core/engine-xray`, `core/tun`, `data`, `feature:*` — سطح اکسپرت فقط در ماژول خودش کامپایل شود.
4. **تولید کانفیگ قفل‌شده به داخل هسته** → `ConfigBuilder` interface per موتور + پایپ‌لاین artifact قفل‌وتولیدپذیر.
5. **گره‌خورده بودن تبلیغ/نشست/رضایت/آپدیت با منطق اتصال** → ماژول‌های ایزوله با اینترفیس صریح (امکان flavor پولی/بی‌تبلیغ بدون لمس اتصال).

## جمع‌بندی بین‌پروژه‌ای

- **defyxvpn شکل محصول را ثابت می‌کند:** قرارداد کانال مات + پروتکل رویداد پیشرفت + split-tunnel سه‌payload — قراردادی موتور-مستقل که ارزش کپی دارد (نه کدش).
- **درفش زیرساخت موتور کارا می‌دهد:** Xray گوموبایل + hev TUN + استک فارسی/RTL/تبلیغات ایرانی.
- **مسیر سریع:** تفکیک درفش به `engine-xray` + `tun-hev` + UI، با قرارداد کانال/پیشرفت/split-tunnel الگو‌گرفته از defyx، فونت Vazirmatn از همان‌جا، و مکانیک نشست/تبلیغات به‌عنوان ماژول ایزوله.
