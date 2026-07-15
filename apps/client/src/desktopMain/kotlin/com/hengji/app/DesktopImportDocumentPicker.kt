package com.hengji.app

import com.hengji.app.application.PickedImportDocument
import com.hengji.app.application.UserImportDocumentPicker
import com.hengji.app.importflow.ImportDocumentFormat
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

class DesktopImportDocumentPicker : UserImportDocumentPicker {
    override suspend fun pick(format: ImportDocumentFormat): PickedImportDocument? {
        val file = withContext(Dispatchers.Swing) {
            val extension = format.name.lowercase()
            val chooser = JFileChooser().apply {
                dialogTitle = "选择要导入的 ${extension.uppercase()} 消费记录"
                fileFilter = FileNameExtensionFilter("${extension.uppercase()} 文件", extension)
                isAcceptAllFileFilterUsed = false
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        } ?: return null
        return withContext(Dispatchers.IO) { readSelectedFile(file, format) }
    }

    private fun readSelectedFile(file: File, format: ImportDocumentFormat): PickedImportDocument {
        require(file.isFile) { "所选路径不是文件" }
        require(file.length() in 1..MAX_IMPORT_BYTES) { "文件必须小于 5 MiB 且不能为空" }
        val expectedExtension = format.name.lowercase()
        require(file.extension.equals(expectedExtension, ignoreCase = true)) { "文件扩展名与所选格式不一致" }
        return PickedImportDocument(
            displayName = file.name,
            content = file.readText(Charsets.UTF_8),
            format = format,
        )
    }

    private companion object {
        const val MAX_IMPORT_BYTES: Long = 5L * 1024L * 1024L
    }
}
