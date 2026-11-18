package com.classroom.rfdetector.inspection.flow

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classroom.rfdetector.core.SignalPresence
import com.classroom.rfdetector.core.TrackedDevice
import com.classroom.rfdetector.ui.RfColors

internal data class WalkUi(
    val available: Boolean = false,
    val active: Boolean = false,
    val pointIndex: Int = 0,
    val holdSeconds: Int? = null,
    val result: List<WalkCandidate>? = null,
    val message: String? = null,
)

@Composable
internal fun RoomWalkScreen(
    state: WalkUi,
    devices: List<TrackedDevice>,
    onStart: () -> Unit,
    onMark: () -> Unit,
    onFind: (String) -> Unit,
) {
    val result = state.result
    val (located, unlocated) = remember(result) { result.orEmpty().partition { it.zone != null } }
    val byKey = remember(result != null, devices) {
        if (result == null) emptyMap() else devices.associateBy { it.trackingKey }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("room-walk"),
        state = key(result != null) { rememberLazyListState() },
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (result == null) {
            item {
                Text("표시된 순서대로 이동해 각 지점에서 측정하세요. 지도는 블루투스 신호 강도로 추정한 기기의 위치를 보여줍니다.")
            }
            item { WalkRoute(state.pointIndex, state.active) }
        }
        state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        when {
            result != null -> {
                item {
                    Text("측정 결과", style = MaterialTheme.typography.titleLarge)
                    WalkResultMap(located, byKey, state.available, onFind)
                }
                if (located.isEmpty()) item {
                    Text(if (unlocated.isEmpty()) "측정된 신호가 없습니다." else "지도에 표시할 수 있는 신호가 없습니다.")
                    Text("블루투스 수신을 확인한 뒤 휴대폰을 같은 높이와 방향으로 들고, 모서리 사이를 천천히 걸으세요. 각 지점에서는 8초 동안 움직이지 말고 다시 측정하세요.")
                }
                item { OutlinedButton(onClick = onStart, enabled = state.available) { Text("다시 측정") } }
                if (unlocated.isNotEmpty()) item {
                    Text("위치를 추정하지 못한 신호 (${unlocated.size})", style = MaterialTheme.typography.titleMedium)
                    Text("지도에는 표시하지 않습니다. 아래 주소를 선택해 현재 세기를 확인할 수 있습니다.")
                }
                items(unlocated, key = { it.trackingKey }) { candidate ->
                    val device = byKey[candidate.trackingKey]
                    val availability = walkAvailability(state.available, device)
                    val address = device?.key ?: candidate.trackingKey.removePrefix("BLE:")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(device?.displayName ?: "이전 신호", style = MaterialTheme.typography.titleMedium)
                        Text("주소: $address")
                        Text("$availability · 구역을 추정할 수 없음")
                        OutlinedButton(onClick = { onFind(candidate.trackingKey) },
                            modifier = Modifier.semantics { contentDescription = "$address 신호 찾기" }) { Text("이 신호 찾기") }
                    }
                }
            }
            state.active -> {
                val anchor = walkAnchors.getOrNull(state.pointIndex)
                if (anchor != null) item {
                    Text("${state.pointIndex + 1} / ${walkAnchors.size} · ${anchor.label}", style = MaterialTheme.typography.titleMedium)
                    if (state.holdSeconds != null) {
                        Text("제자리에서 측정 중 · ${state.holdSeconds}초 남음")
                    } else {
                        Text("이 지점에 도착하면 측정을 시작하세요. 8초 동안 제자리에 머물러 주세요.")
                    }
                    Button(onClick = onMark, enabled = state.holdSeconds == null) { Text("이 지점에서 측정") }
                }
            }
            else -> item {
                Text("다섯 지점을 차례로 측정합니다. 각 지점에서 8초 동안 머물러 주세요.")
                if (!state.available && state.message == null) Text("블루투스 감시와 가속도 센서를 사용할 수 있어야 합니다.")
                Button(onClick = onStart, enabled = state.available) { Text("교실 측정 시작") }
            }
        }
    }
}

internal fun walkAvailability(available: Boolean, device: TrackedDevice?): String = when {
    !available || device == null || device.presence != SignalPresence.RECENT -> "현재 수신되지 않음"
    device.rssiEma == null -> "신호 세기 확인 중"
    !walkSignalUsable(device) -> "현재 수신 중 · 약한 신호"
    else -> "현재 수신 중"
}

@Composable
private fun WalkRoute(pointIndex: Int, active: Boolean) {
    val primary = RfColors.primary
    val muted = RfColors.textDim
    val markerText = MaterialTheme.colorScheme.onPrimary
    val inactiveText = MaterialTheme.colorScheme.onSurface
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    Canvas(
        Modifier.fillMaxWidth().height(230.dp).semantics {
            contentDescription = "교실 이동 경로: P1 왼쪽 아래, P2 왼쪽 위, P3 오른쪽 위, P4 오른쪽 아래, P5 아래 중앙 교탁. 순서대로 이동. " +
                if (active) "완료 ${pointIndex.coerceIn(0, walkAnchors.size)}개, 현재 P${pointIndex + 1}" else "이동 경로"
        },
    ) {
        val radius = maxOf(15.dp.toPx(), 11.sp.toPx())
        val h = minOf(size.height - 2 * radius - 8.dp.toPx(), (size.width - 2 * radius) / .68f)
        if (h <= 0f) return@Canvas
        val w = .68f * h
        val origin = Offset((size.width - w) * .5f, (size.height - h) * .5f)
        fun position(index: Int): Offset {
            val anchor = walkAnchors[index]
            return origin + Offset(anchor.x * w, anchor.y * h)
        }
        drawRect(muted, origin, androidx.compose.ui.geometry.Size(w, h), style = Stroke(1.dp.toPx()))
        for (i in 0 until walkAnchors.lastIndex) {
            val from = position(i)
            val to = position(i + 1)
            drawLine(muted, from, to, strokeWidth = 2.dp.toPx())
            val delta = to - from
            val length = kotlin.math.sqrt(delta.x * delta.x + delta.y * delta.y)
            if (length > 0f) {
                val direction = delta / length
                val middle = from + delta * .5f
                val side = Offset(-direction.y, direction.x)
                drawLine(muted, middle, middle - direction * 8.dp.toPx() + side * 4.dp.toPx(), 2.dp.toPx())
                drawLine(muted, middle, middle - direction * 8.dp.toPx() - side * 4.dp.toPx(), 2.dp.toPx())
            }
        }
        paint.textSize = 11.sp.toPx()
        val baseline = (paint.ascent() + paint.descent()) * .5f
        for (index in walkAnchors.indices) {
            val point = position(index)
            val selected = active && index == pointIndex
            val completed = index < pointIndex
            drawCircle(if (selected || completed) primary else RfColors.surfaceAlt, radius, point)
            drawCircle(if (selected) Color.White else muted, radius, point, style = Stroke(1.dp.toPx()))
            paint.color = (if (selected || completed) markerText else inactiveText).toArgb()
            drawContext.canvas.nativeCanvas.drawText("P${index + 1}", point.x, point.y - baseline, paint)
        }
    }
    Text("교탁 / 앞쪽 · 축척 아님", style = MaterialTheme.typography.bodySmall)
}
