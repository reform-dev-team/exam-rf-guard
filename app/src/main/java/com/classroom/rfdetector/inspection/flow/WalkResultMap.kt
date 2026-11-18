package com.classroom.rfdetector.inspection.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.classroom.rfdetector.core.TrackedDevice
import com.classroom.rfdetector.ui.RfColors

private val markerTarget = 48.dp

private data class MapSignal(val candidate: WalkCandidate, val number: Int) {
    val address get() = candidate.trackingKey.removePrefix("BLE:")
    val zone get() = checkNotNull(candidate.zone)
}

private data class MapPin(val x: Dp, val y: Dp, val signals: MutableList<MapSignal>)

@Composable
internal fun WalkResultMap(
    located: List<WalkCandidate>,
    devices: Map<String, TrackedDevice>,
    available: Boolean,
    onFind: (String) -> Unit,
) {
    var overlap by remember(located) { mutableStateOf<List<MapSignal>?>(null) }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val width = minOf(maxWidth, 225.dp)
        val height = width / .68f
        val travelX = (width - markerTarget).coerceAtLeast(0.dp)
        val travelY = (height - markerTarget).coerceAtLeast(0.dp)
        val pins = remember(located, travelX, travelY) {
            val groups = mutableListOf<MapPin>()
            located.forEachIndexed { index, candidate ->
                val signal = MapSignal(candidate, index + 1)
                val x = travelX * signal.zone.x
                val y = travelY * signal.zone.y
                // Keep the first fit as the pin, grouping any overlapping 48dp hit boxes.
                val existing = groups.firstOrNull { (it.x - x).value in -50f..50f && (it.y - y).value in -50f..50f }
                if (existing != null) existing.signals.add(signal)
                else groups.add(MapPin(x, y, mutableListOf(signal)))
            }
            groups
        }
        Box(
            Modifier.size(width, height).testTag("walk-result-map")
                .background(RfColors.surfaceAlt).border(1.dp, RfColors.textDim),
        ) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).size(36.dp, 4.dp).background(RfColors.textDim))
            for (pin in pins) {
                val first = pin.signals.first()
                val single = pin.signals.size == 1
                val device = devices[first.candidate.trackingKey]
                val live = available && pin.signals.any { signal ->
                    devices[signal.candidate.trackingKey]?.let(::walkSignalUsable) == true
                }
                Box(
                    Modifier.absoluteOffset(pin.x, pin.y).size(markerTarget)
                        .semantics {
                            contentDescription = if (single) "${first.address} 신호 찾기"
                            else "겹친 신호 ${pin.signals.size}개, ${first.number}번부터 선택"
                            if (single) stateDescription = "${first.zone.label} · ${walkAvailability(available, device)}"
                        }
                        .clickable(role = Role.Button) {
                            if (single) onFind(first.candidate.trackingKey) else overlap = pin.signals
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val scheme = MaterialTheme.colorScheme
                    Box(
                        Modifier.size(38.dp).background(if (live) scheme.primary else scheme.surface, CircleShape)
                            .border(1.dp, scheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (single) first.number.toString() else "×${pin.signals.size}",
                            color = if (live) scheme.onPrimary else scheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
    Text("교탁 / 앞쪽 · 축척 아님", style = MaterialTheme.typography.bodySmall)
    if (located.isNotEmpty()) {
        Text("대략적인 신호 위치입니다. 표시를 눌러 찾으세요. ×숫자는 겹친 신호 수입니다.")
    }
    overlap?.let { signals ->
        AlertDialog(
            onDismissRequest = { overlap = null },
            title = { Text("겹친 신호 선택") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(signals, key = { it.candidate.trackingKey }) { signal ->
                        val device = devices[signal.candidate.trackingKey]
                        TextButton(
                            onClick = { overlap = null; onFind(signal.candidate.trackingKey) },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${signal.address} 신호 찾기" },
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text("${signal.number} · ${device?.displayName ?: "이전 신호"}")
                                Text(signal.address)
                                Text(walkAvailability(available, device), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { overlap = null }) { Text("닫기") } },
        )
    }
}
