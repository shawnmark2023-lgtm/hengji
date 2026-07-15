package com.hengji.app

import com.hengji.app.application.LedgerExportWriter
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

class DesktopLedgerExportWriter : LedgerExportWriter {
    override suspend fun save(suggestedFileName: String, utf8Content: String, mediaType: String): String? {
        val file = withContext(Dispatchers.Swing) {
            val chooser = JFileChooser().apply {
                dialogTitle = "保存衡记账本备份"
                selectedFile = File(suggestedFileName)
                val extension = suggestedFileName.substringAfterLast('.', "json")
                fileFilter = FileNameExtensionFilter("${extension.uppercase()} 文件", extension)
                isAcceptAllFileFilterUsed = false
            }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        } ?: return null
        val extension = suggestedFileName.substringAfterLast('.', "json")
        val target = if (file.extension.equals(extension, ignoreCase = true)) file else File(file.path + ".$extension")
        withContext(Dispatchers.IO) {
            require(target.parentFile?.let { it.exists() || it.mkdirs() } != false) { "无法创建导出目录" }
            target.writeText(utf8Content, Charsets.UTF_8)
        }
        return target.absolutePath
    }
}
