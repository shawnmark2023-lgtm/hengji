package com.hengji.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hengji.data.room.RoomLedgerRepository
import com.hengji.data.room.RoomStoragePolicy
import com.hengji.data.room.createAndroidLedgerRepository

class MainActivity : ComponentActivity() {
    private lateinit var repository: RoomLedgerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = createAndroidLedgerRepository(
            context = applicationContext,
            policy = RoomStoragePolicy.ALLOW_UNENCRYPTED_DEVELOPMENT,
        )
        val importPicker = AndroidImportDocumentPicker(this)
        val exportWriter = AndroidLedgerExportWriter(this)
        enableEdgeToEdge()
        setContent {
            HengjiApp(repository, importPicker, exportWriter)
        }
    }

    override fun onDestroy() {
        repository.close()
        super.onDestroy()
    }
}
