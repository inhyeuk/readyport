package com.readyport.board

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.readyport.MainActivity
import com.readyport.R
import com.readyport.data.settings.SettingsRepository
import com.readyport.trip.TripNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 답글 알림 글 (순수 — BoardRepliesTest). 서버 토큰 없이 앱이 스스로 확인해 **이 휴대폰 안에서** 알린다.
 * 남이 쓴 글은 짧은 미리보기 한 줄만 — 욕설 그물에 걸리면 미리보기 없이 숫자만.
 */
object BoardReplyText {
    /** 미리보기 최대 글자 */
    const val PREVIEW_MAX = 40

    data class Text(val count: Int, val preview: String?, val postId: String?)

    fun of(fresh: List<BoardComment>): Text? {
        if (fresh.isEmpty()) return null
        val latest = fresh.maxBy { it.createdAt }
        val body = latest.body.replace(Regex("\\s+"), " ").trim()
        val preview = body.takeIf { it.isNotEmpty() && !Profanity.contains(it) && !Profanity.contains(latest.nickname) }
            ?.let { if (it.length > PREVIEW_MAX) it.take(PREVIEW_MAX - 1).trimEnd() + "…" else it }
            ?.let { "${latest.nickname}: $it" }
        return Text(fresh.size, preview, latest.postId)
    }
}

/** 게시판 답글 알림 통로 `게시판 답글` — 챙길 일·공지 알림과 따로 켜고 끈다 */
object BoardNotifications {
    const val CHANNEL = "board"

    /** 알림 번호 (공지 2,000,000+, 여행 1000~900999와 겹치지 않게) */
    const val ID = 3_000_001

    fun post(context: Context, text: BoardReplyText.Text): Boolean {
        if (!TripNotifications.canNotify(context)) return false
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.board_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        // 누르면 그 글 (글 id만 넘긴다)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_BOARD, text.postId.orEmpty())
        val open = PendingIntent.getActivity(context, ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = context.getString(R.string.board_reply_title, text.count)
        val body = text.preview ?: context.getString(R.string.board_reply_body_generic)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            // 잠금 화면에는 남이 쓴 미리보기를 숨긴다
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                    .setContentTitle(title)
                    .build(),
            )
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(ID, n)
        return true
    }
}

/**
 * 내 글·댓글에 새 답글이 왔는지 본다 — 앱을 켤 때와 하루 한 번(챙길 일 쓸기·팩 받기 작업)에.
 * 자녀 폰 모드·`게시판 답글 알림` 끔·로그인한 적 없음이면 아무것도 하지 않는다. 실패해도 조용히 넘어간다.
 */
@Singleton
class BoardReplyCheck @Inject constructor(
    @ApplicationContext private val context: Context,
    private val board: BoardRepository,
    private val settings: SettingsRepository,
) {
    suspend fun run() {
        val s = settings.current()
        if (s.childMode || !s.boardReplies) return
        val fresh = runCatching { board.checkReplies() }.getOrDefault(emptyList())
        BoardReplyText.of(fresh)?.let { BoardNotifications.post(context, it) }
    }
}
