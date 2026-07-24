package com.hengji.app

import com.hengji.app.application.LedgerExportWriter
import com.hengji.app.application.LedgerExportPolicy
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

class DesktopLedgerExportWriter : LedgerExportWriter {
    override suspend fun save(suggestedFileName: String, utf8Content: String, mediaType: String): String? {
        val export = LedgerExportPolicy.prepare(suggestedFileName, utf8Content, mediaType)
        val file = withContext(Dispatchers.Swing) {
            val chooser = JFileChooser().apply {
                dialogTitle = "保存衡记账本备份"
                selectedFile = File(export.fileName)
                fileFilter = FileNameExtensionFilter(
                    "${export.fileExtension.uppercase()} 文件",
                    export.fileExtension,
                )
                isAcceptAllFileFilterUsed = false
            }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        } ?: return null
        val target = if (file.extension.equals(export.fileExtension, ignoreCase = true)) {
            file
        } else {
            File(file.path + ".${export.fileExtension}")
        }
        withContext(Dispatchers.IO) {
            require(target.parentFile?.let { it.exists() || it.mkdirs() } != false) { "无法创建导出目录" }
            target.writeBytes(export.bytes)
        }
        return target.absolutePath
    }
}
