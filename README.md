<div align="center">

# سیمرغ | SimorghVPN

**کلاینت فیلترشکن فارسی‌محور — یک اپ، همهٔ موتورها**

_Persian-first, multi-engine, anti-censorship VPN client for Android._

[![Status](https://img.shields.io/badge/Status-Phase%200%20·%20Architecture-orange)](docs/ARCHITECTURE.md) [![Platform](https://img.shields.io/badge/Platform-Android-3ddc84?logo=android&logoColor=white)]() [![License](https://img.shields.io/badge/License-AGPL--3.0-blue)](LICENSE)

</div>

---

## سیمرغ چیست؟

در منطق‌الطیر عطار، سی پرنده به جست‌وجوی شاه‌مرغ برمی‌خیزند و درمی‌یابند که سیمرغ کسی جز خودشان نیست — **قدرت آزادی در خود ماست.** سیمرغ‌وی‌پی‌ان هم همین است: یک اپ واحد که بهترینِ هفت پروژهٔ ضدسانسور متن‌باز را زیر یک قرارداد جمع کرده است.

```
سی + مرغ = سی پرنده که با هم، قله‌ی قاف را فتح می‌کنند
```

کاربر فقط دکمهٔ اتصال را می‌زند. سیاست‌گذار سیمرغ به‌صورت خودکار از میان موتورها (دی‌سینک DPI، Xray/VLESS+Reality، WARP، MASQUE، Psiphon، …) بهترین مسیر شبکهٔ فعلی را پیدا می‌کند، سلامتش را با **پروب دادهٔ واقعی** تأیید می‌کند و در صورت خرابی بی‌درنگ مسیر بعدی را برمی‌دارد.

| | |
|---|---|
| 🚀 **یک‌ضربه‌ای** | بدون کانفیگ هم کار می‌کند (صفر-کانفیگ اول) |
| 🇮🇷 **فارسی‌محور** | رابط کامل فارسی/RTL، فونت وزیرمتن، Smart-Iran |
| 🛡️ **اتصال صادقانه** | «متصل» = دادهٔ واقعی از داخل تونل رد شده |
| 🎭 **ضدسانسور** | دی‌سینک DPI، SNI-mask، DNS-obfs، ECH، uTLS/fragmentation |
| 🆓 **آزاد** | AGPL-3.0 — برای همیشه متن‌باز |

## مستندات

- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — سند یکپارچه‌سازی: تصمیم‌ها، معماری لایه‌ای، قرارداد Engine API، فازبندی
- **[docs/research/](docs/research/)** — ۶ گزارش تحلیل عمیق پروژه‌های مرجع
- **[elham/](elham/)** — اسنپ‌شات پروژه‌های مرجع (فقط مطالعه)

## نقشه راه

| فاز | هدف | وضعیت |
|-----|-----|--------|
| ۰ | اسکلت چندماژوله، قرارداد Engine API، CI | 🟡 در جریان |
| ۱ | MVP: موتور Xray (مهاجرت درفش) + TUN/hev + UI یک‌ضربه + اشتراک | ⚪ |
| ۲ | صفر-کانفیگ: موتور Desync (پورت Shroudly) + نردبان AUTO | ⚪ |
| ۳ | موتورهای آزاد: WARP/MASQUE (Aether) + Psiphon + failover | ⚪ |
| ۴ | هوشمند: مسیریابی تطبیقی per-app (MAE) + Smart-Iran + cfnet | ⚪ |
| ۵ | سخت‌افشاری: MTU-scan + SNI-pool ایران + remote policy امضاشده | ⚪ |

## معماری در یک نگاه

```
UI (Compose, فارسی/RTL)
  └── SimorghCore — سیاست‌گذار: نردبان AUTO + پروب صادقانه + failover
        └── Engine API — Xray | Desync | WARP/MASQUE | Psiphon | Tor
              └── TunEngine — hev → tun2proxy → tun2socks (با fallback)
                    └── Data — پروفایل، اشتراک، remote policy امضاشده
```

## مشارکت

- لایسنس: **AGPL-3.0** (به دلیل استفاده از کد AGPL/GPL بالادست‌ها — جزئیات در `NOTICE`)
- قبل از شروع، `docs/ARCHITECTURE.md` را بخوانید
- Issue های فازبندی‌شده در بخش Issues ریپو

---

<div align="center">

**«چون این همه راه آمدیم، اینجا خانه‌ی ماست.»**

</div>
