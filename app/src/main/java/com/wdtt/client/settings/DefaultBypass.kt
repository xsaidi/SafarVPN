package com.wdtt.client

/** Concrete user-requested services; operator reachability is not guaranteed by this preset. */
internal object DefaultBypass {
    val domains = """
        vk.com mycdn.me magnit.com yandex.net yastatic.net yandex.cloud wbstatic.net
        2gis.com avito.st okko.tv osmand.net share.here.com mappls.com mapy.cz
        bk.probank.pro amina.bright-soft.org api-sec-migrants.bright-soft.org
        pudsmig.firebasestorage.app maps.google.com maps.apple.com premier.one
        russian.rt.com moex.com motiv.me pobeda.aero ren.tv vmeste-rf.tv lizaalert.org
        lenta.com soloviev.live tvoyhod.online ivi.tv tochka.com pskb.com express.ms
        beeline.tv mir24.tv travel2moscow.com drivee.com chizhik.club drinkit.io
        vkclips.app energo.app yappy.media otzovik.com openstreetmap.org maps.me
        timeweb.com go.yandex yandex.taxi yandex.ru ya.ru mail.ru ok.ru gosuslugi.ru
        ozon.ru wildberries.ru avito.ru 2gis.ru magnit.ru sberbank.ru tbank.ru
    """.trimIndent().split(Regex("\\s+")).distinct()
    val packages = listOf(
        "com.vkontakte.android", "ru.ok.android", "com.vk.clips", "ru.mail.mailapp",
        "ru.yandex.searchplugin", "com.yandex.browser", "ru.yandex.yandexmaps",
        "ru.yandex.yandexnavi", "ru.yandex.taxi", "ru.yandex.market", "ru.yandex.disk",
        "ru.dublgis.dgismobile", "com.avito.android", "ru.ozon.app.android",
        "com.wildberries.ru", "ru.gosuslugi.app", "ru.sberbankmobile",
        "com.idamob.tinkoff.android", "ru.tander.magnit", "ru.lenta.lentochka",
        "ru.ivi.client", "ru.more.play", "com.mapswithme.maps.pro",
        "net.osmand", "net.osmand.plus"
    )
    fun mergeDomains(existing: String): String = (existing.lineSequence().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith('#') }.toList() + domains)
        .distinct().take(BypassRoutes.MAX_RULES).joinToString("\n")
    fun mergePackages(existing: String): String = (existing.split(',').filter { it.isNotBlank() } + packages)
        .distinct().joinToString(",")
}
