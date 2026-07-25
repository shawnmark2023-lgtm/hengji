package com.hengji.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hengji.app.theme.HengjiTheme

@Composable
fun AndroidStorageStartupStatus(
    loading: Boolean,
    message: String,
    onRetry: (() -> Unit)? = null,
    onExit: (() -> Unit)? = null,
) {
    HengjiTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Text("无法安全打开本机账本")
                }
                Text(message)
                onRetry?.let { retry ->
                    Button(onClick = retry) {
                        Text("重试")
                    }
                }
                onExit?.let { exit ->
                    OutlinedButton(onClick = exit) {
                        Text("退出")
                    }
                }
            }
        }
    }
}
