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
fun DiscoveryPanel(source: ScanSource, state: DiscoveryState, baseline: Map<String, Int>?, watching: Boolean, nowMs: Long,
    baselineAddresses: Map<String, String> = emptyMap()) {
    var expanded by remember(source) { mutableStateOf(false) }
    val p2p = source == ScanSource.WIFI_P2P
    val current = state.isCurrent(nowMs)
    val groups = discoveryGroups(source, state.peers, baseline, baselineAddresses)
    val increased = if (current && watching) groups.count { it.increased } else 0
    Surface(color = RfColors.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (p2p) "Wi-Fi Direct 탐색" else "Bluetooth 일반 탐색", fontWeight = FontWeight.Bold)
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
            if (watching && baseline == null) Text("이 경로에는 비교에 필요한 기준 정보가 없습니다. 빈 교실부터 다시 측정하세요.", style = MaterialTheme.typography.bodySmall)
            if (current && watching && baseline != null) Text("빈 교실 때보다 늘어난 후보 ${increased}종", color = if (increased > 0) RfColors.warning else RfColors.textDim)
            if (state.reportedAtMs != null) {
                TextButton(onClick = { expanded = !expanded }) { Text("탐색 목록 ${state.peers.size}건 · ${if (expanded) "접기" else "펼치기"}") }
                if (expanded) {
                    if (groups.isEmpty()) Text("이번 목록에 보고된 기기가 없습니다.", style = MaterialTheme.typography.bodySmall)
                    groups.forEach { group ->
                        val mixedNames = group.peers.map { it.name?.trim()?.takeIf { name -> name.isNotEmpty() } }.distinct().size > 1
                        val label = if (mixedNames) "이름이 다른 탐색 결과"
                            else group.peers.first().name?.takeIf { it.isNotBlank() } ?: if (p2p) "이름 없는 P2P 기기" else "이름 없는 Bluetooth 기기"
                        Text("$label · ${group.peers.size}건", fontWeight = FontWeight.SemiBold)
                        if (watching && group.baselineCount != null) Text("빈 교실 때 ${group.baselineCount}건 → 탐색 목록 ${group.peers.size}건", style = MaterialTheme.typography.labelSmall)
                        group.peers.forEach { peer ->
                            if (mixedNames) Text(peer.name?.takeIf { it.isNotBlank() } ?: "이름 없음", style = MaterialTheme.typography.bodySmall)
                            val details = listOfNotNull(peer.kind, peer.rssi?.takeUnless { p2p }?.let { "탐색 때 세기 $it dBm" }).joinToString(" · ")
                            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text(if (p2p) "시스템이 보고한 P2P 목록입니다. 실제 패킷 수신 시각·세기·거리는 알 수 없으며 일반 Wi-Fi에 연결된 모든 기기를 찾지는 못합니다."
                else "검색에 응답하는 기기만 보입니다. BLE 목록과 같은 기기일 수 있으며 거리·실시간 위치를 뜻하지 않습니다.", style = MaterialTheme.typography.labelSmall, color = RfColors.textDim)
        }
    }
}
