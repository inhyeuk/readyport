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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
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
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.CardNewsCard
import com.readyport.ui.components.ComingSoonGroup
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.InfoCard
import com.readyport.ui.components.KeyValueRow
import com.readyport.ui.components.ListDivider
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.SecondaryButton
import com.readyport.ui.components.SecurityBanner
import com.readyport.ui.components.SelectChip
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.components.minTouch
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.vault.PassportRecord
import com.readyport.vault.WalletRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.Executors

private val PassportSteps = listOf(R.string.passport_step_scan, R.string.passport_step_chip, R.string.passport_step_confirm)
private val PassportStepIcons: List<ImageVector> = listOf(
    Icons.Outlined.PhotoCamera,
    Icons.Outlined.Nfc,
    Icons.AutoMirrored.Outlined.FactCheck,
)

/** 칩 확인(선택) 단계 — NFC는 아직 준비 중이라 지나가도 '완료'로 그리지 않는다 */
private const val OptionalChipStep = 1

/**
 * 단계 표시: 촬영 → 칩 확인(선택) → 값 확인 (PRD 5.5, DESIGN_SPEC 6-25).
 * 누를 수 없는 아이콘 스텝퍼 — 지난 단계 Accent 채움 + Check, 지금 Accent 채움 + 단계 아이콘, 다음 흰 원 + LineStrong 테두리.
 * 아이콘 아래 단계 이름 글자를 그대로 두고(원칙 8), TalkBack은 `passport_steps_desc` 한 문장으로 읽는다.
 */
@Composable
private fun PassportStepper(current: Int) {
    val dimens = LocalDimens.current
    val labels = PassportSteps.map { stringResource(it) }
    val desc = stringResource(R.string.passport_steps_desc, labels[current], current + 1, labels.size)
    val node = dimens.iconBadge
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = desc }) {
        labels.forEachIndexed { i, label ->
            val done = i < current && i != OptionalChipStep
            val now = i == current
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(node), contentAlignment = Alignment.Center) {
                    // 노드 사이 2dp 연결선 (지나온 구간은 Accent)
                    Row(Modifier.fillMaxWidth().height(2.dp)) {
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(if (i == 0) Color.Transparent else if (i <= current) Tokens.Accent else Tokens.Line),
                        )
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(if (i == labels.lastIndex) Color.Transparent else if (i < current) Tokens.Accent else Tokens.Line),
                        )
                    }
                    val filled = done || now
                    Box(
                        Modifier
                            .size(node)
                            .clip(CircleShape)
                            .background(if (filled) Tokens.Accent else Tokens.Surface)
                            .then(if (filled) Modifier else Modifier.border(1.dp, Tokens.LineStrong, CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (done) Icons.Outlined.Check else PassportStepIcons[i],
                            contentDescription = null,
                            tint = if (filled) Tokens.Surface else Tokens.InkTertiary,
                            modifier = Modifier.size(dimens.icon),
                        )
                    }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (now) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (now) Tokens.Accent else Tokens.InkSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/**
 * 여권 사진 면 그림: 사진 칸·글 줄과 맨 아래 두 줄(MRZ, `<<<`)을 Accent 테두리로 강조한다. 장식(글자 없음, TalkBack 숨김).
 * 글자를 품지 않는 그림이라 높이를 dp로 정한다.
 */
@Composable
private fun MrzIllustration(modifier: Modifier = Modifier) {
    val height = if (LocalDimens.current.easyMode) 168.dp else 144.dp
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics {}) {
        val u = 1.dp.toPx()
        // 여권 면
        val pageW = minOf(size.width, 300 * u)
        val left = (size.width - pageW) / 2f
        val top = 4 * u
        val pageH = size.height - 8 * u
        drawRoundRect(Tokens.SurfaceSunken, Offset(left, top), Size(pageW, pageH), CornerRadius(12 * u))
        drawRoundRect(Tokens.Line, Offset(left, top), Size(pageW, pageH), CornerRadius(12 * u), style = Stroke(u))
        // 사진 칸 + 사람 모양
        val pad = 14 * u
        val photoW = 52 * u
        val photoH = 62 * u
        drawRoundRect(Tokens.SurfaceHighest, Offset(left + pad, top + pad), Size(photoW, photoH), CornerRadius(6 * u))
        val cx = left + pad + photoW / 2f
        drawCircle(Tokens.LineStrong, radius = 10 * u, center = Offset(cx, top + pad + 22 * u))
        drawRoundRect(
            Tokens.LineStrong,
            Offset(cx - 17 * u, top + pad + 37 * u),
            Size(34 * u, 25 * u),
            CornerRadius(14 * u, 14 * u),
        )
        // 글 줄
        val textLeft = left + pad + photoW + 14 * u
        val textMax = left + pageW - pad - textLeft
        listOf(0.55f, 0.85f, 0.4f, 0.7f).forEachIndexed { i, f ->
            drawRoundRect(
                Tokens.Line,
                Offset(textLeft, top + pad + 4 * u + i * 15 * u),
                Size(textMax * f, 6 * u),
                CornerRadius(3 * u),
            )
        }
        // MRZ 두 줄 강조
        val zoneTop = top + pad + photoH + 8 * u
        val zoneH = top + pageH - 8 * u - zoneTop
        val zoneLeft = left + 8 * u
        val zoneW = pageW - 16 * u
        drawRoundRect(Tokens.AccentSoft, Offset(zoneLeft, zoneTop), Size(zoneW, zoneH), CornerRadius(8 * u))
        drawRoundRect(Tokens.Accent, Offset(zoneLeft, zoneTop), Size(zoneW, zoneH), CornerRadius(8 * u), style = Stroke(2 * u))
        val rows = listOf("P<KOR<HONG<<GILDONG<<<<<<<<<<<<", "M12345678<KOR8001019M3001012<<<<<")
        val cell = 8 * u
        rows.forEachIndexed { r, pattern ->
            val y = zoneTop + zoneH * (if (r == 0) 0.32f else 0.70f)
            val count = ((zoneW - 16 * u) / cell).toInt()
            for (c in 0 until count) {
                val x = zoneLeft + 8 * u + c * cell
                val ch = pattern[c % pattern.length]
                if (ch == '<') {
                    val p = Path().apply {
                        moveTo(x + cell * 0.7f, y - 3 * u)
                        lineTo(x + cell * 0.25f, y)
                        lineTo(x + cell * 0.7f, y + 3 * u)
                    }
                    drawPath(p, Tokens.Accent, style = Stroke(1.4f * u, cap = StrokeCap.Round))
                } else {
                    drawRoundRect(Tokens.InkSecondary, Offset(x + cell * 0.22f, y - 3.5f * u), Size(cell * 0.48f, 7 * u), CornerRadius(u))
                }
            }
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
        item(key = "security") { SecurityBanner(compact = true) }
        item(key = "steps") { PassportStepper(current = 0) }
        item(key = "body") {
            InfoCard {
                MrzIllustration()
                Text(stringResource(R.string.passport_intro_body), style = MaterialTheme.typography.bodyLarge, color = Tokens.Ink)
            }
        }
        // 카메라·여권 처리에 대한 눈에 띄는 고지와 동의 (PRD 8.1 민감 정보)
        item(key = "privacy") {
            CardNewsCard(
                title = stringResource(R.string.passport_privacy_title),
                icon = Icons.Outlined.PrivacyTip,
            ) {
                IconBullet(stringResource(R.string.passport_privacy_1), Icons.Outlined.NoPhotography, tone = BadgeTone.Accent)
                IconBullet(stringResource(R.string.passport_privacy_2), Icons.Outlined.Lock, tone = BadgeTone.Accent)
                IconBullet(stringResource(R.string.passport_privacy_3), Icons.Outlined.Info, tone = BadgeTone.Accent)
                HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = Tokens.Line)
                ConsentRow(
                    text = stringResource(R.string.passport_consent),
                    checked = agreed,
                    onCheckedChange = { agreed = it },
                )
            }
        }
        if (scan == ScanState.NotFound) {
            item(key = "not-found") {
                NoticeBanner(stringResource(R.string.passport_scan_no_result), icon = Icons.Outlined.SearchOff, tone = BannerTone.Caution)
            }
        }
        if (scan == ScanState.Reading) {
            item(key = "reading") { ReadingRow(stringResource(R.string.passport_scan_reading)) }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                PrimaryButton(stringResource(R.string.passport_use_camera), onClick = onCamera, enabled = agreed, icon = Icons.Outlined.PhotoCamera)
                SecondaryButton(stringResource(R.string.passport_use_photo), onClick = onPickPhoto, icon = Icons.Outlined.PhotoLibrary, enabled = agreed)
                QuietButton(stringResource(R.string.passport_use_manual), onClick = onManual, icon = Icons.Outlined.EditNote)
            }
        }
    }
}

/** 동의 체크 줄: 줄 전체가 Checkbox 토글(초점 한 번), 체크하면 연한 Accent 바탕 */
@Composable
internal fun ConsentRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier
            .fillMaxWidth()
            .minTouch()
            .clip(shape)
            .background(if (checked) Tokens.AccentSoft else Tokens.SurfaceSunken)
            .toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = Tokens.Accent, uncheckedColor = Tokens.LineStrong, checkmarkColor = Tokens.Surface),
        )
        Text(text, style = MaterialTheme.typography.labelLarge, color = Tokens.Ink, modifier = Modifier.weight(1f))
    }
}

/** 읽는 중 표시 (진행 원 + 문장) */
@Composable
private fun ReadingRow(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = Tokens.Accent, strokeWidth = 3.dp, modifier = Modifier.size(LocalDimens.current.icon))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Tokens.Ink, modifier = Modifier.weight(1f))
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
        item(key = "security") { SecurityBanner(compact = true) }
        item(key = "steps") { PassportStepper(current = 0) }
        if (!granted) {
            item(key = "permission") {
                CardNewsCard(
                    title = stringResource(R.string.passport_scan_allow_camera),
                    icon = Icons.Outlined.PhotoCamera,
                    body = stringResource(R.string.passport_scan_camera_needed),
                ) {
                    PrimaryButton(
                        stringResource(R.string.passport_scan_allow_camera),
                        onClick = { permission.launch(Manifest.permission.CAMERA) },
                        icon = Icons.Outlined.PhotoCamera,
                    )
                }
            }
        } else {
            item(key = "camera") { MrzCamera(viewModel) }
            item(key = "hint") {
                IconBullet(stringResource(R.string.passport_scan_hint), Icons.Outlined.DocumentScanner, tone = BadgeTone.Accent)
            }
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
            .clip(MaterialTheme.shapes.extraLarge),
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

/**
 * 25 여권 값 확인. 경고는 목록 위, 값은 KeyValueRow + 확인 배지(정확히 칸마다 하나 — 다른 곳에 중복으로 그리지 않는다).
 * 체크디지트가 하나라도 틀리면 저장 버튼을 아예 그리지 않는다.
 */
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
        item(key = "security") { SecurityBanner(compact = true) }
        item(key = "steps") { PassportStepper(current = 2) }
        item(key = "body") {
            Text(stringResource(R.string.passport_confirm_body), style = MaterialTheme.typography.bodyLarge, color = Tokens.Ink)
        }
        if (mrz != null) {
            if (!mrz.allChecksPass) {
                item(key = "warning") {
                    NoticeBanner(stringResource(R.string.passport_check_warning), icon = Icons.Outlined.ReportProblem, tone = BannerTone.Caution)
                }
            }
            if (mrz.expiryDate.isBefore(today)) {
                item(key = "expired") {
                    NoticeBanner(stringResource(R.string.wallet_passport_expired), icon = Icons.Outlined.EventBusy, tone = BannerTone.Danger)
                }
            }
            item(key = "values") {
                val rows = listOf(
                    Triple(stringResource(R.string.passport_field_surname), mrz.surname, null),
                    Triple(stringResource(R.string.passport_field_given), mrz.givenNames, null),
                    Triple(stringResource(R.string.passport_field_number), mrz.documentNumber, MrzCheck.DocumentNumber),
                    Triple(stringResource(R.string.passport_field_nationality), mrz.nationality, null),
                    Triple(stringResource(R.string.passport_field_birth), mrz.birthDate.toString(), MrzCheck.BirthDate),
                    Triple(stringResource(R.string.passport_field_sex), sexLabel(mrz.sex), null),
                    Triple(stringResource(R.string.passport_field_expiry), mrz.expiryDate.toString(), MrzCheck.ExpiryDate),
                )
                ListGroup(stringResource(R.string.passport_values_title)) {
                    rows.forEachIndexed { i, (label, value, check) ->
                        KeyValueRow(
                            label = label,
                            value = value,
                            badge = if (check != null) {
                                { CheckTag(mrz.checks[check] == true, ok, fail) }
                            } else {
                                null
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        if (i != rows.lastIndex) ListDivider(indent = false)
                    }
                }
            }
        }
        if (saveFailed) {
            item(key = "save-failed") {
                NoticeBanner(stringResource(R.string.booking_save_failed), icon = Icons.Outlined.ErrorOutline, tone = BannerTone.Caution)
            }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                // 체크디지트가 하나라도 틀리면 저장하지 않는다 — OCR 오류가 입국 거부로 이어질 수 있다 (PRD 8.2)
                if (mrz != null && mrz.allChecksPass) {
                    PrimaryButton(stringResource(R.string.passport_save), onClick = onSave, icon = Icons.Outlined.Check)
                }
                SecondaryButton(stringResource(R.string.passport_rescan), onClick = onRescan, icon = Icons.Outlined.PhotoCamera)
                QuietButton(stringResource(R.string.passport_use_manual), onClick = onManual, icon = Icons.Outlined.EditNote)
            }
        }
        // NFC 칩 확인은 2차 — 누를 수 없는 '곧 추가돼요' 묶음으로 (D15)
        if (mrz != null) {
            item(key = "chip") { ComingSoonGroup(listOf(Icons.Outlined.Nfc to stringResource(R.string.passport_chip_soon))) }
        }
    }
}

/** 체크디지트 확인 배지: 맞으면 Verified(확인 완료), 틀리면 Caution(확인 안 됨) — 색 + 아이콘 + 글자 */
@Composable
private fun CheckTag(passed: Boolean, ok: String, fail: String) {
    StatusTag(if (passed) ok else fail, if (passed) StatusKind.Verified else StatusKind.Caution)
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
        item(key = "security") { SecurityBanner(compact = true) }
        item(key = "body") {
            Text(stringResource(R.string.passport_manual_body), style = MaterialTheme.typography.bodyLarge, color = Tokens.Ink)
        }
        item(key = "form") {
            InfoCard {
                Column(verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap)) {
                    ManualField(R.string.passport_field_surname, surname, Icons.Outlined.Person) { surname = it }
                    ManualField(R.string.passport_field_given, given, Icons.Outlined.Person) { given = it }
                    ManualField(R.string.passport_field_number, number, Icons.Outlined.Badge) { number = it }
                    ManualField(R.string.passport_field_nationality, nationality, Icons.Outlined.Public) { nationality = it }
                    ManualField(R.string.passport_field_birth, birth, Icons.Outlined.CalendarMonth, hint = R.string.passport_date_hint) { birth = it }
                    Text(stringResource(R.string.passport_field_sex), style = MaterialTheme.typography.titleSmall, color = Tokens.InkSecondary)
                    FlowRow(
                        modifier = Modifier.selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf('M' to R.string.passport_sex_m, 'F' to R.string.passport_sex_f, 'X' to R.string.passport_sex_x).forEach { (value, label) ->
                            SelectChip(selected = sex == value, onClick = { sex = value }, label = stringResource(label))
                        }
                    }
                    ManualField(R.string.passport_field_expiry, expiry, Icons.Outlined.EventBusy, hint = R.string.passport_date_hint) { expiry = it }
                }
            }
        }
        if (invalid) {
            item(key = "invalid") {
                NoticeBanner(stringResource(R.string.passport_manual_invalid), icon = Icons.Outlined.ErrorOutline, tone = BannerTone.Caution)
            }
        }
        item(key = "save") {
            PrimaryButton(stringResource(R.string.passport_save), icon = Icons.Outlined.Check, onClick = {
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

/** 직접 입력 칸: 라벨 + 앞 아이콘 + (날짜면) 형식 안내를 칸 아래 supportingText로 늘 보인다 */
@Composable
private fun ManualField(label: Int, value: String, icon: ImageVector, hint: Int? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        supportingText = hint?.let { { Text(stringResource(it)) } },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.small,
        // 영문 대문자 입력, 자동 고침 끔
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}
