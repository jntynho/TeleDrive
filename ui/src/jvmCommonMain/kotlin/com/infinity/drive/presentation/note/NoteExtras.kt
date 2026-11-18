package com.infinity.drive.presentation.note

/**
 * Links use a Drive id so they still resolve after the local notes and
 * attachments have been rebuilt from the same Telegram channel.
 */
object NoteExtras {
    data class Attachment(val name: String, val fileId: String)
    data class Task(val line: Int, val name: String, val checked: Boolean)

    private val linkPattern = Regex("\\[([^\\]\\r\\n]+)]\\(teledrive://file/([a-zA-Z0-9_-]{1,80})\\)")
    private val taskPattern = Regex("^(\\s*[-*] +\\[)([ xX])(] +)(.+)$")

    fun attachmentLink(name: String, id: String): String {
        val label = name.replace('[', '(').replace(']', ')').replace('\r', ' ').replace('\n', ' ')
        return "[$label](teledrive://file/$id)"
    }

    fun attachments(body: String): List<Attachment> =
        linkPattern.findAll(body).map { Attachment(it.groupValues[1], it.groupValues[2]) }
            .distinctBy { it.fileId }.toList()

    fun tasks(body: String): List<Task> = body.lineSequence().mapIndexedNotNull { index, line ->
        taskPattern.matchEntire(line.removeSuffix("\r"))?.let {
            Task(index, it.groupValues[4], it.groupValues[2] != " ")
        }
    }.toList()

    fun toggle(body: String, task: Task): String {
        val lines = body.split('\n').toMutableList()
        val line = lines.getOrNull(task.line) ?: return body
        val matched = taskPattern.matchEntire(line.removeSuffix("\r")) ?: return body
        if (matched.groupValues[4] != task.name || (matched.groupValues[2] != " ") != task.checked) return body
        lines[task.line] = line.replaceRange(requireNotNull(matched.groups[2]).range, if (task.checked) " " else "x")
        return lines.joinToString("\n")
    }
}
