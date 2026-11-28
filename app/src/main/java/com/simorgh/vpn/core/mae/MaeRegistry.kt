package com.simorgh.vpn.core.mae

/**
 * Built-in service registry (mlmvpn MAE registry pattern, Iran-focused).
 *
 * Every entry carries a tiny, safe probe URL — one GET, a few KB max, no
 * account, no cookies. What is ACTUALLY reachable on the user's network is
 * learned at runtime by [MaeLearner]; the `likelyCensored` flag is only the
 * cold-start prior for the UI before the first probe.
 */
object MaeRegistry {

    val SERVICES: List<MaeServiceDef> = listOf(
        MaeServiceDef(
            id = "telegram",
            faName = "تلگرام",
            domains = listOf("telegram.org", "t.me", "telegram.me", "telesco.pe"),
            probeUrl = "https://core.telegram.org/"
        ),
        MaeServiceDef(
            id = "instagram",
            faName = "اینستاگرام",
            domains = listOf("instagram.com", "cdninstagram.com", "instagr.am"),
            probeUrl = "https://www.instagram.com/"
        ),
        MaeServiceDef(
            id = "whatsapp",
            faName = "واتساپ",
            domains = listOf("whatsapp.com", "wa.me", "whatsapp.net"),
            probeUrl = "https://www.whatsapp.com/"
        ),
        MaeServiceDef(
            id = "youtube",
            faName = "یوتیوب",
            domains = listOf("youtube.com", "youtu.be", "googlevideo.com", "ytimg.com", "ggpht.com"),
            probeUrl = "https://www.youtube.com/"
        ),
        MaeServiceDef(
            id = "twitter",
            faName = "توییتر / X",
            domains = listOf("twitter.com", "x.com", "twimg.com", "t.co"),
            probeUrl = "https://x.com/"
        ),
        MaeServiceDef(
            id = "discord",
            faName = "دیسکورد",
            domains = listOf("discord.com", "discordapp.com", "discord.gg", "discordmedia.com"),
            probeUrl = "https://discord.com/"
        ),
        MaeServiceDef(
            id = "reddit",
            faName = "ردیت",
            domains = listOf("reddit.com", "redd.it", "redditstatic.com"),
            probeUrl = "https://www.reddit.com/"
        ),
        MaeServiceDef(
            id = "signal",
            faName = "سیگنال",
            domains = listOf("signal.org", "signal.tube", "updates.signal.org"),
            probeUrl = "https://signal.org/"
        ),
        MaeServiceDef(
            id = "google",
            faName = "گوگل",
            domains = listOf("google.com", "gstatic.com", "googleapis.com", "googleusercontent.com"),
            probeUrl = "https://www.google.com/"
        ),
        MaeServiceDef(
            id = "playstore",
            faName = "گوگل‌پلی",
            domains = listOf("play.google.com", "android.clients.google.com"),
            probeUrl = "https://play.google.com/"
        ),
        MaeServiceDef(
            id = "netflix",
            faName = "نتفلیکس",
            domains = listOf("netflix.com", "nflxvideo.net", "nflxext.com"),
            probeUrl = "https://www.netflix.com/"
        ),
        MaeServiceDef(
            id = "spotify",
            faName = "اسپاتیفای",
            domains = listOf("spotify.com", "scdn.co", "spotifycdn.com"),
            probeUrl = "https://www.spotify.com/"
        ),
        MaeServiceDef(
            id = "twitch",
            faName = "توئیچ",
            domains = listOf("twitch.tv", "ttvnw.net", "twitchcdn.net", "jtvnw.net"),
            probeUrl = "https://www.twitch.tv/"
        ),
        MaeServiceDef(
            id = "linkedin",
            faName = "لینکدین",
            domains = listOf("linkedin.com", "licdn.com", "lnkd.in"),
            probeUrl = "https://www.linkedin.com/"
        ),
        MaeServiceDef(
            id = "pinterest",
            faName = "پینترست",
            domains = listOf("pinterest.com", "pinimg.com", "pin.it"),
            probeUrl = "https://www.pinterest.com/"
        ),
        MaeServiceDef(
            id = "medium",
            faName = "مدیوم",
            domains = listOf("medium.com", "medium.systems"),
            probeUrl = "https://medium.com/"
        ),
        MaeServiceDef(
            id = "github",
            faName = "گیت‌هاب",
            domains = listOf("github.com", "githubusercontent.com", "githubassets.com"),
            probeUrl = "https://github.com/",
            likelyCensored = false // usually reachable; throttling varies
        ),
        MaeServiceDef(
            id = "wikipedia",
            faName = "ویکی‌پدیا",
            domains = listOf("wikipedia.org", "wikimedia.org", "wikimediafoundation.org"),
            probeUrl = "https://www.wikipedia.org/",
            likelyCensored = false
        )
    )

    fun byId(id: String): MaeServiceDef? = SERVICES.firstOrNull { it.id == id }
}
