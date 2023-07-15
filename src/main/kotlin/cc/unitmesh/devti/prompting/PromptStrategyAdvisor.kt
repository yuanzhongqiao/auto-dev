package cc.unitmesh.devti.prompting

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.EncodingType

data class FinalPrompt(val prefixCode: String, val suffixCode: String) {}

@Service(Service.Level.PROJECT)
class PromptStrategyAdvisor(val project: Project) {

    // The default OpenAI Token will be 4096, we leave 3072 for the code.
    var tokenLength = 3072
    private var registry: EncodingRegistry? = Encodings.newDefaultEncodingRegistry()
    private var encoding: Encoding = registry?.getEncoding(EncodingType.CL100K_BASE)!!
    private val psiElementFactory = project.let { JavaPsiFacade.getElementFactory(it) }
    private val javaPsiFacade = JavaPsiFacade.getInstance(project)

    fun advice(prefixCode: String, suffixCode: String): FinalPrompt {
        val tokenCount: Int = encoding.countTokens(prefixCode)
        if (tokenCount < tokenLength) {
            return FinalPrompt(prefixCode, suffixCode)
        }

        // remove all `import` syntax in java code, should contain with new line
        val importRegexWithNewLine = Regex("import .*;\n")
        val prefixCodeWithoutImport = prefixCode.replace(importRegexWithNewLine, "")
        val tokenCountWithoutImport: Int = encoding.countTokens(prefixCodeWithoutImport)

        if (tokenCountWithoutImport < tokenLength) {
            return FinalPrompt(prefixCodeWithoutImport, suffixCode)
        }

        // keep only the service calling?
        return FinalPrompt(prefixCodeWithoutImport, suffixCode)
    }

    fun advice(javaCode: PsiClass, calleeName: String): FinalPrompt {
        val codeString = javaCode.text
        val textbase = advice(codeString, "")
        if (encoding.countTokens(textbase.prefixCode) < tokenLength) {
            return FinalPrompt(codeString, "")
        }

        // for Web controller, service, repository, etc.
        val fields = javaCode.fields.filter {
            it.type.canonicalText == calleeName
        }.map {
            it.name
        }

        if (fields.isEmpty()) {
            return FinalPrompt(codeString, "")
        }

        val lines = getByFields(fields, codeString)

        return FinalPrompt(lines, "")
    }

    private fun getByFields(
        fields: List<@NlsSafe String>,
        codeString: @NlsSafe String
    ): String {
        val firstFieldName = fields[0]
        // get all line when match by field usage (by Regex)
        val regex = Regex(".*\\s+$firstFieldName\\..*")
        val lines = codeString.split("\n").filter {
            it.matches(regex)
        }

        return lines.joinToString("") { "        {some other code}\n$it\n" }
    }

    companion object {
        private val logger = Logger.getInstance(PromptStrategyAdvisor::class.java)
    }
}