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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.StatusChip
import com.readyport.ui.components.TopicCard
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.ui.wallet.maskName
import com.readyport.ui.wallet.maskNumber
import com.readyport.ui.wallet.rememberDeviceAuth
import com.readyport.vault.TravelCompanion
import com.readyport.vault.EntryDoc
import com.readyport.vault.WalletRepository
import com.readyport.vault.WalletState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    MaxBrightness(bright)
    AppScreen(
        title = stringResource(R.string.present_title),
        speech = stringResource(R.string.present_speech),
        headerActions = { StatusChip(stringResource(R.string.present_offline), Tokens.SuccessBg, Tokens.SuccessText) },
    ) {
        if (ui.locked) {
            item(key = "locked") {
                InfoCard {
                    Text(stringResource(R.string.wallet_locked_title), style = MaterialTheme.typography.titleMedium)
                    PrimaryButton(stringResource(R.string.wallet_unlock), onClick = onUnlock)
                }
            }
            return@AppScreen
        }
        if (ui.travelers.size > 1) {
            item(key = "travelers") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.travelers.forEach { t ->
                        FilterChip(
                            selected = traveler == t.id,
                            onClick = { traveler = t.id },
                            label = { Text(t.label, style = MaterialTheme.typography.labelLarge) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
        item(key = "bright") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.toggleable(bright, role = Role.Switch, onValueChange = { bright = it }).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.present_brightness), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = bright, onCheckedChange = null)
            }
        }
        val mine = ui.docs.filter { it.doc.travelerId == traveler }
        if (mine.isEmpty()) {
            item(key = "none") { TopicCard(stringResource(R.string.present_none), null, tone = CardTone.Notice) }
        }
        mine.forEach { d ->
            item(key = "doc-${d.doc.id}") {
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.formName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        StatusChip(stringResource(R.string.present_submitted), Tokens.SuccessBg, Tokens.SuccessText)
                    }
                    d.image?.let {
                        Image(
                            bitmap = it,
                            contentDescription = stringResource(R.string.present_doc_image, d.formName),
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().background(Tokens.Surface),
                        )
                    }
                    d.doc.confirmationNo?.let { Text("${stringResource(R.string.present_confirmation)}: $it", style = MaterialTheme.typography.titleMedium) }
                    listOfNotNull(d.maskedName, d.doc.arrivalDate, d.doc.flightNo, d.maskedPassport).takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onShare(d.doc) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.present_share)) }
                        TextButton(onClick = { onDelete(d.doc) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.present_delete)) }
                    }
                }
            }
        }
        item(key = "add") {
            OutlinedButton(onClick = { onAddPhoto(traveler) }, modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight)) {
                Text(stringResource(R.string.present_add_photo), style = MaterialTheme.typography.labelLarge)
            }
        }
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
    AppScreen(
        title = stringResource(R.string.companions_title),
        subtitle = stringResource(R.string.companions_body),
        speech = stringResource(R.string.companions_body),
    ) {
        val contents = (state as? WalletState.Unlocked)?.contents
        if (contents == null) {
            item(key = "locked") {
                InfoCard {
                    Text(stringResource(R.string.wallet_locked_title), style = MaterialTheme.typography.titleMedium)
                    PrimaryButton(stringResource(R.string.wallet_unlock), onClick = onUnlock)
                }
            }
            return@AppScreen
        }
        contents.companions.forEach { c ->
            item(key = "c-${c.id}") {
                InfoCard {
                    Text(c.label, style = MaterialTheme.typography.titleLarge)
                    if (c.passport != null) {
                        StatusChip(stringResource(R.string.companion_passport_done), Tokens.SuccessBg, Tokens.SuccessText)
                    } else {
                        PrimaryButton(stringResource(R.string.companion_passport_add), onClick = { onRegisterPassport(c.id) })
                    }
                    TextButton(onClick = { onDelete(c.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.companion_delete)) }
                }
            }
        }
        item(key = "add") {
            InfoCard(tone = CardTone.Caution) {
                Text(stringResource(R.string.companion_add), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text(stringResource(R.string.companion_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.companion_consent), style = MaterialTheme.typography.bodyLarge)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.heightIn(min = 48.dp).toggleable(consent, role = Role.Checkbox, onValueChange = { consent = it }),
                ) {
                    Checkbox(checked = consent, onCheckedChange = null)
                    Text(stringResource(R.string.companion_consent_yes), style = MaterialTheme.typography.labelLarge)
                }
                // 동의 확인 없이는 추가할 수 없다 (PRD 3.3, 8.2)
                PrimaryButton(stringResource(R.string.companion_add), enabled = consent && label.isNotBlank(), onClick = {
                    onAdd(label); label = ""; consent = false
                })
            }
        }
    }
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
