package com.readyport.ui.wallet

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.common.InputImage
import com.readyport.R
import com.readyport.doc.mrz.MrzCheck
import com.readyport.doc.mrz.MrzData
import com.readyport.security.SecureScreen
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.CardTone
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.StatusChip
import com.readyport.ui.components.TopicCard
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.vault.PassportRecord
import com.readyport.vault.WalletRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.Executors

private val PassportSteps = listOf(R.string.passport_step_scan, R.string.passport_step_chip, R.string.passport_step_confirm)

/** 단계 표시: 촬영 → 칩 확인(선택) → 값 확인 (PRD 5.5) */
@Composable
private fun PassportStepper(current: Int) {
    val labels = PassportSteps.map { stringResource(it) }
    val desc = stringResource(R.string.passport_steps_desc, labels[current], current + 1, labels.size)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = desc },
    ) {
        labels.forEachIndexed { i, label ->
            StatusChip(
                "${i + 1}. $label",
                container = if (i == current) Tokens.Accent else Tokens.AccentSoft,
                content = if (i == current) Tokens.Surface else Tokens.Ink,
            )
        }
    }
}

// ---------------- 1. 안내·동의 ----------------

@Composable
fun PassportIntroScreen(
    viewModel: PassportFlowViewModel,
    onCamera: () -> Unit,
    onManual: () -> Unit,
    onFound: () -> Unit,
) {
    SecureScreen()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.readPhoto(uri)
    }
    LaunchedEffect(scan) { if (scan is ScanState.Found) onFound() }
    PassportIntroContent(
        scan = scan,
        onCamera = onCamera,
        onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onManual = onManual,
    )
}

/** 여권 등록 소개 — 상태 없는 화면(갤러리·접근성 점검이 그대로 띄운다). 동작은 PassportIntroScreen과 같다 */
@Composable
fun PassportIntroContent(scan: ScanState, onCamera: () -> Unit, onPickPhoto: () -> Unit, onManual: () -> Unit) {
    var agreed by remember { mutableStateOf(false) }
    AppScreen(
        title = stringResource(R.string.passport_title),
        speech = stringResource(R.string.passport_speech),
    ) {
        item(key = "steps") { PassportStepper(current = 0) }
        item(key = "body") { Text(stringResource(R.string.passport_intro_body), style = MaterialTheme.typography.bodyLarge) }
        // 카메라·여권 처리에 대한 눈에 띄는 고지와 동의 (PRD 8.1 민감 정보)
        item(key = "privacy") {
            InfoCard(tone = CardTone.Notice) {
                listOf(R.string.passport_privacy_1, R.string.passport_privacy_2, R.string.passport_privacy_3).forEach {
                    Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .toggleable(agreed, role = Role.Checkbox, onValueChange = { agreed = it }),
                ) {
                    Checkbox(checked = agreed, onCheckedChange = null)
                    Text(stringResource(R.string.passport_consent), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (scan == ScanState.NotFound) {
            item(key = "not-found") {
                TopicCard(stringResource(R.string.passport_scan_no_result), null, tone = CardTone.Caution)
            }
        }
        if (scan == ScanState.Reading) {
            item(key = "reading") { Text(stringResource(R.string.passport_scan_reading), style = MaterialTheme.typography.bodyLarge) }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                PrimaryButton(stringResource(R.string.passport_use_camera), onClick = onCamera, enabled = agreed)
                OutlinedButton(
                    onClick = onPickPhoto,
                    enabled = agreed,
                    modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight),
                ) { Text(stringResource(R.string.passport_use_photo), style = MaterialTheme.typography.labelLarge) }
                OutlinedButton(
                    onClick = onManual,
                    modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight),
                ) { Text(stringResource(R.string.passport_use_manual), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

// ---------------- 2. 카메라 촬영 ----------------

@Composable
fun PassportScanScreen(viewModel: PassportFlowViewModel, onFound: () -> Unit) {
    SecureScreen()
    val context = LocalContext.current
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(scan) { if (scan is ScanState.Found) onFound() }

    AppScreen(
        title = stringResource(R.string.passport_title),
        speech = stringResource(R.string.passport_scan_hint),
    ) {
        item(key = "steps") { PassportStepper(current = 0) }
        if (!granted) {
            item(key = "permission") {
                InfoCard {
                    Text(stringResource(R.string.passport_scan_camera_needed), style = MaterialTheme.typography.bodyLarge)
                    PrimaryButton(stringResource(R.string.passport_scan_allow_camera), onClick = { permission.launch(Manifest.permission.CAMERA) })
                }
            }
        } else {
            item(key = "camera") { MrzCamera(viewModel) }
            item(key = "hint") { Text(stringResource(R.string.passport_scan_hint), style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

/**
 * CameraX 미리보기 + 프레임 분석. 프레임은 메모리에서 ML Kit으로 읽고 바로 닫는다 — 파일로 남지 않는다.
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun MrzCamera(viewModel: PassportFlowViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val p = providerFuture.get().also { provider = it }
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null) {
                    proxy.close()
                    return@setAnalyzer
                }
                val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                viewModel.recognizer.process(input)
                    .addOnSuccessListener { viewModel.onFrameText(it.text) }
                    .addOnCompleteListener { proxy.close() }
            }
            p.unbindAll()
            p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(MaterialTheme.shapes.large),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().fillMaxHeight())
        // MRZ 안내 네모: 화면 아래쪽 여권 두 줄 자리
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .aspectRatio(5f)
                .border(BorderStroke(3.dp, Tokens.Surface), MaterialTheme.shapes.medium),
        )
    }
}

// ---------------- 3. 값 확인 ----------------

@Composable
fun PassportConfirmScreen(
    viewModel: PassportFlowViewModel,
    onRescan: () -> Unit,
    onManual: () -> Unit,
    onSaved: () -> Unit,
) {
    SecureScreen()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val mrz = (scan as? ScanState.Found)?.mrz
    val scope = rememberCoroutineScope()
    val auth = rememberDeviceAuth()
    var saveFailed by remember { mutableStateOf(false) }

    fun save(record: PassportRecord) {
        scope.launch {
            when (viewModel.save(record)) {
                WalletRepository.SaveResult.Saved -> onSaved()
                WalletRepository.SaveResult.Locked, WalletRepository.SaveResult.NeedsAuth -> auth {
                    scope.launch { if (viewModel.save(record) == WalletRepository.SaveResult.Saved) onSaved() else saveFailed = true }
                }
                WalletRepository.SaveResult.Failed -> saveFailed = true
            }
        }
    }

    PassportConfirmContent(
        mrz = mrz,
        saveFailed = saveFailed,
        onSave = { mrz?.let { save(it.toRecord()) } },
        onRescan = { viewModel.rescan(); onRescan() },
        onManual = onManual,
    )
}

@Composable
fun PassportConfirmContent(
    mrz: MrzData?,
    saveFailed: Boolean,
    onSave: () -> Unit,
    onRescan: () -> Unit,
    onManual: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val ok = stringResource(R.string.passport_check_ok)
    val fail = stringResource(R.string.passport_check_fail)
    AppScreen(
        title = stringResource(R.string.passport_title),
        speech = stringResource(R.string.passport_confirm_body),
    ) {
        item(key = "steps") { PassportStepper(current = 2) }
        item(key = "body") { Text(stringResource(R.string.passport_confirm_body), style = MaterialTheme.typography.bodyLarge) }
        if (mrz != null) {
            item(key = "values") {
                InfoCard {
                    fun badge(check: MrzCheck?) = check?.let { if (mrz.checks[it] == true) ok else fail }
                    ConfirmRow(stringResource(R.string.passport_field_surname), mrz.surname, null)
                    ConfirmRow(stringResource(R.string.passport_field_given), mrz.givenNames, null)
                    ConfirmRow(stringResource(R.string.passport_field_number), mrz.documentNumber, badge(MrzCheck.DocumentNumber))
                    ConfirmRow(stringResource(R.string.passport_field_nationality), mrz.nationality, null)
                    ConfirmRow(stringResource(R.string.passport_field_birth), mrz.birthDate.toString(), badge(MrzCheck.BirthDate))
                    ConfirmRow(stringResource(R.string.passport_field_sex), sexLabel(mrz.sex), null)
                    ConfirmRow(stringResource(R.string.passport_field_expiry), mrz.expiryDate.toString(), badge(MrzCheck.ExpiryDate))
                }
            }
            if (!mrz.allChecksPass) {
                item(key = "warning") { TopicCard(stringResource(R.string.passport_check_warning), null, tone = CardTone.Caution) }
            }
            if (mrz.expiryDate.isBefore(today)) {
                item(key = "expired") { TopicCard(stringResource(R.string.wallet_passport_expired), null, tone = CardTone.Caution) }
            }
            item(key = "chip") {
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.passport_chip_soon))
                }
            }
        }
        if (saveFailed) {
            item(key = "save-failed") { TopicCard(stringResource(R.string.booking_save_failed), null, tone = CardTone.Caution) }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                // 체크디지트가 하나라도 틀리면 저장하지 않는다 — OCR 오류가 입국 거부로 이어질 수 있다 (PRD 8.2)
                if (mrz != null && mrz.allChecksPass) PrimaryButton(stringResource(R.string.passport_save), onClick = onSave)
                OutlinedButton(onClick = onRescan, modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight)) {
                    Text(stringResource(R.string.passport_rescan), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth().heightIn(min = LocalDimens.current.buttonHeight)) {
                    Text(stringResource(R.string.passport_use_manual), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ConfirmRow(label: String, value: String, badge: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        if (badge != null) {
            val passed = badge == stringResource(R.string.passport_check_ok)
            StatusChip(
                badge,
                container = if (passed) Tokens.SuccessBg else Tokens.DangerBg,
                content = if (passed) Tokens.SuccessText else Tokens.DangerText,
            )
        }
    }
}

@Composable
private fun sexLabel(sex: Char) = stringResource(
    when (sex) {
        'M' -> R.string.passport_sex_m
        'F' -> R.string.passport_sex_f
        else -> R.string.passport_sex_x
    },
)

// ---------------- 직접 입력 ----------------

@Composable
fun PassportManualScreen(viewModel: PassportFlowViewModel, onSaved: () -> Unit) {
    SecureScreen()
    val scope = rememberCoroutineScope()
    val auth = rememberDeviceAuth()
    // 개인정보가 저장 상태(Bundle)로 새지 않도록 rememberSaveable을 쓰지 않는다
    var surname by remember { mutableStateOf("") }
    var given by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var nationality by remember { mutableStateOf("KOR") }
    var birth by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf('M') }
    var invalid by remember { mutableStateOf(false) }

    fun record(): PassportRecord? {
        val upper = Regex("^[A-Z][A-Z ]*$")
        val b = runCatching { LocalDate.parse(birth.trim()) }.getOrNull()
        val e = runCatching { LocalDate.parse(expiry.trim()) }.getOrNull()
        val num = number.trim().uppercase()
        val nat = nationality.trim().uppercase()
        if (!upper.matches(surname.trim().uppercase()) || b == null || e == null ||
            !Regex("^[A-Z0-9]{5,9}$").matches(num) || !Regex("^[A-Z]{3}$").matches(nat)
        ) return null
        return PassportRecord(
            surname = surname.trim().uppercase(),
            givenNames = given.trim().uppercase(),
            documentNumber = num,
            nationality = nat,
            issuingState = nat,
            birthDate = b.toString(),
            sex = sex.toString(),
            expiryDate = e.toString(),
            source = "manual",
            mrzVerified = false,
            savedAt = LocalDateTime.now().toString(),
        )
    }

    AppScreen(
        title = stringResource(R.string.passport_title),
        speech = stringResource(R.string.passport_manual_body),
    ) {
        item(key = "body") { Text(stringResource(R.string.passport_manual_body), style = MaterialTheme.typography.bodyLarge) }
        item(key = "form") {
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                ManualField(R.string.passport_field_surname, surname) { surname = it }
                ManualField(R.string.passport_field_given, given) { given = it }
                ManualField(R.string.passport_field_number, number) { number = it }
                ManualField(R.string.passport_field_nationality, nationality) { nationality = it }
                ManualField(R.string.passport_field_birth, birth, hint = R.string.passport_date_hint) { birth = it }
                Text(stringResource(R.string.passport_field_sex), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf('M' to R.string.passport_sex_m, 'F' to R.string.passport_sex_f, 'X' to R.string.passport_sex_x).forEach { (value, label) ->
                        FilterChip(
                            selected = sex == value,
                            onClick = { sex = value },
                            label = { Text(stringResource(label), style = MaterialTheme.typography.labelLarge) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                ManualField(R.string.passport_field_expiry, expiry, hint = R.string.passport_date_hint) { expiry = it }
            }
        }
        if (invalid) {
            item(key = "invalid") { TopicCard(stringResource(R.string.passport_manual_invalid), null, tone = CardTone.Caution) }
        }
        item(key = "save") {
            PrimaryButton(stringResource(R.string.passport_save), onClick = {
                val r = record()
                invalid = r == null
                if (r != null) scope.launch {
                    when (viewModel.save(r)) {
                        WalletRepository.SaveResult.Saved -> onSaved()
                        else -> auth { scope.launch { if (viewModel.save(r) == WalletRepository.SaveResult.Saved) onSaved() } }
                    }
                }
            })
        }
    }
}

@Composable
private fun ManualField(label: Int, value: String, hint: Int? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        placeholder = hint?.let { { Text(stringResource(it)) } },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        // 영문 대문자 입력, 자동 고침 끔
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}
