<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://via.placeholder.com/150x150/0d1117/22d3ee?text=S" width="120">
  <img src="https://via.placeholder.com/150x150/f8fafc/0f766e?text=S" width="120" alt="SimorghVPN logo">
</picture>

# سیمرغ | SimorghVPN

**کلاینت فیلترشکن فارسی‌محور — سریع، امن و دور از سانسور**

_Persian-first VPN client — fast, secure, uncensorable._

[![Status](https://img.shields.io/badge/Status-Planning/Pre--alpha-orange)]() [![Platform](https://img.shields.io/badge/Platform-Android-3ddc84?logo=android&logoColor=white)]() [![License](https://img.shields.io/badge/License-TBD-lightgrey)]()

</div>

---

## چرا «سیمرغ»؟

در منطق‌الطیر عطار، سی پرنده به جست‌وجوی شاه‌مرغ برمی‌خیزند؛ سیاه‌چاله‌ها و هفت وادی را می‌گذرنند و در پایان درمی‌یابند که سیمرغ کسی جز خودشان نیست — **قدرت آزادی در خود ماست.**

سیمرغ‌وی‌پی‌ان هم همین است: شبکه‌ای از نودها (مرغان) که با هم پرواز می‌کنند و از دیوار فیلترینگ فراتر می‌روند.

```
سی + مرغ = سی پرنده که با هم، قله‌ی قاف را فتح می‌کنند
```

## چشم‌انداز | Vision

ساختن کلاینتی که:

- 🚀 **یک‌ضربه‌ای** — دکمه‌ی اتصال، بدون کانفیگ دردسرساز
- 🇮🇷 **فارسی‌محور** — رابط کامل فارسی، راست‌به‌چپ، مناسب کاربر ایرانی
- 🛡️ **امن و مدرن** — رمزنگاری استاندارد، بدون لاگ، بدون نشت DNS
- 🎭 **ضد سانسور** — تروجان، VLESS+Reality، Hysteria2 و پروتکل‌های مقاوم در برابر DPI
- 🔄 **مقاوم در قطعی** — اتصال مجدد خودکار، فیلتر خودکار کانفیگ‌های مرده
- 🆓 **آزاد** — متن‌باز (در حال تصمیم‌گیری درباره نوع لایسنس)

## نقشه راه | Roadmap

| فاز | هدف | وضعیت |
|-----|-----|--------|
| ۰ | معماری، انتخاب هسته (Xray / sing-box) و طراحی UI | 🟡 در جریان |
| ۱ | کلاینت اندروید پایه — اتصال به کانفیگ VLESS/VMess/Trojan | ⚪ شروع نشده |
| ۲ | اشتراک‌گذاری کانفیگ (subscription) و QR | ⚪ شروع نشده |
| ۳ | تست خودکار کانفیگ‌ها و انتخاب بهترین نود | ⚪ شروع نشده |
| ۴ | رابط فارسی کامل + حالت شب + ویجت | ⚪ شروع نشده |
| ۵ | انتشار نسخه اولیه (APK + فروشگاه‌های جایگزین) | ⚪ شروع نشده |

## معماری پیشنهادی | Proposed Stack

- **پلتفرم اول:** اندروید (Kotlin)
- **هسته پروکسی:** [Xray-core](https://github.com/XTLS/Xray-core) یا [sing-box](https://github.com/SagerNet/sing-box) — تحت بررسی
- **تونل:** [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) برای حالت VPN
- **مرجع مطالعاتی:** [Derafsh-VPN](https://github.com/Ali-Razmjoo/Derafsh-VPN)

## ساختار پروژه (پیش‌نویس)

```
SimorghVPN/
├── app/                  # کلاینت اندروید (Kotlin)
├── core/                 # بایندینگ هسته پروکسی (Xray/sing-box)
├── docs/                 # مستندات فارسی و انگلیسی
└── tools/                # اسکریپت‌های بیلد و تست
```

---

<div align="center">

**«چون این همه راه آمدیم، اینجا خانه‌ی ماست.»**

⭐ اگر با این ایده هم‌نظر هستید، استار یادتون نره.

</div>
