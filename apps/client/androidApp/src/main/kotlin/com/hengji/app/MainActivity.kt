package com.hengji.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.hengji.data.ProtectedLedgerOpenOutcome
import com.hengji.data.ProtectedLedgerOpenResult
import com.hengji.data.openAndroidProtectedLedger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var storageState by mutableStateOf<AndroidStorageState>(AndroidStorageState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val importPicker = AndroidImportDocumentPicker(this)
        val exportWriter = AndroidLedgerExportWriter(this)
        enableEdgeToEdge()
        setContent {
            when (val state = storageState) {
                AndroidStorageState.Loading -> AndroidStorageStartupStatus(
                    loading = true,
                    message = "正在安全打开本机账本…",
                )

                is AndroidStorageState.Opened -> HengjiApp(
                    repository = state.result.repository,
                    userImportDocumentPicker = importPicker,
                    ledgerExportWriter = exportWriter,
                    seedDemoData = state.result.outcome == ProtectedLedgerOpenOutcome.CREATED_EMPTY,
                )

                AndroidStorageState.Failed -> AndroidStorageStartupStatus(
                    loading = false,
                    message = "密钥、密文或旧账本迁移不可用。恒迹没有创建明文替代账本，原有文件保持不变。",
                    onRetry = ::openProtectedLedger,
                    onExit = ::finish,
                )
            }
        }
        openProtectedLedger()
    }

    private fun openProtectedLedger() {
        storageState = AndroidStorageState.Loading
        lifecycleScope.launch {
            storageState = try {
                val opened = withContext(Dispatchers.Default) {
                    openAndroidProtectedLedger(applicationContext)
                }
                AndroidStorageState.Opened(opened)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                AndroidStorageState.Failed
            }
        }
    }
}

private sealed interface AndroidStorageState {
    data object Loading : AndroidStorageState

    data class Opened(val result: ProtectedLedgerOpenResult) : AndroidStorageState

    data object Failed : AndroidStorageState
}
