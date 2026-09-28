package com.readyport.ui.form

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.BuildConfig
import com.readyport.autofill.FieldReport
import com.readyport.autofill.FieldReporter
import com.readyport.autofill.FieldValue
import com.readyport.autofill.FormValues
import com.readyport.autofill.Recipe
import com.readyport.autofill.SafeClipboard
import com.readyport.pack.FormInfo
import com.readyport.pack.PackRepository
import com.readyport.pack.PackVersionSource
import com.readyport.ui.nav.FormConfirmRoute
import com.readyport.vault.FormRecord
import com.readyport.vault.WalletRepository
import com.readyport.vault.WalletState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject

/** 입국 서류 화면들이 공통으로 쓰는 준비물: 레시피, 국가 팩의 양식 안내, 안전 스위치 */
data class FormContext(
    val formId: String,
    val form: FormInfo?,
    val recipe: Recipe?,
    val recipeVersion: String?,
    val killed: Boolean,
) {
    /** 자동 입력을 쓸 수 있는지. 레시피가 없거나 스위치가 꺼졌으면 수동 모드 (ARCHITECTURE 9.2) */
    val autofillAvailable: Boolean get() = recipe != null && !killed
}

private suspend fun loadContext(formId: String, packs: PackRepository, versions: PackVersionSource): FormContext {
    val country = formId.substringBefore('_')
    val form = packs.pack(country)?.value?.forms?.firstOrNull { it.id == formId }
    val recipe = packs.recipe(formId)
    return FormContext(formId, form, recipe?.value, recipe?.version, versions.autofillKilled(formId))
}

// ---------------- 3개 국어 확인 (PRD 5.2) ----------------

data class ConfirmUi(
    val context: FormContext? = null,
    val wallet: WalletState = WalletState.Locked(false),
    val values: Map<String, FieldValue> = emptyMap(),
    /** 사용자가 이번에 적거나 고른 값 (메모리에만. 저장은 '맞아요'를 누를 때 암호화 보관함에) */
    val draft: Map<String, String> = emptyMap(),
    val saveFailed: Boolean = false,
)

@HiltViewModel
class FormConfirmViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val packs: PackRepository,
    private val versions: PackVersionSource,
    private val wallet: WalletRepository,
) : ViewModel() {
    private val formId = handle.toRoute<FormConfirmRoute>().formId
    private val _ui = MutableStateFlow(ConfirmUi())
    val ui: StateFlow<ConfirmUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val ctx = loadContext(formId, packs, versions)
            _ui.update { it.copy(context = ctx) }
            wallet.state.collect { state -> onWallet(state) }
        }
    }

    private fun onWallet(state: WalletState) {
        val ctx = _ui.value.context ?: return
        val contents = (state as? WalletState.Unlocked)?.contents
        val saved = contents?.forms?.get(formId)?.values.orEmpty()
        // 처음 열 때만 저장된 값·기본 제안으로 초안을 채운다
        val draft = _ui.value.draft.ifEmpty { FormValues.defaults(ctx.recipe ?: return@ifEmpty saved) + saved }
        val values = if (contents != null && ctx.recipe != null) FormValues.build(ctx.recipe, contents, draft) else emptyMap()
        _ui.update { it.copy(wallet = state, draft = draft, values = values) }
    }

    fun unlock() = viewModelScope.launch { wallet.unlock() }

    fun setValue(key: String, value: String) {
        _ui.update { ui ->
            val draft = ui.draft + (key to value)
            val contents = (ui.wallet as? WalletState.Unlocked)?.contents
            val recipe = ui.context?.recipe
            val values = if (contents != null && recipe != null) FormValues.build(recipe, contents, draft) else ui.values
            ui.copy(draft = draft, values = values)
        }
    }

    /** '맞아요, 입력해 주세요' — 확인한 값을 암호화 보관함에 저장 */
    suspend fun confirm(): WalletRepository.SaveResult {
        val draft = _ui.value.draft.filterValues { it.isNotBlank() }
        val result = wallet.update { c ->
            c.copy(forms = c.forms + (formId to FormRecord(formId, draft, "confirmed", LocalDateTime.now().toString())))
        }
        _ui.update { it.copy(saveFailed = result != WalletRepository.SaveResult.Saved) }
        return result
    }
}

// ---------------- 자동 입력 (PRD 5.3) · 수동 모드 ----------------

data class AutofillUi(
    val context: FormContext? = null,
    val values: Map<String, FieldValue> = emptyMap(),
    val engine: String? = null,
    val locked: Boolean = false,
    val submitted: Boolean = false,
)

@HiltViewModel
class AutofillViewModel @Inject constructor(
    handle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val packs: PackRepository,
    private val versions: PackVersionSource,
    private val wallet: WalletRepository,
    private val reporter: FieldReporter,
    val clipboard: SafeClipboard,
) : ViewModel() {
    // AutofillRoute·FormManualRoute 모두 formId 인자를 가진다
    private val formId: String = checkNotNull(handle["formId"])
    private val _ui = MutableStateFlow(AutofillUi())
    val ui: StateFlow<AutofillUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val ctx = loadContext(formId, packs, versions)
            // 엔진은 앱에 내장된 파일만 쓴다. 원격 JavaScript는 실행하지 않는다 (PRD 7.5)
            val engine = withContext(Dispatchers.IO) {
                appContext.assets.open("autofill/engine.js").use { it.readBytes().decodeToString() }
            }
            _ui.update { it.copy(context = ctx, engine = engine) }
            if (ctx.killed) report("-", "kill_switch")
            wallet.state.collect { state ->
                val contents = (state as? WalletState.Unlocked)?.contents
                val values = if (contents != null && ctx.recipe != null) {
                    FormValues.build(ctx.recipe, contents, contents.forms[formId]?.values.orEmpty())
                } else emptyMap()
                _ui.update { it.copy(values = values, locked = contents == null) }
            }
        }
    }

    fun unlock() = viewModelScope.launch { wallet.unlock() }

    /** 지금 보이는 단계를 채울 계획 (JS 호출 문자열) */
    fun fillScript(visibleSteps: Set<String>): String? {
        val recipe = _ui.value.context?.recipe ?: return null
        val plan = FormValues.plan(recipe, _ui.value.values, visibleSteps)
        return "window.__readyport ? window.__readyport.fill($plan) : null"
    }

    fun probeScript(): String? {
        val recipe = _ui.value.context?.recipe ?: return null
        val probes = recipe.steps.map { it.probe ?: "__none__" }
        return "window.__readyport ? window.__readyport.probe(${kotlinx.serialization.json.JsonArray(probes.map { kotlinx.serialization.json.JsonPrimitive(it) })}) : null"
    }

    fun readScript(keys: Collection<String>): String? {
        val recipe = _ui.value.context?.recipe ?: return null
        val map = recipe.fields.filter { it.key in keys && it.selector != null }
            .associate { it.key to kotlinx.serialization.json.JsonPrimitive(it.selector!!) }
        return "window.__readyport ? window.__readyport.read(${kotlinx.serialization.json.JsonObject(map)}) : null"
    }

    fun onSubmittedPage() {
        if (_ui.value.submitted) return
        _ui.update { it.copy(submitted = true) }
        viewModelScope.launch {
            wallet.update { c ->
                val now = LocalDateTime.now().toString()
                val rec = c.forms[formId] ?: FormRecord(formId, emptyMap(), "confirmed", now)
                c.copy(forms = c.forms + (formId to rec.copy(status = "submitted", submittedAt = now, updatedAt = now)))
            }
        }
    }

    /** 개인정보 없는 실패 리포트 */
    fun report(stepId: String, code: String, siteVersion: String? = null) {
        val ctx = _ui.value.context ?: return
        reporter.report(
            FieldReport(
                formId = formId,
                packVersion = ctx.recipeVersion ?: "none",
                stepId = stepId,
                errorCode = code,
                appVersion = BuildConfig.VERSION_NAME,
                ts = System.currentTimeMillis(),
                siteVersion = siteVersion,
            ),
        )
    }
}
