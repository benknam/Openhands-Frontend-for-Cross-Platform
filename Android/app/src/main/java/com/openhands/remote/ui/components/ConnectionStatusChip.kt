package com.openhands.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openhands.remote.core.model.ConnectionState
import com.openhands.remote.ui.theme.OpenHandsColors

@Composable
fun ConnectionStatusChip(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.CONNECTED -> OpenHandsColors.success
        ConnectionState.CONNECTING,
        ConnectionState.AUTHENTICATING,
        ConnectionState.SYNCING -> OpenHandsColors.warning
        ConnectionState.AUTH_FAILED,
        ConnectionState.NOT_FOUND,
        ConnectionState.FAILED -> OpenHandsColors.error
        else -> OpenHandsColors.disconnected
    }
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("●", color = color, style = MaterialTheme.typography.labelSmall)
        Text(
            text = state.label(),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun ConnectionState.label(): String = when (this) {
    ConnectionState.AUTHENTICATING -> "认证中"
    ConnectionState.SYNCING -> "同步中"
    ConnectionState.CONNECTED -> "已连接"
    ConnectionState.CONNECTING -> "连接中"
    ConnectionState.DISCONNECTED -> "已断开"
    ConnectionState.WAITING_NETWORK -> "等待网络"
    ConnectionState.AUTH_FAILED -> "认证失败"
    ConnectionState.NOT_FOUND -> "会话不存在"
    ConnectionState.FAILED -> "连接失败"
    ConnectionState.IDLE -> "未连接"
}
