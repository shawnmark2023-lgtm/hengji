package com.hengji.app

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Materializes signed APK assets into a private, non-backed-up directory required by ONNX Runtime. */
internal object AndroidBuiltInModelAssets {
    fun ensureInstalled(context: Context): File {
        val root = File(context.noBackupFilesDir, "built-in-ai")
        val target = File(root, BuiltInAiModelManifest.DIRECTORY_NAME)
        val marker = File(target, ".verified-${BuiltInAiModelManifest.MODEL_REVISION}")
        if (marker.isFile) return target

        val staging = File(root, "${BuiltInAiModelManifest.DIRECTORY_NAME}.staging")
        require(staging.canonicalPath.startsWith(root.canonicalPath + File.separator))
        require(target.canonicalPath.startsWith(root.canonicalPath + File.separator))
        staging.deleteRecursively()
        staging.mkdirs()
        BuiltInAiModelManifest.sha256ByFile.forEach { (name, expectedHash) ->
            val destination = File(staging, name)
            val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open("models/${BuiltInAiModelManifest.DIRECTORY_NAME}/$name").use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                }
            }
            val actualHash = digest.digest().joinToString("") { byte -> "%02X".format(byte) }
            require(actualHash == expectedHash) { "Built-in model asset failed integrity check: $name" }
        }
        if (target.exists()) target.deleteRecursively()
        check(staging.renameTo(target)) { "Unable to install the built-in model in private storage" }
        marker.writeText("verified\n", Charsets.UTF_8)
        return target
    }
}
