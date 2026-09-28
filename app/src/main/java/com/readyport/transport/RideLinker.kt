package com.readyport.transport

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.serialization.Serializable
import java.net.URLEncoder

/** 가는 곳 (숙소 등). 기기 안에만 저장 */
@Serializable
data class Place(
    val id: String,
    val name: String,
    /** 현지 글자 주소 — 기사님께 보여 주는 용 */
    val addressLocal: String,
    val lat: Double? = null,
    val lng: Double? = null,
)

/**
 * 교통 앱 연결 방식 (국가 팩 transport_apps[].link_type, ARCHITECTURE 9.7)
 * - maps_url: 공식 문서의 Google 지도 길찾기 주소로 목적지까지 채워 연다
 * - uber_url: 공식 문서의 Uber 딥링크 (좌표가 있을 때만)
 * - open_and_copy: 목적지를 넣는 공식 규격이 없는 앱 — 앱을 열고 주소를 클립보드에 복사
 */
enum class LinkType(val code: String) {
    MapsUrl("maps_url"), UberUrl("uber_url"), OpenAndCopy("open_and_copy");

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: OpenAndCopy
    }
}

sealed interface Attempt {
    /** 목적지를 넣은 주소로 열기 (1단계) */
    data class OpenUri(val uri: String, val pkg: String?) : Attempt
    /** 앱만 열고 주소 복사 (2단계) */
    data class LaunchApp(val pkg: String, val copyText: String) : Attempt
    /** 설치 안 됨 → Play 스토어 (3단계) */
    data class PlayStore(val pkg: String) : Attempt
}

object RideLinker {

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Google 지도 길찾기 URL (developers.google.com/maps/documentation/urls) */
    fun mapsUrl(dest: Place, travelMode: String = "transit"): String {
        val destination = if (dest.lat != null && dest.lng != null) "${dest.lat},${dest.lng}" else dest.addressLocal
        return "https://www.google.com/maps/dir/?api=1&destination=${enc(destination)}&travelmode=$travelMode"
    }

    /**
     * Uber 딥링크 (developer.uber.com 'Deep Links' 문서의 uber://riderequest 형식).
     * 좌표가 있어야 목적지를 채울 수 있다. Android에서는 사용자가 출발지를 정한 뒤 목적지가 보인다(문서 안내).
     */
    fun uberUrl(dest: Place): String? {
        if (dest.lat == null || dest.lng == null) return null
        return "uber://riderequest?pickup=my_location" +
            "&dropoff%5Blatitude%5D=${dest.lat}&dropoff%5Blongitude%5D=${dest.lng}" +
            "&dropoff%5Bnickname%5D=${enc(dest.name)}&dropoff%5Bformatted_address%5D=${enc(dest.addressLocal)}"
    }

    /**
     * 3단계 연결 순서 (ARCHITECTURE 9.7). 앞 단계가 실패하면 다음 단계로.
     * 설치 안 된 앱은 바로 Play 스토어.
     */
    fun plan(pkg: String, linkType: LinkType, installed: Boolean, dest: Place): List<Attempt> {
        if (!installed) return listOf(Attempt.PlayStore(pkg))
        val first = when (linkType) {
            LinkType.MapsUrl -> Attempt.OpenUri(mapsUrl(dest), pkg)
            LinkType.UberUrl -> uberUrl(dest)?.let { Attempt.OpenUri(it, pkg) }
            LinkType.OpenAndCopy -> null
        }
        return listOfNotNull(first, Attempt.LaunchApp(pkg, dest.addressLocal), Attempt.PlayStore(pkg))
    }

    fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)

    /**
     * 차례로 시도하고, 성공한 단계를 돌려준다. 모두 실패하면 null.
     * [copy]는 2단계에서 주소를 복사할 때 부른다.
     */
    fun execute(context: Context, attempts: List<Attempt>, copy: (String) -> Unit): Attempt? {
        for (a in attempts) {
            val ok = runCatching {
                when (a) {
                    is Attempt.OpenUri -> {
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse(a.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        a.pkg?.let { i.setPackage(it) }
                        context.startActivity(i)
                    }
                    is Attempt.LaunchApp -> {
                        val i = context.packageManager.getLaunchIntentForPackage(a.pkg) ?: throw ActivityNotFoundException(a.pkg)
                        copy(a.copyText)
                        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    is Attempt.PlayStore -> {
                        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${a.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(market)
                        } catch (e: ActivityNotFoundException) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${a.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            }.isSuccess
            if (ok) return a
        }
        return null
    }
}
