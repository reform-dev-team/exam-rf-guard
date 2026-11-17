package com.classroom.rfdetector.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.classroom.rfdetector.core.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiscoveryPanel(source: ScanSource, state: DiscoveryState, baseline: Map<String, Int>?, watching: Boolean, nowMs: Long) {
    var expanded by remember(source) { mutableStateOf(false) }
    val current = state.isCurrent(nowMs)
    val groups = discoveryGroups(source, state.peers, baseline)
    val increased = if (current && watching) groups.count { it.increased } else 0
    Surface(color = RfColors.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bluetooth 일반 탐색", fontWeight = FontWeight.Bold)
            Text("2분마다 추가 탐색 · 연속 수신과 별도", style = MaterialTheme.typography.labelSmall, color = RfColors.textDim)
            Text(when (state.status) {
                DiscoveryStatus.IDLE -> "첫 탐색 대기"
                DiscoveryStatus.SCANNING -> "추가 탐색 중"
                DiscoveryStatus.COMPLETE -> if (current) "탐색 완료" else "이전 탐색 결과 · 최신 여부 확인 필요"
                DiscoveryStatus.FAILED -> "추가 탐색 실패 · 이전 또는 부분 결과"
                DiscoveryStatus.TIMED_OUT -> "추가 탐색 시간 초과 · 이전 또는 부분 결과"
                DiscoveryStatus.BUSY -> "추가 탐색 대기 · 다른 탐색 사용 중"
                DiscoveryStatus.UNSUPPORTED -> "이 기기에서 지원하지 않습니다"
                DiscoveryStatus.PAUSED -> "추가 탐색 중지 · 이전 결과"
            }, color = if (current) RfColors.primary else RfColors.warning)
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.reportedAtMs?.let {
                Text("목록 수신 ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))}", style = MaterialTheme.typography.labelSmall)
            }
            if (watching && baseline == null) Text("이 경로의 빈 교실 기준 기록이 없습니다. 비교하려면 빈 교실부터 다시 측정하세요.", style = MaterialTheme.typography.bodySmall)
            if (current && watching && baseline != null) Text("빈 교실 때보다 늘어난 후보 ${increased}종", color = if (increased > 0) RfColors.warning else RfColors.textDim)
            if (state.reportedAtMs != null) {
                TextButton(onClick = { expanded = !expanded }) { Text("탐색 목록 ${state.peers.size}건 · ${if (expanded) "접기" else "펼치기"}") }
                if (expanded) {
                    if (groups.isEmpty()) Text("이번 목록에 보고된 기기가 없습니다.", style = MaterialTheme.typography.bodySmall)
                    groups.forEach { group ->
                        Text((group.peers.first().name?.takeIf { it.isNotBlank() } ?: "이름 없는 Bluetooth 기기") + " · ${group.peers.size}건", fontWeight = FontWeight.SemiBold)
                        if (watching && group.baselineCount != null) Text("빈 교실 때 ${group.baselineCount}건 → 탐색 목록 ${group.peers.size}건", style = MaterialTheme.typography.labelSmall)
                        group.peers.forEach { peer ->
                            Text(listOfNotNull(peer.kind, peer.rssi?.let { "탐색 때 세기 $it dBm" }).joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text("검색에 응답하는 기기만 보입니다. BLE 목록과 같은 기기일 수 있으며 거리·실시간 위치를 뜻하지 않습니다.", style = MaterialTheme.typography.labelSmall, color = RfColors.textDim)
        }
    }
}
