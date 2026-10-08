package com.readyport.board

import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ======================= 게시판 글 다루기 (순수 함수 — BoardTextTest) =======================

/** 길이 제한 — Firestore 규칙(firebase/firestore.rules)과 같은 값 */
object BoardLimits {
    val TITLE = 4..60
    val BODY = 10..3000
    val COMMENT = 1..1000
    val NICKNAME = 2..12

    /** 글과 글 사이 최소 간격(초) — 규칙이 board_users.lastPostAt 으로 막는다 */
    const val POST_GAP_SECONDS = 30L

    /** 댓글과 댓글 사이 최소 간격(초) */
    const val COMMENT_GAP_SECONDS = 10L

    /** 한 쪽에 불러오는 글 수 */
    const val PAGE_SIZE = 20

    /** 신고가 이만큼 쌓이면 이 휴대폰에서 글을 접어 둔다(`신고가 쌓여 가려진 글이에요` + 보기) */
    const val AUTO_HIDE_REPORTS = 3

    /** 사진 최대 장수·긴 변 픽셀 / 동영상 최대 길이·크기 */
    const val MAX_IMAGES = 4
    const val IMAGE_MAX_EDGE = 1600
    const val VIDEO_MAX_SECONDS = 30
    const val VIDEO_MAX_BYTES = 20L * 1024 * 1024

    /** 목록 카드 미리보기 글자 수 (줄 수로 자르지 않는다 — DESIGN_SPEC 원칙 6) */
    const val PREVIEW_CHARS = 70

    /** 검색 낱말 최대 개수 (규칙: keywords.size() <= 40) */
    const val MAX_KEYWORDS = 40
}

/** 입력 검사 결과 (글자 수 = 앞뒤 공백을 뺀 글자 — 규칙의 string.size()와 같은 셈) */
enum class FieldProblem { TooShort, TooLong }

fun checkLength(text: String, range: IntRange): FieldProblem? {
    val n = text.trim().length
    return when {
        n < range.first -> FieldProblem.TooShort
        n > range.last -> FieldProblem.TooLong
        else -> null
    }
}

// ---------------- 검색 낱말 ----------------

/**
 * Firestore에는 전문 검색이 없다 → 글을 올릴 때 `keywords` 배열을 함께 저장하고 `array-contains` 하나로 찾는다.
 * - 한글 낱말은 두 글자씩 겹쳐 자른다(`방콕공항` → 방콕·콕공·공항): 조사가 붙어도(`공항에서`) 찾힌다.
 * - 영문·숫자 낱말은 소문자 통째로(두 글자 이상).
 * - 제목 낱말이 먼저, 그다음 본문 — 최대 40개(규칙). 본문이 길면 뒤쪽 낱말은 빠진다(문서에 적은 한계).
 */
object BoardKeywords {
    private val Splitter = Regex("[^\\p{L}\\p{N}]+")

    private fun isHangul(c: Char) = c in '가'..'힣'

    /** 낱말 하나 → 검색 조각 */
    fun tokens(word: String): List<String> {
        val w = word.lowercase()
        if (w.length < 2) return emptyList()
        return if (w.any(::isHangul)) {
            w.windowed(2).filter { gram -> gram.all(::isHangul) || gram.all { it.isLetterOrDigit() } }
        } else {
            listOf(w)
        }
    }

    /** 글 한 덩어리 → 조각들(나온 순서, 겹침 없음) */
    fun of(text: String): List<String> =
        Normalizer.normalize(text, Normalizer.Form.NFC).split(Splitter).flatMap(::tokens).distinct()

    /** 저장할 검색 낱말 (제목 먼저, 최대 [BoardLimits.MAX_KEYWORDS]개) */
    fun forPost(title: String, body: String): List<String> =
        (of(title) + of(body)).distinct().take(BoardLimits.MAX_KEYWORDS)

    /** 찾는 말 → 서버에 물을 조각 하나(가장 긴 낱말의 첫 조각)와 화면에서 거를 조각 전부 */
    fun query(search: String): SearchPlan? {
        val words = Normalizer.normalize(search, Normalizer.Form.NFC).split(Splitter).filter { it.length >= 2 }
        if (words.isEmpty()) return null
        val all = words.flatMap(::tokens).distinct()
        if (all.isEmpty()) return null
        val first = tokens(words.maxBy { it.length }).first()
        return SearchPlan(first, all)
    }

    /** 화면에서 한 번 더 거르기: 찾는 조각이 모두 제목·본문에 있는 글만 (서버 조각 하나로는 넓게 걸린다) */
    fun matches(post: BoardPost, plan: SearchPlan): Boolean {
        val text = of(post.title + " " + post.body).toSet()
        return plan.all.all { it in text }
    }
}

/** [server]: array-contains 로 물을 조각, [all]: 모두 들어 있어야 하는 조각 */
data class SearchPlan(val server: String, val all: List<String>)

// ---------------- 개인정보 거르기 ----------------

/** 글에서 찾은 개인정보 같은 부분 */
enum class PiiKind(val blocking: Boolean) {
    /** 여권 번호처럼 보이는 글자 (영문 1~2 + 숫자 7~8) — 꼭 고쳐야 올라간다 */
    Passport(true),

    /** 여권 아래 두 줄(MRZ) — 꼭 고쳐야 올라간다 */
    Mrz(true),

    /** 주민등록번호 — 꼭 고쳐야 올라간다 */
    ResidentId(true),

    /** 전화번호 — 대사관·공항 번호처럼 공개된 번호일 수 있어 경고만(그대로 올리기 가능) */
    Phone(false),

    /** 이메일 — 경고만 */
    Email(false),
}

data class PiiHit(val kind: PiiKind, val range: IntRange)

/**
 * 올리기 전에 개인정보처럼 보이는 글자를 찾는다(`개인정보가 들어 있는 것 같아요` + 찾은 부분 표시).
 * 이 앱의 약속(여권 정보는 휴대폰 밖으로 나가지 않는다)을 사람이 직접 깨지 않게 돕는 그물이다 — 모든 경우를 잡지는 못한다.
 */
object PiiGuard {
    private val rules: List<Pair<PiiKind, Regex>> = listOf(
        // MRZ: 영문 대문자·숫자·'<' 30자 이상, '<<' 포함 (여권 아래 두 줄)
        PiiKind.Mrz to Regex("[A-Z0-9<]{30,44}"),
        // 주민등록번호: 6자리-7자리(뒤 첫 자리 1~8)
        PiiKind.ResidentId to Regex("(?<![0-9])[0-9]{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12][0-9]|3[01])\\s?-?\\s?[1-8][0-9]{6}(?![0-9])"),
        // 여권 번호: 영문 1~2자 + 숫자 7~8자 (한국 여권 M12345678, 차세대 M123A4567도)
        PiiKind.Passport to Regex("(?<![A-Za-z0-9])(?:[A-Za-z]{1,2}[0-9]{7,8}|[A-Za-z][0-9]{3}[A-Za-z][0-9]{4})(?![A-Za-z0-9])"),
        PiiKind.Email to Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
        // 전화번호: 국내 휴대폰·지역번호, +국가번호
        PiiKind.Phone to Regex(
            "(?<![0-9])(?:\\+[0-9]{1,3}[ -]?[0-9]{1,4}[ -]?[0-9]{3,4}[ -]?[0-9]{4}|0[0-9]{1,2}[ .-]?[0-9]{3,4}[ .-]?[0-9]{4})(?![0-9])",
        ),
    )

    fun scan(text: String): List<PiiHit> {
        val hits = mutableListOf<PiiHit>()
        for ((kind, regex) in rules) {
            regex.findAll(text).forEach { m ->
                if (kind == PiiKind.Mrz && "<<" !in m.value) return@forEach
                // 이미 찾은 부분과 겹치면 앞의 것(더 엄격한 것)만
                if (hits.none { it.range.first <= m.range.last && m.range.first <= it.range.last }) hits += PiiHit(kind, m.range)
            }
        }
        return hits.sortedBy { it.range.first }
    }

    /** 고치지 않으면 올릴 수 없는 것이 있는지 */
    fun blocking(hits: List<PiiHit>): Boolean = hits.any { it.kind.blocking }
}

// ---------------- 욕설 거르기 ----------------

/**
 * 짧은 욕설 목록(한국어·영어). 글에 있으면 올리기 전에 `＊`로 가린 모습을 보여 주고 그대로 가려서 올린다.
 * 닉네임에는 아예 쓸 수 없다. 알림 미리보기에도 가린 글만 쓴다. 완벽하지 않다 — 신고와 운영자 가림이 뒤를 받친다.
 */
object Profanity {
    private val words = listOf(
        "씨발", "시발", "ㅅㅂ", "씨빨", "개새끼", "개새기", "병신", "ㅂㅅ", "좆", "존나", "지랄", "미친놈", "미친년", "썅", "느금마", "니애미",
        "fuck", "shit", "bitch", "asshole", "bastard", "cunt", "motherfucker",
    )

    /** 낱말 사이에 끼운 띄어쓰기·점·숫자(`씨 발`, `씨1발`)도 잡는다 */
    private val patterns: List<Regex> = words.map { w ->
        Regex(w.toList().joinToString("[\\s._\\-0-9]{0,2}") { Regex.escape(it.toString()) }, RegexOption.IGNORE_CASE)
    }

    fun contains(text: String): Boolean = patterns.any { it.containsMatchIn(text) }

    /** 욕설 글자를 같은 길이의 `＊`로 */
    fun mask(text: String): String {
        var out = text
        patterns.forEach { p -> out = p.replace(out) { m -> m.value.map { if (it.isWhitespace()) it else MASK }.joinToString("") } }
        return out
    }

    const val MASK = '＊'
}

// ---------------- 닉네임 ----------------

enum class NicknameProblem { TooShort, TooLong, BadChars, Reserved, Profanity }

object NicknameRules {
    /** 규칙과 같은 글자: 한글·영문·숫자·밑줄·가운데 띄어쓰기 */
    private val Allowed = Regex("^[가-힣A-Za-z0-9_ ]+$")
    private val Reserved = listOf("운영자", "관리자", "admin", "레디포트", "readyport", "운영팀", "official")

    fun validate(raw: String): NicknameProblem? {
        val n = raw.trim()
        return when {
            n.length < BoardLimits.NICKNAME.first -> NicknameProblem.TooShort
            n.length > BoardLimits.NICKNAME.last -> NicknameProblem.TooLong
            !Allowed.matches(n) || "  " in n -> NicknameProblem.BadChars
            Reserved.any { n.lowercase().replace(" ", "").contains(it) } -> NicknameProblem.Reserved
            Profanity.contains(n) -> NicknameProblem.Profanity
            else -> null
        }
    }

    /** 처음 고를 때 보여 줄 이름 `여행자 4821` (숫자 네 자리) */
    fun suggest(random: kotlin.random.Random = kotlin.random.Random.Default): String =
        "여행자 " + random.nextInt(1000, 10000)
}

// ---------------- 시간·모양 ----------------

/** 상대 시각 표시 단위 */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val n: Long) : RelativeTime
    data class Hours(val n: Long) : RelativeTime
    data class Days(val n: Long) : RelativeTime

    /** 7일이 넘으면 날짜 `2026. 10. 8.` */
    data class Date(val text: String) : RelativeTime
}

object BoardTime {
    private val DateFmt = DateTimeFormatter.ofPattern("yyyy. M. d.")

    fun relative(now: Instant, then: Instant, zone: ZoneId = ZoneId.systemDefault()): RelativeTime {
        val d = Duration.between(then, now).coerceAtLeast(Duration.ZERO)
        return when {
            d.toMinutes() < 1 -> RelativeTime.JustNow
            d.toHours() < 1 -> RelativeTime.Minutes(d.toMinutes())
            d.toDays() < 1 -> RelativeTime.Hours(d.toHours())
            d.toDays() < 7 -> RelativeTime.Days(d.toDays())
            else -> RelativeTime.Date(DateFmt.format(then.atZone(zone)))
        }
    }

    /** 다시 쓸 수 있을 때까지 남은 초 (0이면 지금 써도 된다) */
    fun waitSeconds(now: Instant, last: Instant?, gapSeconds: Long): Long {
        if (last == null) return 0
        val left = gapSeconds - Duration.between(last, now).seconds
        return left.coerceAtLeast(0)
    }
}

/** 목록 카드 미리보기: 줄바꿈을 띄어쓰기로, [BoardLimits.PREVIEW_CHARS]자 넘으면 낱말 경계에서 자르고 `…` */
fun boardPreview(body: String, max: Int = BoardLimits.PREVIEW_CHARS): String {
    val flat = body.replace(Regex("\\s+"), " ").trim()
    if (flat.length <= max) return flat
    val cut = flat.take(max)
    val space = cut.lastIndexOf(' ')
    return (if (space > max / 2) cut.take(space) else cut).trimEnd() + "…"
}

/** 닉네임 첫 글자(아바타 원 안) — 숫자로 시작해도 그 글자 */
fun avatarInitial(nickname: String): String =
    nickname.trim().firstOrNull()?.toString()?.uppercase() ?: "?"

/** 아바타 원 색 번호 (uid 해시 → 0 until [count]) — 사진 없이 사람을 구분하는 색 */
fun avatarSlot(uid: String, count: Int): Int = (uid.hashCode() and Int.MAX_VALUE) % count
