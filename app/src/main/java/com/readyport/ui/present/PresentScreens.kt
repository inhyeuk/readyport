package com.readyport.ui.present

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.SendToMobile
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.readyport.R
import com.readyport.pack.PackRepository
import com.readyport.security.SecureScreen
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.DangerButton
import com.readyport.ui.components.DestructiveConfirm
import com.readyport.ui.components.EmptyState
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.KeyValueRow
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.ListRow
import com.readyport.ui.components.LocalAppActions
import com.readyport.ui.components.LocalShowBack
import com.readyport.ui.components.LockedState
import com.readyport.ui.components.NewsStyle
import com.readyport.ui.components.OnDark
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.RowTrailing
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SecurityBanner
import com.readyport.ui.components.SelectChip
import com.readyport.ui.components.StatusChip
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.TextCircle
import com.readyport.ui.components.TileGrid
import com.readyport.ui.components.TrailingFlow
import com.readyport.ui.components.minTouchSize
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.LocalTypeExtras
import com.readyport.ui.theme.Tokens
import com.readyport.ui.wallet.ConsentRow
import com.readyport.ui.wallet.DisabledReason
import com.readyport.ui.wallet.KeepText
import com.readyport.ui.wallet.SpokenAs
import com.readyport.ui.wallet.keepWords
import com.readyport.ui.wallet.maskName
import com.readyport.ui.wallet.maskNumber
import com.readyport.ui.wallet.rememberDeviceAuth
import com.readyport.vault.EntryDoc
import com.readyport.vault.TravelCompanion
import com.readyport.vault.WalletRepository
import com.readyport.vault.WalletState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

// ======================= 입국 때 보여 주기 (PRD 5.4) =======================

data class Traveler(val id: String, val label: String)

data class DocView(
    val doc: EntryDoc,
    val formName: String,
    val image: ImageBitmap?,
    /** 가린 이름·여권 번호 (PRD 5.4) */
    val maskedName: String?,
    val maskedPassport: String?,
)

data class PresentUi(
    val locked: Boolean = true,
    val travelers: List<Traveler> = emptyList(),
    val docs: List<DocView> = emptyList(),
)

@HiltViewModel
class PresentViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wallet: WalletRepository,
    private val packs: PackRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(PresentUi())
    val ui: StateFlow<PresentUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch { wallet.state.collect { refresh(it) } }
    }

    private suspend fun refresh(state: WalletState) {
        val c = (state as? WalletState.Unlocked)?.contents
        if (c == null) { _ui.value = PresentUi(locked = true); return }
        val self = context.getString(R.string.present_self)
        val travelers = listOf(Traveler("self", self)) + c.companions.map { Traveler(it.id, it.label) }
        val docs = c.entryDocs.sortedByDescending { it.savedAt }.map { d ->
            val passport = if (d.travelerId == "self") c.passport else c.companions.firstOrNull { it.id == d.travelerId }?.passport
            val formName = packs.pack(d.formId.substringBefore('_'))?.value?.forms?.firstOrNull { it.id == d.formId }?.nameKo ?: d.formId
            val bytes = wallet.readBlob(d.blobId)
            val image = bytes?.let { withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
            DocView(d, formName, image, passport?.let { maskName(it.surname, it.givenNames) }, passport?.let { maskNumber(it.documentNumber) })
        }
        _ui.value = PresentUi(locked = false, travelers = travelers, docs = docs)
    }

    fun unlock() = viewModelScope.launch { wallet.unlock() }

    /** 메일로 받은 QR 사진, 보호자 폰에서 보낸 QR 사진을 지갑에 넣는다 */
    fun addFromPhoto(uri: Uri, travelerId: String, formId: String) = viewModelScope.launch {
        val png = withContext(Dispatchers.IO) { readAsPng(uri) } ?: return@launch
        val blob = wallet.writeBlob(png) ?: return@launch
        wallet.update { c ->
            c.copy(entryDocs = c.entryDocs + EntryDoc(
                id = UUID.randomUUID().toString(), formId = formId, travelerId = travelerId, blobId = blob,
                source = "import", savedAt = LocalDateTime.now().toString(),
            ))
        }
    }

    fun delete(doc: EntryDoc) = viewModelScope.launch {
        if (wallet.update { c -> c.copy(entryDocs = c.entryDocs.filterNot { it.id == doc.id }) } == WalletRepository.SaveResult.Saved) {
            wallet.deleteBlob(doc.blobId)
        }
    }

    /**
     * 다른 폰(자녀 폰)으로 보내기: 서버를 거치지 않고 시스템 공유로 기기끼리 (PRD 3.3).
     * 복호화한 그림을 잠깐 캐시에 두었다가 2분 뒤 지운다.
     */
    fun share(doc: EntryDoc, launch: (Intent) -> Unit) = viewModelScope.launch {
        val bytes = wallet.readBlob(doc.blobId) ?: return@launch
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "entry-${doc.id.take(8)}.png")
        withContext(Dispatchers.IO) { f.writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        launch(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                null,
            ),
        )
        delay(120_000)
        f.delete()
    }

    private fun readAsPng(uri: Uri): ByteArray? = runCatching {
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        ByteArrayOutputStream().use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out); bmp.recycle(); out.toByteArray() }
    }.getOrNull()
}

@Composable
fun PresentScreen(defaultFormId: String?, viewModel: PresentViewModel = hiltViewModel()) {
    SecureScreen()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val auth = rememberDeviceAuth()
    val activity = LocalActivity.current
    var targetTraveler by remember { mutableStateOf("self") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addFromPhoto(uri, targetTraveler, defaultFormId ?: "OTHER")
    }
    PresentContent(
        ui = ui,
        onUnlock = { auth { viewModel.unlock() } },
        onAddPhoto = { traveler ->
            targetTraveler = traveler
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onShare = { doc -> viewModel.share(doc) { activity?.startActivity(it) } },
        onDelete = viewModel::delete,
    )
}

/**
 * 21 입국 때 보여 주기 (DESIGN_SPEC 6-21). 제목 옆 `비행기 모드에서도 보여요`, 맨 위 compact SecurityBanner.
 * 서류 한 장 = Navy '보여 주기' 카드(onDark 내용 세트만) + 카드 밖 오른쪽 지우기(DangerButton + 확인 대화상자 — D8·D18).
 */
@Composable
fun PresentContent(
    ui: PresentUi,
    onUnlock: () -> Unit,
    onAddPhoto: (String) -> Unit,
    onShare: (EntryDoc) -> Unit,
    onDelete: (EntryDoc) -> Unit,
) {
    var traveler by remember { mutableStateOf("self") }
    var bright by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<EntryDoc?>(null) }
    MaxBrightness(bright)
    val title = stringResource(R.string.present_title)
    AppScreen(
        title = title,
        speech = stringResource(R.string.present_speech),
        header = { PresentHeader(title) },
    ) {
        item(key = "security") { SecurityBanner(compact = true) }
        if (ui.locked) {
            item(key = "locked") {
                LockedState(
                    title = keepWords(stringResource(R.string.wallet_locked_title)),
                    body = keepWords(stringResource(R.string.wallet_locked_body)),
                    buttonLabel = keepWords(stringResource(R.string.wallet_unlock)),
                    onUnlock = onUnlock,
                    icon = Icons.Outlined.QrCode2,
                    badgeIcon = Icons.Outlined.Lock,
                )
            }
            return@AppScreen
        }
        if (ui.travelers.size > 1) {
            item(key = "travelers") {
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ui.travelers.forEach { t ->
                        SelectChip(selected = traveler == t.id, onClick = { traveler = t.id }, label = t.label, leadingIcon = Icons.Outlined.Person)
                    }
                }
            }
        }
        // 켬·끔 상태가 있는 토글이라 버튼이 아니라 스위치 행 (6-21)
        item(key = "bright") {
            ListGroup {
                ListRow(
                    title = keepWords(stringResource(R.string.present_brightness)),
                    icon = Icons.Outlined.LightMode,
                    trailing = RowTrailing.Switch(bright) { bright = it },
                )
            }
        }
        val mine = ui.docs.filter { it.doc.travelerId == traveler }
        if (mine.isEmpty()) {
            item(key = "none") {
                EmptyState(
                    icon = Icons.Outlined.QrCode2,
                    title = keepWords(stringResource(R.string.present_empty_title)),
                    body = keepWords(stringResource(R.string.present_empty_body)),
                    tone = BadgeTone.Accent,
                    action = {
                        PrimaryButton(
                            keepWords(stringResource(R.string.present_add_photo)),
                            onClick = { onAddPhoto(traveler) },
                            icon = Icons.Outlined.AddPhotoAlternate,
                        )
                    },
                )
            }
        } else {
            mine.forEach { d ->
                item(key = "doc-${d.doc.id}") { DocCard(d, onShare = { onShare(d.doc) }, onDelete = { pendingDelete = d.doc }) }
            }
            item(key = "add") {
                SecondaryButton(
                    keepWords(stringResource(R.string.present_add_photo)),
                    onClick = { onAddPhoto(traveler) },
                    icon = Icons.Outlined.AddPhotoAlternate,
                )
            }
        }
    }
    pendingDelete?.let { doc ->
        DestructiveConfirm(
            title = keepWords(stringResource(R.string.present_delete_confirm_title)),
            body = keepWords(stringResource(R.string.present_delete_confirm_body)),
            confirmLabel = keepWords(stringResource(R.string.present_delete)),
            onConfirm = { pendingDelete = null; onDelete(doc) },
            onDismiss = { pendingDelete = null },
            secure = true,
        )
    }
}

/**
 * 제목 + `비행기 모드에서도 보여요` 칩. 칩을 옆에 두면 제목이 더 꺾일 만큼 폭이 모자라면(큰 글자) 제목 아래 줄로 내린다 —
 * 제목이 한 음절씩 세로로 쪼개지지 않게. 서브 화면이면 AppScreen 기본 제목 줄처럼 뒤로 버튼을 둔다.
 */
@Composable
private fun PresentHeader(title: String) {
    val dimens = LocalDimens.current
    val actions = LocalAppActions.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (LocalShowBack.current) {
            IconButton(onClick = actions.goBack, modifier = Modifier.minTouchSize()) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    modifier = Modifier.size(if (dimens.easyMode) 32.dp else 24.dp),
                )
            }
        }
        TrailingFlow(
            trailing = {
                // 칩 글자도 어절 중간에서 꺾지 않는다(`보여/요` 방지) — 읽는 글자는 원문
                val offline = stringResource(R.string.present_offline)
                val shown = keepWords(offline)
                SpokenAs(offline, shown) {
                    StatusChip(shown, container = Tokens.SuccessBg, content = Tokens.SuccessText, icon = Icons.Outlined.AirplanemodeActive)
                }
            },
            modifier = Modifier.weight(1f),
            centerVertically = true,
        ) {
            KeepText(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = Tokens.Ink,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

/** 입국 서류 한 장: Navy 카드(QR 그림·확인 번호·가린 값·보내기) + 카드 밖 오른쪽 지우기 */
@Composable
private fun DocCard(d: DocView, onShare: () -> Unit, onDelete: () -> Unit) {
    val dimens = LocalDimens.current
    val facts = listOfNotNull(
        d.maskedName?.let { stringResource(R.string.wallet_passport_name) to it },
        d.maskedPassport?.let { stringResource(R.string.wallet_passport_number) to it },
        d.doc.arrivalDate?.let { stringResource(R.string.present_field_arrival) to it },
        d.doc.flightNo?.let { stringResource(R.string.wallet_booking_flights) to it },
    )
    Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
        CardNewsCard(
            title = keepWords(d.formName),
            icon = Icons.Outlined.QrCode2,
            style = NewsStyle.Navy,
            trailing = {
                val submitted = stringResource(R.string.present_submitted)
                val shown = keepWords(submitted)
                SpokenAs(submitted, shown) { StatusTag(shown, StatusKind.Verified) }
            },
        ) {
            if (d.image != null) {
                // 심사관이 찍는 QR·확인 화면: 카드 폭 전체 + 흰 8dp 여백(QR 조용한 영역)
                Image(
                    bitmap = d.image,
                    contentDescription = stringResource(R.string.present_doc_image, d.formName),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(Tokens.Surface)
                        .padding(8.dp),
                )
            } else {
                // 그림을 못 열었으면 QR이 있는 것처럼 보이지 않게 그렇다고 말하고 아래 값을 보여 주게 한다
                ImageMissing()
            }
            d.doc.confirmationNo?.let { ConfirmationNumber(stringResource(R.string.present_confirmation), it) }
            if (facts.isNotEmpty()) {
                TileGrid(facts, columns = rememberGridColumns()) { (label, value), cell ->
                    KeyValueRow(label = label, value = value, modifier = cell, onDark = true)
                }
            }
            SecondaryButton(
                keepWords(stringResource(R.string.present_share)),
                onClick = onShare,
                icon = Icons.AutoMirrored.Outlined.SendToMobile,
                onDark = true,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DangerButton(keepWords(stringResource(R.string.present_delete)), onClick = onDelete)
        }
    }
}

/** 서류 그림을 열지 못했을 때 (Navy 카드 안 — onDark 색만): 깨진 그림 아이콘 + 안내 */
@Composable
private fun ImageMissing() {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.BrokenImage,
            contentDescription = null,
            tint = OnDark.secondary,
            modifier = Modifier.size(LocalDimens.current.icon),
        )
        KeepText(
            stringResource(R.string.present_image_missing),
            style = MaterialTheme.typography.bodyMedium,
            color = OnDark.secondary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 확인 번호: 라벨(White80) + 큰 값(statSmall, Surface) — 심사관이 한눈에 읽게. 한 번에 읽는다 */
@Composable
private fun ConfirmationNumber(label: String, value: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = OnDark.secondary)
        Text(value, style = LocalTypeExtras.current.statSmall, color = OnDark.content)
    }
}

/** '밝기 최대로': 이 화면에 있는 동안만 창 밝기를 최대로 */
@Composable
private fun MaxBrightness(on: Boolean) {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(on) {
        val lp = window.attributes
        lp.screenBrightness = if (on) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
        onDispose {
            val reset = window.attributes
            reset.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = reset
        }
    }
}

// ======================= 가족 모드: 같이 가는 사람 (PRD 3.3) =======================

@HiltViewModel
class CompanionsViewModel @Inject constructor(private val wallet: WalletRepository) : ViewModel() {
    val state: StateFlow<WalletState> = wallet.state
    fun unlock() = viewModelScope.launch { wallet.unlock() }

    fun add(label: String) = viewModelScope.launch {
        wallet.update { c ->
            c.copy(companions = c.companions + TravelCompanion(UUID.randomUUID().toString(), label.trim(), null, true, LocalDateTime.now().toString()))
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        val docs = wallet.contentsOrNull?.entryDocs.orEmpty().filter { it.travelerId == id }
        if (wallet.update { c -> c.copy(companions = c.companions.filterNot { it.id == id }, entryDocs = c.entryDocs.filterNot { it.travelerId == id }) } ==
            WalletRepository.SaveResult.Saved
        ) docs.forEach { wallet.deleteBlob(it.blobId) }
    }
}

@Composable
fun CompanionsScreen(onRegisterPassport: (String) -> Unit, viewModel: CompanionsViewModel = hiltViewModel()) {
    SecureScreen()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val auth = rememberDeviceAuth()
    CompanionsContent(state, { auth { viewModel.unlock() } }, viewModel::add, viewModel::delete, onRegisterPassport)
}

/**
 * 27 같이 가는 사람 (DESIGN_SPEC 6-27). 맨 위 compact SecurityBanner. 사람마다 이니셜 아바타 + 이름 + 여권 등록(됨) + 지우기(확인 대화상자,
 * 이름은 대화상자에 넣지 않는다). 추가 폼은 흰 카드 — 동의 체크 없이는 추가할 수 없다(PRD 3.3, 8.2).
 */
@Composable
fun CompanionsContent(
    state: WalletState,
    onUnlock: () -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRegisterPassport: (String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val contents = (state as? WalletState.Unlocked)?.contents
    val empty = contents != null && contents.companions.isEmpty()
    AppScreen(
        title = stringResource(R.string.companions_title),
        // 비어 있으면 같은 문장을 빈 상태 안내로 보이므로 부제는 생략한다(같은 글 두 번 금지)
        subtitle = if (empty) null else keepWords(stringResource(R.string.wallet_companions_body)),
        speech = stringResource(R.string.companions_body),
    ) {
        item(key = "security") { SecurityBanner(compact = true) }
        if (contents == null) {
            item(key = "locked") {
                LockedState(
                    title = keepWords(stringResource(R.string.wallet_locked_title)),
                    body = keepWords(stringResource(R.string.wallet_locked_body)),
                    buttonLabel = keepWords(stringResource(R.string.wallet_unlock)),
                    onUnlock = onUnlock,
                    icon = Icons.Outlined.FamilyRestroom,
                )
            }
            return@AppScreen
        }
        if (empty) {
            item(key = "empty") {
                // 제목이 이미 '보호자 폰 하나로 가족 서류'를 말하므로 본문은 새 정보(사람마다 여권·서류)만
                EmptyState(
                    icon = Icons.Outlined.FamilyRestroom,
                    title = keepWords(stringResource(R.string.companion_empty_title)),
                    body = keepWords(stringResource(R.string.companion_empty_body)),
                    tone = BadgeTone.Accent,
                )
            }
        }
        contents.companions.forEach { c ->
            item(key = "c-${c.id}") {
                CompanionCard(c, onRegisterPassport = { onRegisterPassport(c.id) }, onDelete = { pendingDelete = c.id })
            }
        }
        item(key = "add") {
            val canAdd = consent && label.isNotBlank()
            CardNewsCard(title = keepWords(stringResource(R.string.companion_add)), icon = Icons.Outlined.PersonAdd) {
                CompanionNameField(label) { label = it }
                KeepText(stringResource(R.string.companion_consent), style = MaterialTheme.typography.bodyLarge, color = Tokens.Ink)
                ConsentRow(
                    text = stringResource(R.string.companion_consent_yes),
                    checked = consent,
                    onCheckedChange = { consent = it },
                )
                // 동의 확인 없이는 추가할 수 없다 (PRD 3.3, 8.2)
                PrimaryButton(keepWords(stringResource(R.string.companion_add)), enabled = canAdd, icon = Icons.Outlined.PersonAdd, onClick = {
                    onAdd(label); label = ""; consent = false
                })
                if (!canAdd) DisabledReason(stringResource(R.string.companion_add_hint))
            }
        }
    }
    pendingDelete?.let { id ->
        DestructiveConfirm(
            title = keepWords(stringResource(R.string.companion_delete_confirm_title)),
            body = keepWords(stringResource(R.string.companion_delete_confirm_body)),
            confirmLabel = keepWords(stringResource(R.string.companion_delete)),
            onConfirm = { pendingDelete = null; onDelete(id) },
            onDismiss = { pendingDelete = null },
            secure = true,
        )
    }
}

/**
 * 부르는 이름 칸 (6-27 `label = companion_label`): 테두리 홈에 들어가는 라벨은 짧게(`부르는 이름`) 그리고 예시는 칸 아래에 둔다 —
 * 200%에서 긴 라벨이 두 줄로 꺾여 아이콘·테두리 위에 겹치지 않게. TalkBack·테스트가 읽는 칸 이름은 원문 `companion_label`
 * 그대로이고(예시 포함), 칸 아래 예시는 같은 말을 두 번 읽지 않게 숨긴다.
 */
@Composable
private fun CompanionNameField(value: String, onChange: (String) -> Unit) {
    val name = stringResource(R.string.companion_label)
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.replace("\n", "")) },
        label = {
            Text(stringResource(R.string.companion_label_short), modifier = Modifier.semantics { text = AnnotatedString(name) })
        },
        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
        supportingText = { Text(stringResource(R.string.companion_label_example), modifier = Modifier.clearAndSetSemantics {}) },
        singleLine = false,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 같이 가는 사람 한 명: 이니셜 원(글자 크기에 맞춰 커짐, 장식) + 이름 + 여권 상태, 아래 줄 오른쪽에 여권 등록·지우기 */
@Composable
private fun CompanionCard(c: TravelCompanion, onRegisterPassport: () -> Unit, onDelete: () -> Unit) {
    val dimens = LocalDimens.current
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.gap)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextCircle(
                    initialOf(c.label),
                    modifier = Modifier.clearAndSetSemantics {},
                    minSize = dimens.iconBadge,
                    container = Tokens.AccentSoft,
                    content = Tokens.Accent,
                    style = MaterialTheme.typography.titleMedium,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeepText(c.label, style = MaterialTheme.typography.titleLarge, color = Tokens.Ink)
                    if (c.passport != null) {
                        val done = stringResource(R.string.companion_passport_done)
                        val shown = keepWords(done)
                        SpokenAs(done, shown) { StatusTag(shown, StatusKind.Verified) }
                    }
                }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (c.passport == null) {
                    SecondaryButton(
                        keepWords(stringResource(R.string.companion_passport_add)),
                        onClick = onRegisterPassport,
                        icon = Icons.Outlined.Badge,
                        fillWidth = false,
                    )
                }
                DangerButton(keepWords(stringResource(R.string.companion_delete)), onClick = onDelete)
            }
        }
    }
}

/** 이름 첫 글자(이모지·한글 조합 문자도 한 글자로) */
private fun initialOf(label: String): String {
    val t = label.trim()
    if (t.isEmpty()) return "?"
    return String(Character.toChars(t.codePointAt(0)))
}

/** 제출 완료 화면을 그림으로 저장 (WebView 캡처 → 암호화) */
suspend fun saveCapture(wallet: WalletRepository, png: ByteArray, formId: String, arrivalDate: String?, flightNo: String?): Boolean {
    val blob = wallet.writeBlob(png) ?: return false
    return wallet.update { c ->
        c.copy(entryDocs = c.entryDocs + EntryDoc(
            id = UUID.randomUUID().toString(), formId = formId, travelerId = "self", blobId = blob,
            arrivalDate = arrivalDate, flightNo = flightNo, source = "capture", savedAt = LocalDateTime.now().toString(),
        ))
    } == WalletRepository.SaveResult.Saved
}
