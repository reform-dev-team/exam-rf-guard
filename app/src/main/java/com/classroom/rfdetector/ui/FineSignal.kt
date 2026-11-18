package com.classroom.rfdetector.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.classroom.rfdetector.core.TrackedDevice

@Composable
internal fun FineSignal(device: TrackedDevice) {
    val points = device.fineReadings
    val value = points.lastOrNull()
    if (value == null || System.currentTimeMillis() - device.fineAtMs !in 0..3000) {
        Text("새 신호 대기 중")
        return
    }
    val delta = points.getOrNull(points.lastIndex - 1)?.let { value - it }
    Text("$value dBm", style = MaterialTheme.typography.displaySmall)
    if (delta != null) Text(when { delta > 0 -> "직전보다 +$delta dB"; delta < 0 -> "직전보다 $delta dB"; else -> "직전과 같음" })
    LinearProgressIndicator(progress = ((value + 100) / 75f).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth().height(8.dp))
    if (points.size > 1) {
        val color = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxWidth().height(52.dp)) {
            val low = points.min() - 2
            val high = maxOf(low + 12, points.max() + 2)
            fun at(i: Int) = Offset(size.width * i / points.lastIndex, size.height * (high - points[i]) / (high - low))
            for (i in 1..points.lastIndex) drawLine(color, at(i-1), at(i), 2.dp.toPx())
        }
        Text("최근 ${points.size}회 수신 · 오른쪽이 최신", style = MaterialTheme.typography.labelSmall)
    }
}
