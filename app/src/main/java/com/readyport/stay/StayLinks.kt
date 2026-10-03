package com.readyport.stay

import android.content.Context
import com.readyport.transport.Attempt
import com.readyport.transport.RideLinker
import com.readyport.vault.StayRecord

/**
 * 숙소를 지도에서 열기 — 공식 문서(developers.google.com/maps/documentation/urls)의 주소만 쓴다.
 * API 키도, 새 라이브러리도, 서버도 없다: 주소를 만들어 [android.content.Intent.ACTION_VIEW]로 넘기면
 * 지도 앱(없으면 브라우저)이 받는다. 주소는 [Stays]가 만들고 여기서는 열기만 한다.
 */
object StayLinks {

    /** 장소 찾기(Search). 열 수 없으면 false */
    fun openSearch(context: Context, stay: StayRecord): Boolean {
        val url = Stays.searchUrl(stay) ?: return false
        return RideLinker.execute(context, listOf(Attempt.OpenUri(url, null))) {} != null
    }

    /** 길찾기(Directions) — 이동하기 화면과 같은 주소(RideLinker). 주소·좌표가 없으면 false */
    fun openDirections(context: Context, stay: StayRecord): Boolean {
        val place = Stays.place(stay) ?: return false
        return RideLinker.execute(context, listOf(Attempt.OpenUri(RideLinker.mapsUrl(place), null))) {} != null
    }
}
