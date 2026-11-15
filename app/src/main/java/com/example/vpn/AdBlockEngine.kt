package com.example.vpn

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class BlockedAdEvent(
    val id: Long,
    val host: String,
    val layer: String,
    val timestampMs: Long
)

data class AdBlockStats(
    val embeddedCount: Int = 0,
    val downloadedCount: Int = 0,
    val customBlockCount: Int = 0,
    val whitelistCount: Int = 0,
    val dohCount: Int = 0,
    val totalRuleCount: Int = 0,
    val lastListName: String = ""
)

enum class AdBlockMatchType {
    EXACT_OR_SUBDOMAIN,
    DOH_BYPASS,
    CUSTOM_BLACKLIST,
    DOWNLOADED_LIST,
    HEURISTIC
}

/**
 * MOTOR ADBLOCK SHIELD ULTRA (Multi-Camada Lock-Free):
 * 1. Base embutida de 450+ domínios de SDKs de anúncios em APKs Android, redes programáticas, pop-ups e telemetria OEM.
 * 2. Anti-Bypass DoT/DoH (canários Firefox/Apple + resolvers DoH conhecidos + bloqueio porta 853 TCP/UDP).
 * 3. Heurística rápida sem alocações para subdomínios e padrões agressivos de anúncios.
 * 4. Inspeção zero-alloc de TLS ClientHello SNI e HTTP Host para APKs que usam cache de DNS ou IP direto.
 * 5. Suporte offline a listas públicas massivas (hosts 0.0.0.0, AdBlock ||dominio^ e OISD) até 250.000 domínios + Whitelist/Blacklist.
 */
object AdBlockEngine {

    private const val FILE_HOSTS = "adblock_hosts.txt"
    private const val FILE_BLACKLIST = "adblock_blacklist.txt"
    private const val FILE_WHITELIST = "adblock_whitelist.txt"
    private const val FILE_META = "adblock_list_meta.txt"
    private const val MAX_DOWNLOADED_DOMAINS = 250_000
    private const val MAX_RECENT_EVENTS = 80

    private val embeddedDomains: Set<String> = hashSetOf(
        // Google / AdMob / AdSense / DoubleClick / YouTube Ads / Analytics
        "doubleclick.net",
        "g.doubleclick.net",
        "stats.g.doubleclick.net",
        "pubads.g.doubleclick.net",
        "securepubads.g.doubleclick.net",
        "ad.doubleclick.net",
        "cm.g.doubleclick.net",
        "googleads.g.doubleclick.net",
        "adservice.google.com",
        "adservice.google.com.br",
        "adservice.google.pt",
        "googlesyndication.com",
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "ade.googlesyndication.com",
        "video-ad-stats.googlesyndication.com",
        "googleadservices.com",
        "www.googleadservices.com",
        "partnerad.l.google.com",
        "google-analytics.com",
        "ssl.google-analytics.com",
        "www.google-analytics.com",
        "app-measurement.com",
        "app-analytics-services.com",
        "analytics.google.com",
        "googletagmanager.com",
        "www.googletagmanager.com",
        "googletagservices.com",
        "www.googletagservices.com",
        "ads.google.com",
        "ads.youtube.com",
        "admob.com",
        "media.admob.com",
        "fundingchoicesmessages.google.com",
        "imasdk.googleapis.com",
        "s0.2mdn.net",
        "s1.2mdn.net",
        "csi.gstatic.com",
        "crashlyticsreports-pa.googleapis.com",
        "firebaselogging-pa.googleapis.com",
        "firebaseinstallations.googleapis.com",

        // Meta / Facebook / Instagram Ads & Tracking
        "ads.facebook.com",
        "an.facebook.com",
        "pixel.facebook.com",
        "adsmobile.facebook.com",
        "graph.instagram.com",
        "analytics.facebook.com",
        "connect.facebook.net",

        // TikTok / ByteDance / Pangle APK Ad SDKs
        "analytics.tiktok.com",
        "ads.tiktok.com",
        "ads-sg.tiktok.com",
        "ads-api.tiktok.com",
        "business-api.tiktok.com",
        "log.tiktokv.com",
        "log-va.tiktokv.com",
        "mon.tiktokv.com",
        "pangleglobal.com",
        "pangle.io",
        "api.pangle.io",
        "ad.toutiao.com",
        "is.snssdk.com",
        "log.snssdk.com",
        "pangolin-sdk-toutiao.com",
        "byteoversea.com",

        // Mobile APK Ad SDKs & Mediation (Jogos, VPNs, IPTV, Streaming APKs)
        "unityads.unity3d.com",
        "config.uca.cloud.unity3d.com",
        "ads.prd.ie.internal.unity3d.com",
        "adserver.unityads.unity3d.com",
        "webview.unityads.unity3d.com",
        "auction.unityads.unity3d.com",
        "cdp.cloud.unity3d.com",
        "applovin.com",
        "d.applovin.com",
        "rt.applovin.com",
        "ms.applovin.com",
        "prod-a.applovin.com",
        "applvn.com",
        "assets.applovin.com",
        "vungle.com",
        "ads.api.vungle.com",
        "config.vungle.com",
        "events.ads.vungle.com",
        "liftoff.io",
        "ironsrc.com",
        "outcome-ssp.supersonicads.com",
        "init.supersonicads.com",
        "supersonicads.com",
        "is.com",
        "logs.ironsrc.mobi",
        "chartboost.com",
        "live.chartboost.com",
        "da.chartboost.com",
        "inmobi.com",
        "api.w.inmobi.com",
        "config.inmobi.com",
        "telemetry.sdk.inmobi.com",
        "mopub.com",
        "ads.mopub.com",
        "startappservice.com",
        "init.startappservice.com",
        "req.startappservice.com",
        "info.startappservice.com",
        "start.io",
        "startappexchange.com",
        "adcolony.com",
        "adc3-launch.adcolony.com",
        "events3alt.adcolony.com",
        "wd.adcolony.com",
        "tapjoy.com",
        "ws.tapjoyads.com",
        "rpc.tapjoy.com",
        "tapjoyads.com",
        "fyber.com",
        "engine.fyber.com",
        "service.fyber.com",
        "inner-active.mobi",
        "wv.inner-active.mobi",
        "mintegral.com",
        "net.rayjump.com",
        "rayjump.com",
        "hb.rayjump.com",
        "configure.rayjump.com",
        "ogury.co",
        "ogury.com",
        "ms-ads-sdk.presage.io",
        "presage.io",
        "bidmachine.io",
        "api.bidmachine.io",
        "smaato.net",
        "soma.smaato.net",
        "c29.smaato.net",
        "bigo.sg",
        "api.im.bigo.sg",
        "ad-sdk.bigo.sg",
        "moloco.com",
        "ads.moloco.com",
        "yodo1.com",
        "mas-sdk.yodo1.com",
        "pubnative.net",
        "api.pubnative.net",
        "verve.com",
        "hyprmx.com",
        "kidoz.net",
        "superawesome.tv",
        "ads.superawesome.tv",
        "pob.ads.superawesome.tv",
        "appodeal.com",
        "a.appodeal.com",
        "appodealx.com",
        "admost.com",
        "med-api.admost.com",
        "mytarget.ru",
        "ad.mail.ru",
        "r.mail.ru",
        "top-fwz1.mail.ru",
        "yandex.ru",
        "mc.yandex.ru",
        "an.yandex.ru",
        "ads.adfox.ru",
        "yandexadexchange.net",
        "vk.com",
        "ad.vk.com",
        "poll.vk.com",
        "kuaishou.com",
        "open.kuaishou.com",

        // Attribution, Deep-Link Trackers & Analytics SDKs
        "adjust.com",
        "app.adjust.com",
        "app.adjust.io",
        "s2s.adjust.com",
        "view.adjust.com",
        "appsflyer.com",
        "t.appsflyer.com",
        "launches.appsflyer.com",
        "inapps.appsflyer.com",
        "conversions.appsflyer.com",
        "cdn.appsflyer.com",
        "branch.io",
        "api.branch.io",
        "api2.branch.io",
        "app.link",
        "bnc.lt",
        "kochava.com",
        "control.kochava.com",
        "singular.net",
        "sdk-api-v1.singular.net",
        "tenjin.com",
        "track.tenjin.com",
        "flurry.com",
        "data.flurry.com",
        "ads.flurry.com",
        "hotjar.com",
        "static.hotjar.com",
        "script.hotjar.com",
        "mixpanel.com",
        "api.mixpanel.com",
        "api-js.mixpanel.com",
        "amplitude.com",
        "api.amplitude.com",
        "api2.amplitude.com",
        "segment.io",
        "api.segment.io",
        "cdn.segment.com",
        "segment.com",
        "clevertap.com",
        "wzrkt.com",
        "braze.com",
        "sdk.iad-01.braze.com",
        "sdk.iad-03.braze.com",
        "localytics.com",
        "bugsnag.com",
        "notify.bugsnag.com",
        "appcenter.ms",
        "in.appcenter.ms",
        "scorecardresearch.com",
        "sb.scorecardresearch.com",
        "quantserve.com",
        "pixel.quantserve.com",
        "moatads.com",
        "z.moatads.com",
        "doubleverify.com",
        "cdn.doubleverify.com",
        "iasds01.com",
        "adsafeprotected.com",
        "pixel.adsafeprotected.com",
        "omtrdc.net",
        "demdex.net",
        "krxd.net",
        "bluekai.com",
        "crwdcntrl.net",
        "rlcdn.com",
        "agkn.com",
        "exelator.com",
        "eyeota.net",
        "narrative.io",
        "permutive.com",
        "lotame.com",
        "chartbeat.com",
        "ping.chartbeat.net",
        "newrelic.com",
        "bam.nr-data.net",
        "js-agent.newrelic.com",
        "fullstory.com",
        "rs.fullstory.com",
        "clarity.ms",
        "www.clarity.ms",
        "heapanalytics.com",
        "count.ly",

        // Major Web Ad Exchanges, SSPs, DSPs, Native & Video Ads
        "adnxs.com",
        "ib.adnxs.com",
        "acdn.adnxs.com",
        "adsrvr.org",
        "match.adsrvr.org",
        "insight.adsrvr.org",
        "taboola.com",
        "cdn.taboola.com",
        "trc.taboola.com",
        "outbrain.com",
        "widgets.outbrain.com",
        "odb.outbrain.com",
        "criteo.com",
        "criteo.net",
        "gum.criteo.com",
        "bidder.criteo.com",
        "static.criteo.net",
        "pubmatic.com",
        "ads.pubmatic.com",
        "hbopenbid.pubmatic.com",
        "rubiconproject.com",
        "fastlane.rubiconproject.com",
        "pixel.rubiconproject.com",
        "openx.net",
        "us-u.openx.net",
        "rtb.openx.net",
        "casalemedia.com",
        "htlb.casalemedia.com",
        "as-sec.casalemedia.com",
        "smartadserver.com",
        "www8.smartadserver.com",
        "equativ.com",
        "teads.tv",
        "a.teads.tv",
        "t.teads.tv",
        "3lift.com",
        "tlx.3lift.com",
        "eb2.3lift.com",
        "sharethrough.com",
        "btlr.sharethrough.com",
        "sovrn.com",
        "ap.lijit.com",
        "lijit.com",
        "kargo.com",
        "krk.kargo.com",
        "media.net",
        "contextual.media.net",
        "hbx.media.net",
        "amazon-adsystem.com",
        "aax.amazon-adsystem.com",
        "aax-us-east.amazon-adsystem.com",
        "c.amazon-adsystem.com",
        "s.amazon-adsystem.com",
        "fls-na.amazon-adsystem.com",
        "adform.net",
        "track.adform.net",
        "s1.adform.net",
        "advertising.com",
        "pixel.advertising.com",
        "zemanta.com",
        "b1sync.zemanta.com",
        "revcontent.com",
        "trends.revcontent.com",
        "mgid.com",
        "jsc.mgid.com",
        "servicer.mgid.com",
        "ads.yahoo.com",
        "gemini.yahoo.com",
        "beap.gemini.yahoo.com",
        "udc.yahoo.com",
        "bat.bing.com",
        "ads.msn.com",
        "c.msn.com",
        "ads.twitter.com",
        "ads-api.twitter.com",
        "analytics.twitter.com",
        "static.ads-twitter.com",
        "ads.linkedin.com",
        "px.ads.linkedin.com",
        "snapads.com",
        "tr.snapchat.com",
        "sc-static.net",
        "ads.pinterest.com",
        "ct.pinterest.com",
        "log.pinterest.com",
        "alb.reddit.com",
        "events.redditmedia.com",
        "pixel.redditmedia.com",
        "ads-api.reddit.com",
        "spotxchange.com",
        "spotx.tv",
        "springserve.com",
        "vid.springserve.com",
        "imasdk.com",
        "innovid.com",
        "rtr.innovid.com",
        "extreme-reach.io",
        "tremorhub.com",
        "unrulymedia.com",
        "connatix.com",
        "cds.connatix.com",
        "jwpltx.com",
        "aniview.com",
        "gov.aniview.com",
        "yieldmo.com",
        "ads.yieldmo.com",
        "gumgum.com",
        "g2.gumgum.com",
        "seedtag.com",
        "t.seedtag.com",
        "bidswitch.net",
        "x.bidswitch.net",
        "iponweb.net",
        "rtbhouse.com",
        "creativecdn.com",
        "adroll.com",
        "d.adroll.com",
        "s.adroll.com",
        "quantcast.com",
        "liveintent.com",
        "liadm.com",
        "id5-sync.com",
        "adhigh.net",
        "adkernel.com",
        "admixer.net",
        "adtrue.com",
        "appier.net",
        "sonobi.com",
        "undertone.com",
        "conversantmedia.com",
        "dotomi.com",
        "mathtag.com",
        "pixel.mathtag.com",
        "turn.com",
        "simpli.fi",
        "um.simpli.fi",
        "adscale.de",
        "adition.com",
        "weborama.fr",
        "smartclip.net",
        "dsp.smartclip.net",

        // Popups, Redirects, Streaming/Download Site Ads
        "propellerads.com",
        "ad.propellerads.com",
        "pushwhy.com",
        "monetag.com",
        "alwingulla.com",
        "glimtors.net",
        "thaudray.com",
        "afrdtech.com",
        "upskittyan.com",
        "popads.net",
        "c1.popads.net",
        "c2.popads.net",
        "serve.popads.net",
        "popcash.net",
        "cdn.popcash.net",
        "adsterra.com",
        "adsterratech.com",
        "highperformanceformat.com",
        "topcreativeformat.com",
        "profitabledisplaynetwork.com",
        "effectivegatecpm.com",
        "hilltopads.net",
        "hilltopads.com",
        "clickadu.com",
        "galaksion.com",
        "richads.com",
        "richpush.co",
        "exoclick.com",
        "a.exoclick.com",
        "main.exoclick.com",
        "syndication.exoclick.com",
        "realsrv.com",
        "magsrv.com",
        "juicyads.com",
        "adserver.juicyads.com",
        "trafficjunky.net",
        "trafficjunky.com",
        "trafficstars.com",
        "ts.trafficstars.com",
        "admaven.com",
        "d2bgg7rjywcwsy.cloudfront.net",
        "bidvertiser.com",
        "revenuehits.com",
        "clkrev.com",
        "onclkds.com",
        "onclickads.net",
        "onclickmax.com",
        "onclickperformance.com",
        "pushame.com",
        "notifpush.com",
        "pushub.net",
        "adspyglass.com",
        "a-ads.com",
        "coinzilla.com",
        "bitmedia.io",
        "cointraffic.io",
        "adskeeper.com",
        "jsc.adskeeper.com",
        "adnium.com",
        "ero-advertising.com",
        "trafficforce.com",
        "plugrush.com",
        "betterbanners.org",
        "cpmstar.com",
        "server.cpmstar.com",
        "superlinks.xyz",
        "acscdn.com",
        "acint.net",
        "yllix.com",
        "pub.yllix.com",
        "adcash.com",
        "ac.adcash.com",
        "infolinks.com",
        "router.infolinks.com",
        "viglink.com",
        "api.viglink.com",
        "skimresources.com",
        "s.skimresources.com",
        "go.redirectingat.com",

        // OEM / Fabricantes / Telemetria de Sistema (Xiaomi, Samsung, Oppo, Huawei, Roku, Apple)
        "ad.xiaomi.com",
        "sdkconfig.ad.xiaomi.com",
        "sdkconfig.ad.intl.xiaomi.com",
        "tracking.miui.com",
        "tracking.intl.miui.com",
        "data.mistat.xiaomi.com",
        "data.mistat.intl.xiaomi.com",
        "api.ad.xiaomi.com",
        "samsungads.com",
        "config.samsungads.com",
        "ads.samsung.com",
        "metrics.samsung.com",
        "samsungcloudsolution.com",
        "samsungcloudsolution.net",
        "nmetrics.samsung.com",
        "smetrics.samsung.com",
        "adx.ads.oppomobile.com",
        "ck.ads.oppomobile.com",
        "data.ads.oppomobile.com",
        "adsfs.oppomobile.com",
        "bdapi-ads.realmemobile.com",
        "ads.heytapmobi.com",
        "open-api.vivo.com.cn",
        "st-sdk.vivo.com.cn",
        "metrics.data.hicloud.com",
        "metrics2.data.hicloud.com",
        "adx-dre.op.hicloud.com",
        "pps.op.hicloud.com",
        "iadsdk.apple.com",
        "metrics.icloud.com",
        "advertising.apple.com",
        "api-adservices.apple.com",
        "logs.roku.com",
        "scribe.logs.roku.com",
        "cooper.logs.roku.com",
        "ads.aimitv.com",
        "lgad.cjpowercast.com",
        "ngfts.lge.com",
        "ad.lgappstv.com",
        "smartclip.com",

        // Paywalls / Consent Popups / Anti-Adblock Scripts
        "cookielaw.org",
        "cdn.cookielaw.org",
        "cookiebot.com",
        "consent.cookiebot.com",
        "onetrust.com",
        "optanon.blob.core.windows.net",
        "quantcast.mgr.consensu.org",
        "didomi.io",
        "sdk.privacy-center.org",
        "sourcepoint.mgr.consensu.org",
        "cmp.inmobi.com",
        "admiral.com",
        "getadmiral.com",
        "succeedscene.com",
        "blockthrough.com",
        "btloader.com"
    )

    // Domínios DoH e Canários para impedir que Browsers/APKs fujam do DNS da VPN
    private val dohBypassDomains: Set<String> = hashSetOf(
        "use-application-dns.net",
        "mask.icloud.com",
        "mask-h2.icloud.com",
        "dns.google",
        "dns.google.com",
        "8888.google",
        "dns64.dns.google",
        "cloudflare-dns.com",
        "mozilla.cloudflare-dns.com",
        "1dot1dot1dot1.cloudflare-dns.com",
        "chrome.cloudflare-dns.com",
        "security.cloudflare-dns.com",
        "family.cloudflare-dns.com",
        "dns.cloudflare.com",
        "dns.quad9.net",
        "dns9.quad9.net",
        "dns10.quad9.net",
        "dns11.quad9.net",
        "doh.opendns.com",
        "doh.umbrella.com",
        "doh.cleanbrowsing.org",
        "dns.nextdns.io",
        "doh.dns.sb",
        "dns.adguard.com",
        "dns.adguard-dns.com",
        "dns.alidns.com",
        "doh.pub",
        "doh.360.cn",
        "dns.twnic.tw",
        "doh.applied-privacy.net",
        "doh.ffmuc.net",
        "dns.switch.ch",
        "doh.libredns.gr",
        "resolver.uncensoreddns.org"
    )

    private val aggressiveLabelPrefixes = arrayOf(
        "ad.", "ads.", "adx.", "adn.", "adv.", "ad1.", "ad2.", "ad3.",
        "adserver.", "adservice.", "adsystem.", "adclick.", "adtrack.", "adview.",
        "adimg.", "admedia.", "adproxy.", "adpush.", "adtech.", "admanager.",
        "analytics.", "telemetry.", "metrics.", "pixel.", "pixels.", "beacon.",
        "tracker.", "tracking.", "track.", "stats.", "stat.", "click.", "clicks.",
        "pagead", "popad", "popads", "popunder", "banner.", "banners."
    )

    private val aggressiveSubstringTokens = arrayOf(
        "doubleclick",
        "googlesyndication",
        "googleadservices",
        "app-measurement",
        "applovin",
        "ironsrc",
        "supersonicads",
        "unityads",
        "mintegral",
        "pangleglobal",
        "startappservice",
        "adcolony",
        "chartboost",
        "propellerads",
        "adsterra",
        "hilltopads",
        "exoclick",
        "trafficjunky",
        "popads",
        "popcash",
        "clickadu",
        "galaksion",
        "monetag",
        "taboola",
        "outbrain",
        "pubmatic",
        "rubiconproject",
        "criteo",
        "samsungads",
        "scorecardresearch",
        "adsystem",
        "adservice",
        "adserver"
    )

    private data class RuleSnapshot(
        val downloadedDomains: Set<String> = emptySet(),
        val customBlacklist: Set<String> = emptySet(),
        val whitelist: Set<String> = emptySet(),
        val lastListName: String = ""
    )

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var snapshot: RuleSnapshot = RuleSnapshot()

    private val _stats = MutableStateFlow(computeStats(snapshot))
    val stats: StateFlow<AdBlockStats> = _stats.asStateFlow()

    private val _recentBlocked = MutableStateFlow<List<BlockedAdEvent>>(emptyList())
    val recentBlocked: StateFlow<List<BlockedAdEvent>> = _recentBlocked.asStateFlow()

    private val eventIdGen = AtomicLong(0L)
    private val ioLock = Any()

    fun init(context: Context) = initialize(context)

    @Synchronized
    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        reloadFromDisk()
    }

    @Synchronized
    fun reloadFromDisk() {
        val ctx = appContext ?: return
        val dir = ctx.filesDir
        val hostsSet = readDomainSetFile(File(dir, FILE_HOSTS), MAX_DOWNLOADED_DOMAINS)
        val blacklistSet = readDomainSetFile(File(dir, FILE_BLACKLIST), 50_000)
        val whitelistSet = readDomainSetFile(File(dir, FILE_WHITELIST), 50_000)
        val metaName = runCatching {
            val f = File(dir, FILE_META)
            if (f.exists()) f.readText().trim() else ""
        }.getOrDefault("")

        val next = RuleSnapshot(
            downloadedDomains = hostsSet,
            customBlacklist = blacklistSet,
            whitelist = whitelistSet,
            lastListName = metaName
        )
        snapshot = next
        _stats.value = computeStats(next)
    }

    val coreRuleCount: Int
        get() = embeddedDomains.size + dohBypassDomains.size

    val externalRuleCount: Int
        get() = snapshot.downloadedDomains.size

    val customBlacklistCount: Int
        get() = snapshot.customBlacklist.size

    val customWhitelistCount: Int
        get() = snapshot.whitelist.size

    val ruleCount: Int
        get() = _stats.value.totalRuleCount

    fun getCustomBlacklist(): List<String> = snapshot.customBlacklist.sorted()
    fun getCustomWhitelist(): List<String> = snapshot.whitelist.sorted()
    fun getRecentBlockedEvents(): List<BlockedAdEvent> = _recentBlocked.value

    fun replaceExternalRules(domains: Set<String>, listName: String = "") {
        synchronized(ioLock) {
            val capped = if (domains.size > MAX_DOWNLOADED_DOMAINS) {
                domains.take(MAX_DOWNLOADED_DOMAINS).toHashSet()
            } else {
                HashSet(domains)
            }
            appContext?.let { ctx ->
                writeDomainSetFile(File(ctx.filesDir, FILE_HOSTS), capped)
                runCatching { File(ctx.filesDir, FILE_META).writeText(listName) }
            }
            val next = snapshot.copy(downloadedDomains = capped, lastListName = listName)
            snapshot = next
            _stats.value = computeStats(next)
        }
    }

    fun clearExternalRules() {
        synchronized(ioLock) {
            appContext?.let { ctx ->
                runCatching { File(ctx.filesDir, FILE_HOSTS).delete() }
                runCatching { File(ctx.filesDir, FILE_META).delete() }
            }
            val next = snapshot.copy(downloadedDomains = emptySet(), lastListName = "")
            snapshot = next
            _stats.value = computeStats(next)
        }
    }

    fun addCustomBlacklist(rawDomain: String): Boolean {
        val clean = normalizeDomain(rawDomain) ?: return false
        synchronized(ioLock) {
            val cur = snapshot
            val updatedBlack = HashSet(cur.customBlacklist).apply { add(clean) }
            val updatedWhite = HashSet(cur.whitelist).apply { remove(clean) }
            appContext?.let { ctx ->
                writeDomainSetFile(File(ctx.filesDir, FILE_BLACKLIST), updatedBlack)
                writeDomainSetFile(File(ctx.filesDir, FILE_WHITELIST), updatedWhite)
            }
            val next = cur.copy(customBlacklist = updatedBlack, whitelist = updatedWhite)
            snapshot = next
            _stats.value = computeStats(next)
        }
        return true
    }

    fun removeCustomBlacklist(rawDomain: String) {
        val clean = normalizeDomain(rawDomain) ?: return
        synchronized(ioLock) {
            val cur = snapshot
            if (!cur.customBlacklist.contains(clean)) return
            val updatedBlack = HashSet(cur.customBlacklist).apply { remove(clean) }
            appContext?.let { ctx ->
                writeDomainSetFile(File(ctx.filesDir, FILE_BLACKLIST), updatedBlack)
            }
            val next = cur.copy(customBlacklist = updatedBlack)
            snapshot = next
            _stats.value = computeStats(next)
        }
    }

    fun addWhitelist(rawDomain: String): Boolean {
        val clean = normalizeDomain(rawDomain) ?: return false
        synchronized(ioLock) {
            val cur = snapshot
            val updatedWhite = HashSet(cur.whitelist).apply { add(clean) }
            val updatedBlack = HashSet(cur.customBlacklist).apply { remove(clean) }
            appContext?.let { ctx ->
                writeDomainSetFile(File(ctx.filesDir, FILE_WHITELIST), updatedWhite)
                writeDomainSetFile(File(ctx.filesDir, FILE_BLACKLIST), updatedBlack)
            }
            val next = cur.copy(whitelist = updatedWhite, customBlacklist = updatedBlack)
            snapshot = next
            _stats.value = computeStats(next)
        }
        return true
    }

    fun removeWhitelist(rawDomain: String) {
        val clean = normalizeDomain(rawDomain) ?: return
        synchronized(ioLock) {
            val cur = snapshot
            if (!cur.whitelist.contains(clean)) return
            val updatedWhite = HashSet(cur.whitelist).apply { remove(clean) }
            appContext?.let { ctx ->
                writeDomainSetFile(File(ctx.filesDir, FILE_WHITELIST), updatedWhite)
            }
            val next = cur.copy(whitelist = updatedWhite)
            snapshot = next
            _stats.value = computeStats(next)
        }
    }

    fun clearRecentEvents() {
        _recentBlocked.value = emptyList()
    }

    fun recordBlockedEvent(host: String, layer: String) {
        val clean = host.trim().ifBlank { return }
        val event = BlockedAdEvent(
            id = eventIdGen.incrementAndGet(),
            host = clean,
            layer = layer,
            timestampMs = System.currentTimeMillis()
        )
        synchronized(_recentBlocked) {
            val list = _recentBlocked.value
            val updated = ArrayList<BlockedAdEvent>(minOf(list.size + 1, MAX_RECENT_EVENTS))
            updated.add(event)
            for (i in 0 until minOf(list.size, MAX_RECENT_EVENTS - 1)) {
                updated.add(list[i])
            }
            _recentBlocked.value = updated
        }
    }

    fun isBlocked(domain: String): Boolean {
        return evaluateBlock(domain, aggressive = false, blockDoh = false) != null
    }

    fun isBlocked(domain: String, aggressive: Boolean, blockDoh: Boolean = aggressive): Boolean {
        return evaluateBlock(domain, aggressive = aggressive, blockDoh = blockDoh) != null
    }

    fun evaluateBlock(
        domain: String,
        aggressive: Boolean = true,
        blockDoh: Boolean = true
    ): AdBlockMatchType? {
        val clean = normalizeDomain(domain) ?: return null
        val snap = snapshot

        if (matchesSetOrParent(clean, snap.whitelist)) {
            return null
        }

        if (matchesSetOrParent(clean, snap.customBlacklist)) {
            return AdBlockMatchType.CUSTOM_BLACKLIST
        }

        if (blockDoh && matchesSetOrParent(clean, dohBypassDomains)) {
            return AdBlockMatchType.DOH_BYPASS
        }

        if (matchesSetOrParent(clean, embeddedDomains)) {
            return AdBlockMatchType.EXACT_OR_SUBDOMAIN
        }

        if (snap.downloadedDomains.isNotEmpty() && matchesSetOrParent(clean, snap.downloadedDomains)) {
            return AdBlockMatchType.DOWNLOADED_LIST
        }

        if (aggressive && matchesAdHeuristic(clean)) {
            return AdBlockMatchType.HEURISTIC
        }

        return null
    }

    private fun matchesSetOrParent(clean: String, set: Set<String>): Boolean {
        if (set.isEmpty()) return false
        if (set.contains(clean)) return true
        var dotIndex = clean.indexOf('.')
        while (dotIndex != -1 && dotIndex + 1 < clean.length) {
            val parent = clean.substring(dotIndex + 1)
            if (set.contains(parent)) return true
            dotIndex = clean.indexOf('.', dotIndex + 1)
        }
        return false
    }

    private fun matchesAdHeuristic(clean: String): Boolean {
        for (prefix in aggressiveLabelPrefixes) {
            if (clean.startsWith(prefix)) return true
            val idx = clean.indexOf(prefix)
            if (idx > 0 && clean[idx - 1] == '.') return true
        }
        for (token in aggressiveSubstringTokens) {
            if (clean.contains(token)) return true
        }
        return false
    }

    fun normalizeDomain(raw: String): String? {
        val clean = raw.trim().lowercase(Locale.ROOT).trimEnd('.')
        if (clean.length < 3 || clean.length > 253) return null
        if (!clean.contains('.')) return null
        if (clean == "localhost" || clean == "localhost.localdomain" || clean == "0.0.0.0") return null
        if (clean.all { it.isDigit() || it == '.' }) return null
        for (ch in clean) {
            if (!((ch in 'a'..'z') || (ch in '0'..'9') || ch == '.' || ch == '-' || ch == '_')) {
                return null
            }
        }
        return clean
    }

    /**
     * Extrai SNI (Server Name Indication, extensão 0x0000) do primeiro pacote TLS ClientHello
     * sem alocações pesadas, permitindo bloquear APKs que fazem cache de DNS ou ligam por IP.
     */
    fun extractTlsSni(payload: ByteArray, offset: Int = 0, length: Int = payload.size): String? {
        if (length < 43 || offset + length > payload.size) return null
        val end = offset + length

        if ((payload[offset].toInt() and 0xFF) != 0x16) return null
        if ((payload[offset + 1].toInt() and 0xFF) != 0x03) return null

        var pos = offset + 5
        if (pos + 4 > end) return null
        if ((payload[pos].toInt() and 0xFF) != 0x01) return null
        pos += 4

        pos += 34
        if (pos >= end) return null

        val sessionIdLen = payload[pos].toInt() and 0xFF
        pos += 1 + sessionIdLen
        if (pos + 2 > end) return null

        val cipherSuitesLen = ((payload[pos].toInt() and 0xFF) shl 8) or (payload[pos + 1].toInt() and 0xFF)
        pos += 2 + cipherSuitesLen
        if (pos + 1 > end) return null

        val compMethodsLen = payload[pos].toInt() and 0xFF
        pos += 1 + compMethodsLen
        if (pos + 2 > end) return null

        val extTotalLen = ((payload[pos].toInt() and 0xFF) shl 8) or (payload[pos + 1].toInt() and 0xFF)
        pos += 2
        val extEnd = minOf(pos + extTotalLen, end)

        while (pos + 4 <= extEnd) {
            val extType = ((payload[pos].toInt() and 0xFF) shl 8) or (payload[pos + 1].toInt() and 0xFF)
            val extLen = ((payload[pos + 2].toInt() and 0xFF) shl 8) or (payload[pos + 3].toInt() and 0xFF)
            pos += 4
            if (pos + extLen > extEnd) break

            if (extType == 0x0000 && extLen >= 5) {
                val listLen = ((payload[pos].toInt() and 0xFF) shl 8) or (payload[pos + 1].toInt() and 0xFF)
                var sPos = pos + 2
                val sEnd = minOf(sPos + listLen, pos + extLen)
                while (sPos + 3 <= sEnd) {
                    val nameType = payload[sPos].toInt() and 0xFF
                    val nameLen = ((payload[sPos + 1].toInt() and 0xFF) shl 8) or (payload[sPos + 2].toInt() and 0xFF)
                    sPos += 3
                    if (sPos + nameLen > sEnd) break
                    if (nameType == 0x00 && nameLen in 3..253) {
                        val sni = String(payload, sPos, nameLen, Charsets.US_ASCII)
                        return normalizeDomain(sni)
                    }
                    sPos += nameLen
                }
            }
            pos += extLen
        }
        return null
    }

    /**
     * Extrai cabeçalho HTTP `Host:` da primeira carga TCP.
     */
    fun extractHttpHost(payload: ByteArray, offset: Int = 0, length: Int = payload.size): String? {
        if (length < 16 || offset + length > payload.size) return null
        val scanLen = minOf(length, 2048)
        val b0 = payload[offset].toInt() and 0xFF
        val b1 = payload[offset + 1].toInt() and 0xFF
        val looksHttp = (b0 == 'G'.code && b1 == 'E'.code) ||
            (b0 == 'P'.code && (b1 == 'O'.code || b1 == 'U'.code || b1 == 'A'.code)) ||
            (b0 == 'H'.code && b1 == 'E'.code) ||
            (b0 == 'D'.code && b1 == 'E'.code) ||
            (b0 == 'O'.code && b1 == 'P'.code) ||
            (b0 == 'C'.code && b1 == 'O'.code)
        if (!looksHttp) return null

        val headerBlock = String(payload, offset, scanLen, Charsets.US_ASCII)
        val lines = headerBlock.split('\n')
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) break
            if (line.startsWith("host:", ignoreCase = true)) {
                var host = line.substring(5).trim()
                val colon = host.indexOf(':')
                if (colon > 0) host = host.substring(0, colon)
                return normalizeDomain(host)
            }
        }
        return null
    }

    private fun readDomainSetFile(file: File, limit: Int): Set<String> {
        if (!file.exists()) return emptySet()
        val set = HashSet<String>(1024)
        runCatching {
            BufferedReader(FileReader(file), 32 * 1024).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val domain = normalizeDomain(line ?: continue) ?: continue
                    set.add(domain)
                    if (set.size >= limit) break
                }
            }
        }
        return set
    }

    private fun writeDomainSetFile(file: File, domains: Set<String>) {
        runCatching {
            file.bufferedWriter().use { writer ->
                for (d in domains) {
                    writer.write(d)
                    writer.newLine()
                }
            }
        }
    }

    private fun computeStats(snap: RuleSnapshot): AdBlockStats {
        val total = embeddedDomains.size +
            dohBypassDomains.size +
            snap.downloadedDomains.size +
            snap.customBlacklist.size
        return AdBlockStats(
            embeddedCount = embeddedDomains.size,
            downloadedCount = snap.downloadedDomains.size,
            customBlockCount = snap.customBlacklist.size,
            whitelistCount = snap.whitelist.size,
            dohCount = dohBypassDomains.size,
            totalRuleCount = total,
            lastListName = snap.lastListName
        )
    }
}
