# 📁 elham — کلاینت‌های مرجع | Reference VPN Clients

اسنپ‌شات سطحی (shallow snapshot) از پروژه‌های متن‌باز مرجع برای مطالعه در توسعهٔ **سیمرغ**.

> نحوهٔ تهیه: `git clone --depth 1` در تاریخ تهیهٔ این پوشه؛ پوشه‌های `.git` حذف شده‌اند تا تاریخچهٔ سنگین منتقل نشود.
> کپی هر پروژه صرفاً برای مطالعه است؛ توسعهٔ سیمرغ از بالادست (upstream) جدا خواهد بود.

| پوشه | بالادست | لایسنس | توضیح |
|------|---------|--------|-------|
| `msn-guard/` | [mbm110/MSN-GUARD](https://github.com/mbm110/MSN-GUARD) | AGPL-3.0 | VPN سراسری اندروید برای شبکه‌های سخت: WireGuard, MASQUE, WARP-on-WARP, Psiphon, Tor, Foxy, SHARD — با هستهٔ Rust، کشف اندپوینت و اسکن MTU |
| `openrung/` | [openrung/openrung](https://github.com/openrung/openrung) | GPL-3.0 | مونوریپوی اصلی OpenRung: هستهٔ Go (connectcore)، بروکر API، کلاینت دسکتاپ، داکیومنت |
| `openrung-mobile-app/` | [openrung/openrung-mobile-app](https://github.com/openrung/openrung-mobile-app) | GPL-3.0 | کلاینت موبایل OpenRung — React Native + sing-box |
| `mlmvpn-android/` | [mlmvpn/mlmvpn_android](https://github.com/mlmvpn/mlmvpn_android) | GPL-3.0 | VPN چندموتورهٔ اندروید: Xray, WARP, Psiphon, Tor, Geph, OpenVPN, MASQUE/AmneziaWG + موتور تطبیقی مسیریابی هر اپ — Kotlin + Jetpack Compose |
| `nova-android/` | [confeden/Nova-Android](https://github.com/confeden/Nova-Android) | GPL-3.0 | Nova (`com.brent.nova`) — کلاینت ضدسانسور روسی برای اندروید ۶+ — «آنیریگیلاسیا بلاکیرووک» |
| `shroudly/` | [umutxyp/Shroudly](https://github.com/umutxyp/Shroudly) | MIT | اپ ضدسانسور چندسکویی (Win/macOS/Linux/Android/iOS/TV) — بدون سرور VPN، بدون حساب کاربری |
| `defyxvpn/` | [UnboundTechCo/defyxVPN](https://github.com/UnboundTechCo/defyxVPN) | MIT | کلاینت VPN مدرن با Flutter + هستهٔ اختصاصی DXcore |

## نکته‌ها

- فایل‌های Git LFS پروژهٔ mlmvpn (کتابخانه‌های `.so`/`.dat`) در اسنپ‌شات حضور ندارند؛ در صورت نیاز از بالادست بگیرید.
- برای دیدن تاریخچهٔ کامل هر پروژه به مخزن بالادست مراجعه کنید.
