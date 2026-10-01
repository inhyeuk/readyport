package com.readyport.pack

import com.readyport.prep.Essentials
import com.readyport.prep.ImportStatus
import com.readyport.prep.import
import com.readyport.ui.TestPacks
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M8: 서명된 내장 팩만으로(비행기 모드) 꼭 챙길 물건·쇼핑 리스트가 동작하는지, 표시 규칙을 지키는지 */
class M8PacksTest {

    private val index = TestPacks.index.value
    private fun pack(c: String) = runBlocking { TestPacks.repo.pack(c)!!.value }
    private val countries = listOf("TH", "MY", "SG", "ID", "JP", "PH", "VN")

    @Test fun offlineEssentialsPerCountry() {
        val home = index.homePower
        assertNotNull(home)
        fun ids(c: String) = Essentials.select(index.essentials, home, pack(c).power).map { it.id }
        // 싱가포르·말레이시아: 영국식 3핀 → 어댑터. 태국·인도네시아: 한국 플러그가 맞음
        assertTrue("plug_adapter" in ids("SG"))
        assertTrue("plug_adapter" in ids("MY"))
        assertFalse("plug_adapter" in ids("TH"))
        assertFalse("plug_adapter" in ids("ID"))
        // 일본: 100V → 전압 확인. 플러그는 공식 확인이 없어 어댑터 권장(안전한 쪽)
        assertTrue("voltage_check" in ids("JP"))
        assertTrue("plug_adapter" in ids("JP"))
        assertFalse("voltage_check" in ids("MY"))
        // 필리핀: 관광부가 곳에 따라 11자(납작한 핀) 콘센트도 있다고 안내 — 맞는지 한마디로 말할 수 없어(null) 어댑터 권장. 베트남: 한국 플러그가 맞는 곳이 많다(외교부)
        assertTrue("plug_adapter" in ids("PH"))
        assertFalse("plug_adapter" in ids("VN"))
        assertFalse("voltage_check" in ids("PH"))
        assertFalse("voltage_check" in ids("VN"))
        assertEquals(0, TestPacks.remoteCalls)
    }

    @Test fun financialLinksAreNeverAffiliate() {
        // 보험·금융은 수수료 없는 공식 안내만 (작업 규칙 11)
        val insurance = index.essentials.first { it.id == "travel_insurance" }
        assertEquals("official_info", insurance.link?.type)
        index.essentials.filter { Essentials.isAffiliate(it) }.forEach {
            assertFalse(listOf("보험", "환전", "카드", "금융").any { w -> w in it.nameKo })
        }
        // 보조배터리 규정 배지
        assertEquals("carry_on_only", index.essentials.first { it.id == "power_bank" }.ruleBadge)
    }

    @Test fun everyCountryHasSourcedPowerAndShopping() {
        countries.forEach { c ->
            val p = pack(c)
            val ids = p.sources.map { it.id }.toSet()
            assertNotNull(c, p.power)
            assertTrue(c, p.power!!.source in ids)
            assertTrue(c, p.shopping.isNotEmpty())
            p.shopping.forEach { s ->
                assertTrue("$c ${s.id}", s.source in ids && s.importSource in ids)
                assertTrue("$c ${s.id}", s.category in setOf("food", "daily", "souvenir"))
            }
        }
    }

    @Test fun meatAndFreshFruitAreProhibited() {
        assertEquals(ImportStatus.Prohibited, pack("SG").shopping.first { it.id == "bakkwa" }.import)
        assertEquals(ImportStatus.Prohibited, pack("TH").shopping.first { it.id == "fresh_mango" }.import)
        assertEquals(ImportStatus.Prohibited, pack("PH").shopping.first { it.id == "fresh_mango" }.import)
    }

    @Test fun returnInfoIsOfficial() {
        assertTrue(index.returnLinks.all { it.url.contains("customs.go.kr") || it.url.contains("apqa.go.kr") })
        val ids = index.sources.map { it.id }.toSet()
        assertTrue(index.returnFacts.isNotEmpty() && index.returnFacts.all { it.source in ids })
    }
}
