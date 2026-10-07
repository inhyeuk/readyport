package com.readyport

import android.content.Context
import com.readyport.pack.PackSync
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readyport.data.settings.AppSettings
import com.readyport.data.settings.SettingsRepository
import com.readyport.notice.Notice
import com.readyport.notice.NoticeChoice
import com.readyport.notice.NoticeContext
import com.readyport.notice.NoticeRepository
import com.readyport.notice.NoticeSelector
import com.readyport.notice.NoticeStore
import com.readyport.notice.NoticeType
import com.readyport.trip.TripRepository
import com.readyport.tts.Speaker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val speaker: Speaker,
    private val notices: NoticeRepository,
    private val noticeStore: NoticeStore,
    private val trips: TripRepository,
) : ViewModel() {

    /** null = 아직 읽는 중 */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEasyMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEasyMode(enabled) }
    }

    fun setChildMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setChildMode(enabled) }
    }

    /** 나라 안내를 와이파이에서만 받기 (PRD 5.7) */
    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWifiOnly(enabled)
            PackSync.scheduleDaily(context, enabled)
            PackSync.requestNow(context, enabled)
        }
    }

    /** 챙길 일 알림 켬·끔 (PRD 6.1). 작업을 다시 맞추는 일은 ChecklistAlerts가 설정을 지켜보다가 한다 */
    fun setAlertsOn(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAlertsOn(enabled) }
    }

    fun setAlertHour(hour: Int) {
        viewModelScope.launch { settingsRepository.setAlertHour(hour) }
    }

    // ---------------- 공지·소식 (docs/NOTICES_PUSH.md) ----------------

    private val _launchNotices = MutableStateFlow<List<Notice>>(emptyList())

    /** 앱을 켤 때 띄울 공지 (맨 앞 것을 띄우고, 닫으면 다음 것 — 긴급 공지만 여럿이 이어진다) */
    val launchNotices: StateFlow<List<Notice>> = _launchNotices.asStateFlow()
    private var noticesStarted = false

    /** 차례(돌아가며 보이기) 계산에 쓰는 그때의 긴급 아닌 후보 */
    private var launchOthers: List<Notice> = emptyList()

    /**
     * 앱을 켤 때 한 번: 첫 실행 질문을 마친 뒤(같은 실행 안에서 마쳐도 그 뒤에), 자녀 폰 모드가 아니면 서명된 공지를 받아(최대 [LAUNCH_TIMEOUT_MS],
     * 못 받으면 기기 안 사본) 띄울 것을 고른다. [skip]: 위젯·알림·공유로 연 실행(할 일이 따로 있다)이나 화면 회전 같은 다시 만들기.
     */
    fun startNotices(skip: Boolean) {
        if (noticesStarted) return
        noticesStarted = true
        if (skip) return
        viewModelScope.launch {
            val s = settingsRepository.settings.first { it.easyMode != null }
            if (s.childMode) return@launch
            val doc = notices.refresh(LAUNCH_TIMEOUT_MS) ?: return@launch
            val ctx = NoticeContext(
                now = Instant.now(),
                today = LocalDate.now(),
                versionCode = BuildConfig.VERSION_CODE,
                countries = s.favorites + trips.all().map { it.country },
                childMode = s.childMode,
                promoOn = s.promoPush,
            )
            val marks = noticeStore.marks()
            launchOthers = NoticeSelector.candidates(doc, ctx, marks).filter { it.type != NoticeType.Urgent }
            _launchNotices.value = NoticeSelector.launchQueue(doc, ctx, marks)
        }
    }

    /** 대화상자를 닫았다 — 기록하고 다음 것(긴급 공지가 이어질 때)을 띄운다 */
    fun onLaunchNoticeDone(notice: Notice, choice: NoticeChoice) {
        _launchNotices.value = _launchNotices.value.filterNot { it.key == notice.key }
        viewModelScope.launch { noticeStore.record(notice, choice, LocalDate.now(), launchOthers) }
    }

    /** 공지 알림 켬·끔 — 토픽 구독은 CloudSync가 설정을 지켜보다가 맞춘다 */
    fun setNoticePush(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNoticePush(enabled) }
    }

    /** 광고성 소식 받기 (기본 끔). 켜거나 끈 날을 이 휴대폰에 적는다 */
    fun setPromoPush(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPromoPush(enabled, LocalDate.now()) }
    }

    fun setPromoNight(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPromoNight(enabled, LocalDate.now()) }
    }

    fun speak(text: String) = speaker.speak(text)

    override fun onCleared() {
        speaker.stop()
    }

    private companion object {
        /** 앱 시작을 붙잡지 않게 — 못 받으면 기기 안 사본 */
        const val LAUNCH_TIMEOUT_MS = 2_500L
    }
}
