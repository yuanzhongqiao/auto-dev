package cc.unitmesh.devti.language.compiler.exec

import cc.unitmesh.devti.language.compiler.model.LineInfo
import cc.unitmesh.devti.language.utils.lookupFile
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager

/**
 * FileAutoCommand is responsible for reading a file and returning its contents.
 *
 * @param myProject the Project in which the file operations are performed
 * @param prop the property string containing the file name and optional line range
 *
 */
class FileInsCommand(private val myProject: Project, private val prop: String) : InsCommand {
    private val logger = logger<FileInsCommand>()
    private val output = StringBuilder()

    override fun execute(): String? {
        val range: LineInfo? = LineInfo.fromString(prop)

        // prop name can be src/file.name#L1-L2
        val filename = prop.split("#")[0]
        val virtualFile = myProject.lookupFile(filename)

        val contentsToByteArray = virtualFile?.contentsToByteArray()
        if (contentsToByteArray == null) {
            logger.warn("File not found: $virtualFile")
            return null
        }

        contentsToByteArray.let { bytes ->
            val lang = virtualFile.let {
                PsiManager.getInstance(myProject).findFile(it)?.language?.displayName
            } ?: ""

            val content = bytes.toString(Charsets.UTF_8)
            val fileContent = if (range != null) {
                val subContent = try {
                    content.split("\n").slice(range.startLine - 1 until range.endLine)
                        .joinToString("\n")
                } catch (e: StringIndexOutOfBoundsException) {
                    content
                }

                subContent
            } else {
                content
            }

            output.append("\n```$lang\n")
            output.append(fileContent)
            output.append("\n```\n")
        }

        return output.toString()
    }
}

