package com.hengji.app

import com.hengji.app.application.PickedImportDocument
import com.hengji.app.application.UserDocumentPolicy
import com.hengji.app.application.UserDocumentPurpose
import com.hengji.app.application.UserImportDocumentPicker
import com.hengji.app.importflow.ImportDocumentFormat
import java.io.ByteArrayOutputStream
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

class DesktopImportDocumentPicker : UserImportDocumentPicker {
    override suspend fun pick(
        format: ImportDocumentFormat,
        purpose: UserDocumentPurpose,
    ): PickedImportDocument? {
        val file = withContext(Dispatchers.Swing) {
            val extension = format.name.lowercase()
            val chooser = JFileChooser().apply {
                dialogTitle = "选择要导入的 ${extension.uppercase()} 消费记录"
                fileFilter = FileNameExtensionFilter("${extension.uppercase()} 文件", extension)
                isAcceptAllFileFilterUsed = false
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        } ?: return null
        return withContext(Dispatchers.IO) { readSelectedFile(file, format, purpose) }
    }

    private fun readSelectedFile(
        file: File,
        format: ImportDocumentFormat,
        purpose: UserDocumentPurpose,
    ): PickedImportDocument {
        require(file.isFile) { "所选路径不是文件" }
        val maximumBytes = UserDocumentPolicy.maximumBytes(purpose)
        require(file.length() in 1..maximumBytes.toLong()) {
            if (purpose == UserDocumentPurpose.LedgerRestore) {
                "账本备份必须小于 25 MiB 且不能为空"
            } else {
                "文件必须小于 5 MiB 且不能为空"
            }
        }
        return UserDocumentPolicy.decode(
            displayName = file.name,
            bytes = file.inputStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maximumBytes) { "文件超过安全上限" }
                    output.write(buffer, 0, read)
                }
                require(total > 0) { "文件为空" }
                output.toByteArray()
            },
            format = format,
            purpose = purpose,
        )
    }
}
