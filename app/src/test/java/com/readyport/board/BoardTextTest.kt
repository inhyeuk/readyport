package com.readyport.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** 게시판 글 다루기 (순수 함수): 검색 낱말 · 개인정보 그물 · 욕설 · 닉네임 · 시각 · 미리보기 */
class BoardTextTest {

    // ---------------- 검색 낱말 ----------------

    @Test
    fun hangulWordsBecomeOverlappingTwoGrams() {
        assertEquals(listOf("방콕", "콕공", "공항"), BoardKeywords.tokens("방콕공항"))
        // 조사가 붙어도 찾힌다: '공항에서' 안에 '공항'
        assertTrue("공항" in BoardKeywords.of("수완나품 공항에서 TDAC 확인"))
        assertTrue("tdac" in BoardKeywords.of("수완나품 공항에서 TDAC 확인"))
        // 한 글자 낱말은 버린다
        assertEquals(emptyList<String>(), BoardKeywords.tokens("가"))
    }

    @Test
    fun keywordsPutTitleFirstAndCapAtForty() {
        val body = (1..80).joinToString(" ") { "word$it" }
        val k = BoardKeywords.forPost("태국 입국", body)
        assertEquals(BoardLimits.MAX_KEYWORDS, k.size)
        assertEquals(listOf("태국", "입국"), k.take(2))
        assertEquals(k.size, k.toSet().size)
    }

    @Test
    fun searchPlanAsksServerOneTokenAndFiltersTheRestOnDevice() {
        val plan = BoardKeywords.query("방콕 공항버스")!!
        // 서버에는 가장 긴 낱말의 첫 조각 하나 (array-contains는 값 하나만)
        assertEquals("공항", plan.server)
        assertTrue(plan.all.containsAll(listOf("방콕", "공항", "항버", "버스")))
        val hit = post("p1", "방콕 공항버스 타는 곳", "수완나품 1층에서 타요. 번호는 S1이에요.")
        val miss = post("p2", "공항버스 질문", "치앙마이 공항 말이에요.")
        assertTrue(BoardKeywords.matches(hit, plan))
        assertFalse(BoardKeywords.matches(miss, plan))
        assertNull(BoardKeywords.query("가 "))
    }

    // ---------------- 개인정보 그물 ----------------

    @Test
    fun piiGuardFindsPassportMrzResidentPhoneEmail() {
        val text = "여권번호 M12345678 이고 연락은 010-1234-5678 또는 traveler@example.com 으로 주세요. 주민번호 900101-1234567"
        val kinds = PiiGuard.scan(text).map { it.kind }
        assertEquals(listOf(PiiKind.Passport, PiiKind.Phone, PiiKind.Email, PiiKind.ResidentId), kinds)
        // 찾은 자리가 정확하다(화면이 빨갛게 표시)
        val passport = PiiGuard.scan(text).first()
        assertEquals("M12345678", text.substring(passport.range))
        assertTrue(PiiGuard.blocking(PiiGuard.scan(text)))
    }

    @Test
    fun mrzLineIsCaughtAndBlocks() {
        // ICAO 9303 표본(가상 국가 UTO) — 실제 여권이 아니다
        val mrz = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
        val hits = PiiGuard.scan("이 줄을 넣으면 되나요?\n$mrz")
        assertEquals(PiiKind.Mrz, hits.single().kind)
        assertTrue(PiiGuard.blocking(hits))
    }

    @Test
    fun phoneAndEmailOnlyWarnSoEmbassyNumbersCanBePosted() {
        val hits = PiiGuard.scan("방콕 한국대사관은 +66-2-247-7537 이에요")
        assertEquals(listOf(PiiKind.Phone), hits.map { it.kind })
        assertFalse(PiiGuard.blocking(hits))
    }

    @Test
    fun ordinaryTravelTextIsNotFlagged() {
        val text = "KE651편 타고 11월 3일 도착해요. TDAC는 72시간 전부터 내요. 환율은 1바트 39원 정도."
        assertEquals(emptyList<PiiHit>(), PiiGuard.scan(text))
    }

    // ---------------- 욕설 ----------------

    @Test
    fun profanityIsMaskedWithSameLengthStars() {
        assertTrue(Profanity.contains("아 씨발 진짜"))
        assertTrue(Profanity.contains("씨 발"))
        assertEquals("아 ＊＊ 진짜", Profanity.mask("아 씨발 진짜"))
        assertEquals("What the ＊＊＊＊", Profanity.mask("What the FUCK"))
        assertFalse(Profanity.contains("시발점에서 출발해요".replace("시발", "출발")))
        assertEquals("즐거운 여행 되세요", Profanity.mask("즐거운 여행 되세요"))
    }

    // ---------------- 닉네임 ----------------

    @Test
    fun nicknameRules() {
        assertNull(NicknameRules.validate("여행자 4821"))
        assertNull(NicknameRules.validate("Seoul_trip"))
        assertEquals(NicknameProblem.TooShort, NicknameRules.validate("가"))
        assertEquals(NicknameProblem.TooLong, NicknameRules.validate("열세글자가넘는아주긴닉네임"))
        assertEquals(NicknameProblem.BadChars, NicknameRules.validate("여행자<b>"))
        assertEquals(NicknameProblem.BadChars, NicknameRules.validate("여행  자"))
        assertEquals(NicknameProblem.Reserved, NicknameRules.validate("레디포트 운영자"))
        assertEquals(NicknameProblem.Reserved, NicknameRules.validate("Admin 01"))
        assertEquals(NicknameProblem.Profanity, NicknameRules.validate("병신여행"))
        // 추천 이름: `여행자 1234`
        val s = NicknameRules.suggest(Random(7))
        assertTrue(s, Regex("^여행자 [0-9]{4}$").matches(s))
        assertNull(NicknameRules.validate(s))
    }

    // ---------------- 시각·모양 ----------------

    @Test
    fun relativeTimeAndWaitSeconds() {
        val now = Instant.parse("2026-10-08T03:00:00Z")
        val zone = ZoneId.of("Asia/Seoul")
        assertEquals(RelativeTime.JustNow, BoardTime.relative(now, now.minusSeconds(30), zone))
        assertEquals(RelativeTime.Minutes(5), BoardTime.relative(now, now.minusSeconds(300), zone))
        assertEquals(RelativeTime.Hours(3), BoardTime.relative(now, now.minusSeconds(3 * 3600), zone))
        assertEquals(RelativeTime.Days(2), BoardTime.relative(now, now.minusSeconds(2 * 86400), zone))
        assertEquals(RelativeTime.Date("2026. 9. 20."), BoardTime.relative(now, Instant.parse("2026-09-20T01:00:00Z"), zone))
        assertEquals(18, BoardTime.waitSeconds(now, now.minusSeconds(12), 30))
        assertEquals(0, BoardTime.waitSeconds(now, now.minusSeconds(31), 30))
        assertEquals(0, BoardTime.waitSeconds(now, null, 30))
    }

    @Test
    fun previewCutsByCharactersAtWordBoundary() {
        val body = "수완나품 공항 1층 7번 출구 앞에서 공항버스를 타요. 표는 기사님께 사요. 시내까지 한 시간쯤 걸리고 길이 막히면 더 걸려요."
        val p = boardPreview(body)
        assertTrue(p.endsWith("…"))
        assertTrue(p.length <= BoardLimits.PREVIEW_CHARS + 1)
        assertFalse(p.dropLast(1).endsWith(" "))
        assertEquals("짧은 글", boardPreview("짧은\n글"))
    }

    @Test
    fun avatarIsStablePerUser() {
        assertEquals(avatarSlot("uid-a", 6), avatarSlot("uid-a", 6))
        assertTrue(avatarSlot("uid-b", 6) in 0 until 6)
        assertEquals("여", avatarInitial("여행자 4821"))
        assertEquals("S", avatarInitial("seoul"))
    }

    private fun post(id: String, title: String, body: String) = BoardPost(
        id = id, kind = BoardKind.Qna, title = title, body = body, country = "TH", authorUid = "u", nickname = "여행자 1000",
        createdAt = Instant.parse("2026-10-08T00:00:00Z"), keywords = BoardKeywords.forPost(title, body),
    )
}
