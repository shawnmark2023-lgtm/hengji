package com.hengji.app

import ai.onnxruntime.genai.Generator
import ai.onnxruntime.genai.GeneratorParams
import ai.onnxruntime.genai.GenAI
import ai.onnxruntime.genai.Model
import ai.onnxruntime.genai.Tokenizer
import ai.onnxruntime.OrtEnvironment
import com.hengji.insights.PersonalInsightModelAnswer
import com.hengji.insights.PersonalInsightModelCandidate
import com.hengji.insights.PersonalInsightModelContext
import com.hengji.insights.PersonalInsightModelProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Real on-device inference backed by the model shipped with the application. */
class BuiltInPersonalInsightModelProvider(
    private val modelDirectoryResolver: suspend () -> String,
) : PersonalInsightModelProvider, AutoCloseable {
    override val providerId: String = "恒迹内置 Qwen2.5-0.5B"
    override val privacyReviewed: Boolean = true

    private val inferenceLock = Mutex()
    private var runtime: Runtime? = null

    override suspend fun generate(context: PersonalInsightModelContext): PersonalInsightModelAnswer =
        inferenceLock.withLock {
            withContext(Dispatchers.Default) {
                val active = runtime ?: openRuntime(modelDirectoryResolver()).also { runtime = it }
                active.generate(context)
            }
        }

    override fun close() {
        runtime?.close()
        runtime = null
    }

    private fun openRuntime(path: String): Runtime {
        val directory = File(path).canonicalFile
        BuiltInAiModelManifest.verify(directory)
        // The standard ORT loader also installs provider support libraries. GenAI then reuses the
        // already-loaded ORT binary instead of extracting a lone DLL without its dependencies.
        OrtEnvironment.getEnvironment()
        System.setProperty("onnxruntime-genai.native.onnxruntime.skip", "true")
        GenAI.setTelemetry(false)
        return Runtime(Model(directory.absolutePath))
    }

    private class Runtime(
        private val model: Model,
    ) : AutoCloseable {
        private val tokenizer = Tokenizer(model)

        fun generate(context: PersonalInsightModelContext): PersonalInsightModelAnswer {
            val prompt = BuiltInInsightPrompt.render(context)
            val inputTokens = tokenizer.encode(prompt).use { it.getSequence(0) }
            val generated = buildString {
                tokenizer.createStream().use { stream ->
                    GeneratorParams(model).use { parameters ->
                        parameters.setSearchOption("max_length", (inputTokens.size + MAX_NEW_TOKENS).toDouble())
                        parameters.setSearchOption("do_sample", false)
                        Generator(model, parameters).use { generator ->
                            generator.appendTokens(inputTokens)
                            while (!generator.isDone) {
                                generator.generateNextToken()
                                append(stream.decode(generator.getLastTokenInSequence(0)))
                            }
                        }
                    }
                }
            }
            return BuiltInInsightAnswerParser.parse(generated, context)
        }

        override fun close() {
            tokenizer.close()
            model.close()
        }
    }

    private companion object {
        const val MAX_NEW_TOKENS = 180
    }
}

object BuiltInAiModelManifest {
    const val DIRECTORY_NAME: String = "hengji-qwen2.5-0.5b-int4-v1"
    const val MODEL_ID: String = "Qwen/Qwen2.5-0.5B-Instruct"
    const val MODEL_REVISION: String = "7ae557604adf67be50417f59c2c2f167def9a775"
    const val RUNTIME_VERSION: String = "0.15.0"

    val sha256ByFile: Map<String, String> = linkedMapOf(
        "added_tokens.json" to "F0ACEDF99E19EC0A5B797FFBD0B94328172CCD90F7F385BBC521CDA2701B03AB",
        "chat_template.jinja" to "8AA40CE145ADB73CB3A75194DC0224702A95850EC5275CABB728496BBD749FC6",
        "genai_config.json" to "7F16383747C17821ED978768A55628945486B09A6A406351945DE542BEB84265",
        "merges.txt" to "8831E4F1A044471340F7C0A83D7BD71306A5B867E95FD870F74D0C5308A904D5",
        "model.onnx" to "E6EF0307ECF64217AB71214B17676CB05D54237B9957A186BE8134F48E8D44BB",
        "model.onnx.data" to "AE69953EE6DDFDA221F27A6ABBDD8AEE24B184B351F3B40070D53F033E5A9885",
        "special_tokens_map.json" to "57255613BBE23C9497211CA68561FF429A51E871DBAF5A59998FA4C8F7FE168A",
        "tokenizer.json" to "9C5AE00E602B8860CBD784BA82A8AA14E8FEECEC692E7076590D014D7B7FDAFA",
        "tokenizer_config.json" to "4449EE16182D9A6638119B751661EF85FA506D21369722986B353166C31ED9B8",
        "vocab.json" to "CA10D7E9FB3ED18575DD1E277A2579C16D108E32F27439684AFA0E10B1440910",
    )

    fun verify(directory: File) {
        require(directory.isDirectory) { "Built-in model directory is missing" }
        sha256ByFile.forEach { (name, expected) ->
            val file = File(directory, name)
            require(file.isFile) { "Built-in model file is missing: $name" }
            require(file.sha256() == expected) { "Built-in model integrity check failed: $name" }
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02X".format(byte) }
    }
}

private object BuiltInInsightPrompt {
    fun render(context: PersonalInsightModelContext): String = buildString {
        append("<|im_start|>system\n")
        append("你是恒迹内置的本机消费分析助手。数据不会离开设备。")
        append("只分析给出的事实，不猜测身份、收入或动机，不提供投资、借贷、税务建议。")
        append("从候选中选最值得用户现在关注的一项。标题、解释、下一步都不要写数字，数字由界面展示。")
        append("严格只输出五行：候选、标题、解释、依据、下一步。<|im_end|>\n")
        append("<|im_start|>user\n")
        append("消费记录：${context.exactExpenseCount}笔，覆盖${context.exactHistoryDays}天、")
        append("${context.observedExpenseMonthCount}个自然月；学习阶段=${context.learningStage}。\n")
        if (context.preferredInsightTypes.isNotEmpty()) {
            append("用户反馈更关注：${context.preferredInsightTypes.joinToString()}。\n")
        }
        if (context.priorAnalysisSummaries.isNotEmpty()) {
            append("过去分析（避免机械重复）：")
            append(context.priorAnalysisSummaries.joinToString("；"))
            append("\n")
        }
        context.candidates.forEach { candidate -> appendCandidate(candidate) }
        append("输出格式：\n候选:candidate-1\n标题:不超过二十个汉字\n解释:一句自然中文\n")
        append("依据:只能填所选候选已有的依据代码，用英文逗号分隔\n下一步:一个容易执行的动作")
        append("<|im_end|>\n<|im_start|>assistant\n")
    }

    private fun StringBuilder.appendCandidate(candidate: PersonalInsightModelCandidate) {
        append(candidate.candidateKey)
        append(" 类型=")
        append(typeLabel(candidate.insightType))
        append(" 可信度=")
        append(candidate.confidenceBasisPoints)
        append(" 依据=")
        append(candidate.exactEvidence.joinToString(";") { evidence ->
            val value = evidence.numericValue?.toString() ?: evidence.textValue.orEmpty()
            "${evidence.code}:${evidence.kind}:$value"
        })
        append("\n")
    }

    private fun typeLabel(type: String): String = when (type) {
        "category_concentration" -> "某类消费占比突出"
        "budget_pace" -> "预算使用过快"
        "spending_trend" -> "近期支出变化"
        "merchant_concentration" -> "消费集中于同一商家"
        "large_expense" -> "大额消费"
        "possible_duplicate" -> "可能重复记账"
        "possible_subscription" -> "可能是固定扣款"
        "low_usage_asset" -> "物品使用较少"
        "sell_candidate" -> "闲置物品处理"
        "price_target_reached" -> "出售目标已达到"
        else -> "消费变化"
    }
}

private object BuiltInInsightAnswerParser {
    fun parse(raw: String, context: PersonalInsightModelContext): PersonalInsightModelAnswer {
        val fields = raw
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { line ->
                val separator = listOf(line.indexOf(':'), line.indexOf('：')).filter { it >= 0 }.minOrNull()
                    ?: return@mapNotNull null
                line.substring(0, separator).trim() to line.substring(separator + 1).trim()
            }
            .toMap()
        val candidateKey = fields["候选"] ?: error("Built-in model omitted candidate")
        val candidate = context.candidates.firstOrNull { it.candidateKey == candidateKey }
            ?: error("Built-in model selected an unknown candidate")
        val headline = fields.getValue("标题").requireNaturalLanguage("标题", 80)
        val summary = fields.getValue("解释").requireNaturalLanguage("解释", 500)
        val action = fields.getValue("下一步").requireNaturalLanguage("下一步", 80)
        fields.getValue("依据")
        // Evidence is application-owned. Once the model selects a verified candidate, use that
        // candidate's complete whitelist rather than interpreting free-form model punctuation.
        val evidenceCodes = candidate.evidenceCodes
        return PersonalInsightModelAnswer(candidateKey, headline, summary, evidenceCodes, action)
    }

    private fun String.requireNaturalLanguage(label: String, limit: Int): String = trim().also { value ->
        require(value.length in 1..limit) { "$label length is invalid" }
        require(value.none(Char::isDigit)) { "$label must leave numeric claims to verified UI evidence" }
    }
}
