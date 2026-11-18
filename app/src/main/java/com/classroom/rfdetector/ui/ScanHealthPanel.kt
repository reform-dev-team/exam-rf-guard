package com.classroom.rfdetector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.classroom.rfdetector.core.ScanHealth
import com.classroom.rfdetector.core.ScanState

@Composable
internal fun ScanHealthPanel(ble: ScanHealth, wifi: ScanHealth) {
    val failed = listOf(ble, wifi).any { it.state == ScanState.ERROR || it.state == ScanState.STOPPED }
    Surface(Modifier.fillMaxWidth(), color = RfColors.surfaceAlt) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("수신 상태", color = if (failed) RfColors.warning else RfColors.textPrimary)
            Text("블루투스 · ${ble.message}", color = RfColors.textDim)
            Text("와이파이 · ${wifi.message}", color = RfColors.textDim)
        }
    }
}
