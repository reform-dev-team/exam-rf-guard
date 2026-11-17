package com.classroom.rfdetector.inspection.radio

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.classroom.rfdetector.AppViewModel
import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.core.TrackedDevice
import com.classroom.rfdetector.ui.RfColors
import kotlinx.coroutines.delay

@Composable
fun RadioInspectionScreen(vm: AppViewModel) {
    DisposableEffect(vm) {
        vm.setSurveyOpen(true)
        onDispose { vm.setSurveyOpen(false) }
    }
    val devices by vm.devices.collectAsState()
    val monitoring by vm.scanning.collectAsState()
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    // 선택 후 이름/그룹 대표가 바뀌어도 주소는 바꾸지 않는다.
    var target by remember { mutableStateOf<TrackedDevice?>(null) }
    val key = selectedKey
    val chosen = target?.takeIf { it.trackingKey == key } ?: devices.firstOrNull { it.trackingKey == key }
    if (chosen == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("비교할 신호 하나를 고르세요. 같은 이름이어도 다른 신호로 바꿔 따라가지 않습니다.")
            if (devices.isEmpty()) Text("먼저 기본 화면에서 감시를 시작해 신호를 받아 주세요.")
            var details by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { details = !details }) { Text(if (details) "주소 접기" else "주소로 구분하기") }
            devices.sortedBy { it.trackingKey }.forEachIndexed { index, device ->
                OutlinedButton(onClick = { target = device; selectedKey = device.trackingKey }, Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("${index + 1}. ${device.displayName}", style = MaterialTheme.typography.titleMedium)
                        Text(if (device.source == ScanSource.WIFI) "와이파이" else "블루투스")
                        if (details) Text(device.key, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    } else {
        val model = remember(chosen.trackingKey) { RoomSurvey(chosen.trackingKey, chosen.source) }
        val context = LocalContext.current
        val receiver = remember(context) { TargetRadioReceiver(context) }
        var revision by remember { mutableIntStateOf(0) }
        var selectedPoint by remember { mutableStateOf(Checkpoint.FRONT_LEFT) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        fun gate() = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && vm.surveyAllowed(chosen.source)
        fun stop() { receiver.stop(); model.stop(); revision++ }
        DisposableEffect(receiver, lifecycle, model) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stop() }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer); stop() }
        }
        LaunchedEffect(monitoring) { if (!monitoring && receiver.active) stop() }
        LaunchedEffect(model) {
            while (true) {
                if (receiver.active && !gate()) stop()
                revision++
                delay(250)
            }
        }
        val now = SystemClock.elapsedRealtimeNanos()
        // revision makes packet callbacks and the stale timer visible, not an invented radio sample.
        @Suppress("UNUSED_VARIABLE") val update = revision
        RadioSurveyPanel(chosen.displayName, chosen.source, model, now, receiver.active,
            if (gate()) receiver.message else "기본 화면으로 돌아가 감시와 비행기 모드·방해 금지·권한·무선 설정을 확인하세요", selectedPoint,
            onSelectPoint = { selectedPoint = it },
            onStart = { receiver.start(chosen.source, chosen.key, ::gate,
                onPacket = { packetKey, timestamp, rssi -> model.accept(packetKey, timestamp, rssi, SystemClock.elapsedRealtimeNanos()); revision++ },
                onState = { if (!receiver.active) model.stop(); revision++ }) },
            onStop = { stop() },
            onCapture = { model.begin(selectedPoint, SystemClock.elapsedRealtimeNanos()); revision++ },
            onFinish = { if (model.finish(SystemClock.elapsedRealtimeNanos())) selectedPoint = model.nextPoint ?: selectedPoint; revision++ },
            onCancel = { model.cancelCapture(); revision++ },
            onReference = { model.setReference(SystemClock.elapsedRealtimeNanos()); revision++ },
            onReset = { stop(); model.reset(); selectedPoint = Checkpoint.FRONT_LEFT; revision++ },
            onChoose = { stop(); target = null; selectedKey = null }, gateAvailable = gate())
    }
}

/** Pure rendering boundary also used by synthetic native UI journeys. */
@Composable
internal fun RadioSurveyPanel(name: String, source: ScanSource, model: RoomSurvey, now: Long,
    active: Boolean, message: String, selectedPoint: Checkpoint,
    onSelectPoint: (Checkpoint) -> Unit, onStart: () -> Unit, onStop: () -> Unit,
    onCapture: () -> Unit, onFinish: () -> Unit, onCancel: () -> Unit, onReference: () -> Unit,
    onReset: () -> Unit, onChoose: () -> Unit, gateAvailable: Boolean = true) {
    val readings = model.readings
    val point = model.activePoint
    val area = model.strongerArea()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("교탁을 앞에 두고 같은 높이·방향으로 측정하세요. 벽·사람·반사에 따라 달라지는 상대 비교입니다.")
        Text(message, color = RfColors.primary)
        if (!active) Button(onClick = onStart, enabled = gateAvailable, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("선택한 신호 받기") }
        else OutlinedButton(onClick = onStop, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("수신 멈추기") }
        Text("이동하며 비교", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(model.approach(now).label, style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = onReference, enabled = active && model.approach(now) !in listOf(Approach.LOST, Approach.INVALID),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("지금 세기를 출발값으로") }
        HorizontalDivider()
        Text("다섯 곳 비교 · ${readings.size}/5 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        RoomSketch(area)
        Text("측정할 곳: ${selectedPoint.label}", style = MaterialTheme.typography.titleMedium)
        Checkpoint.entries.forEachIndexed { index, checkpoint ->
            OutlinedButton(onClick = { onSelectPoint(checkpoint) }, enabled = point == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("${index + 1}. ${checkpoint.label} · ${if (checkpoint in readings) "기록 완료 / 다시 재기" else "아직 기록 안 함"}")
            }
        }
        if (point == null) {
            Button(onClick = onCapture, enabled = active, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("이곳에서 새 측정 시작") }
        } else {
            Text("${point.label} · 새 신호 ${model.sampleCount}개 받음")
            Text(if (source == ScanSource.WIFI) "새 결과 1회로 잠정 기록할 수 있습니다. 더 기다려 여러 번 모으면 흔들림도 비교합니다. 시스템 제한으로 30초 이상 걸릴 수 있으며 빠른 재요청은 하지 않습니다." else "이곳에 머물러 새 신호 3개 이상과 2초 이상의 변화를 모읍니다.")
            Button(onClick = onFinish, enabled = model.captureReady(now), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("이 지점 기록") }
            OutlinedButton(onClick = onCapture, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("이곳에서 다시 받기") }
            TextButton(onClick = onCancel) { Text("이번 측정 취소") }
        }
        if (readings.size == 5) {
            if (model.tentative) Text("표본이 적은 잠정 비교입니다. 흔들림은 아직 모릅니다. 필요하면 각 지점에서 더 오래 받아 다시 기록하세요.", color = RfColors.warning)
            Text(if (area == StrongerArea.UNKNOWN) area.label else "세기가 더 강했던 쪽: ${area.label}", style = MaterialTheme.typography.titleLarge)
            Text("좌석이나 사람의 위치를 뜻하지 않습니다. 주소가 바뀌면 다른 신호를 새로 골라야 합니다.")
        } else Text("다섯 곳을 모두 기록해야 비교할 수 있습니다.")
        var details by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { details = !details }) { Text(if (details) "측정값 접기" else "측정값 보기") }
        if (details) readings.forEach { (checkpoint, reading) ->
            val spread = reading.dispersion?.let { "$it dB" } ?: "아직 모름"
            Text("${checkpoint.label}: 중앙값 ${reading.median} dBm · 흔들림 $spread · ${reading.count}개")
        }
        OutlinedButton(onClick = onReset, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("다섯 곳 기록 초기화") }
        OutlinedButton(onClick = onChoose, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("다른 신호 고르기") }
    }
}

@Composable
internal fun RoomSketch(area: StrongerArea) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("교탁 / 앞쪽", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        val border = RfColors.textDim
        val highlight = RfColors.primary.copy(alpha = 0.4f)
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            drawRect(border, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            drawLine(border, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height))
            drawLine(border, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
            fun fill(left: Boolean, top: Boolean) {
                drawRect(highlight, Offset(if (left) 0f else size.width / 2, if (top) 0f else size.height / 2), Size(size.width / 2, size.height / 2))
            }
            when (area) {
                StrongerArea.FRONT_LEFT -> fill(true, true); StrongerArea.FRONT_RIGHT -> fill(false, true)
                StrongerArea.BACK_LEFT -> fill(true, false); StrongerArea.BACK_RIGHT -> fill(false, false)
                StrongerArea.FRONT -> { fill(true, true); fill(false, true) }
                StrongerArea.BACK -> { fill(true, false); fill(false, false) }
                StrongerArea.LEFT -> { fill(true, true); fill(true, false) }
                StrongerArea.RIGHT -> { fill(false, true); fill(false, false) }
                StrongerArea.PLATFORM -> drawRect(highlight, Offset(size.width / 4, 0f), Size(size.width / 2, size.height / 5))
                StrongerArea.UNKNOWN -> Unit
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("왼쪽"); Text("오른쪽") }
        Text("색칠은 상대적으로 강했던 쪽이며, 정확한 위치 표시가 아닙니다.", style = MaterialTheme.typography.bodySmall)
    }
}
