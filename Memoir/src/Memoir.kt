// Copyright (c) 2020 William Arthur Hood
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights to
// use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
// of the Software, and to permit persons to whom the Software is furnished
// to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included
// in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
// OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
// HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
// WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
// FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
// OTHER DEALINGS IN THE SOFTWARE.

package rockabilly.memoir

import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

private const val STARTING_CONTENT = "<table>\r\n"
private val PLAINTEXT_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
private val HTML_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val HTML_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

private fun defaultHeader(title: String): String {
    // Still need to convert this from C#
    return "<h1>$title</h1>\r\n<hr>\r\n<small><i>Powered by the Memoir Logging System...</i></small>\r\n\r\n"
}

class MemoirConcludedException: Exception(ALREADY_CONCLUDED_MESSAGE) { }

class Memoir (val title: String = UNSET_STRING, val forPlainText: PrintWriter? = null, val forHTML: PrintWriter? = null, val Header: (String)->String = ::defaultHeader) {
    private val printWriter_HTML: PrintWriter? = forHTML
    private val printWriter_PlainText: PrintWriter? = forPlainText
    private val content = StringBuilder(STARTING_CONTENT)
    private var isConcluded = false
    private var titleName = title

    init {
        val timeStamp = LocalDateTime.now()

        if (printWriter_PlainText != null) {
            EchoPlainText("")
            EchoPlainText(titleName, EMOJI_MEMOIR, timeStamp)
        }

        if (printWriter_HTML != null) {
            printWriter_HTML.print("<html>\r\n<meta charset=\"UTF-8\">\r\n<head>\r\n<title>$title</title>\r\n")
            printWriter_HTML.print(MEMOIR_LOG_STYLING)
            printWriter_HTML.print("</head>\r\n<body>\r\n")
            printWriter_HTML.print(Header(title))
        }
    }

    val wasUsed: Boolean
        get() = (content.length - STARTING_CONTENT.length) > 0

    internal val encapsulationTag: String
        get() = "lvl-${UUID.randomUUID()}"

    // TODO
    fun Conclude(): String {
        if (!isConcluded) {
            EchoPlainText("", EMOJI_TEXT_MEMOIR_CONCLUDE)
            EchoPlainText("")

            isConcluded = true

            content.append("\r\n</table>")

            if (printWriter_HTML != null) {
                printWriter_HTML.print(content.toString())
                printWriter_HTML.print("\r\n</body>\r\n</html>")
                printWriter_HTML.flush()
                printWriter_HTML.close()
            }
        }

        return content.toString()
    }

    // Parameter order differs from the C# version
    fun EchoPlainText(message: String, emoji: String = EMOJI_TEXT_BLANK_LINE, timeStamp: LocalDateTime? = LocalDateTime.now()) {
        if (printWriter_PlainText == null) {
            // Silently decline
            return
        }

        if (isConcluded) {
            throw MemoirConcludedException()
        }

        var dateTime = "                        "
        if (timeStamp != null) {
            dateTime = timeStamp.format(PLAINTEXT_DATETIME_FORMATTER)
        }

        printWriter_PlainText.println("$dateTime $emoji\t$message")
    }

    // Parameter order differs from the C# version
    fun WriteToHTML(message: String, emoji: String = EMOJI_TEXT_BLANK_LINE, timeStamp: LocalDateTime? = LocalDateTime.now()) {
        if (isConcluded) {
            throw MemoirConcludedException()
        }

        var date = "&nbsp;"
        var time = "&nbsp;"

        if (timeStamp != null) {
            date = timeStamp.format(HTML_DATE_FORMATTER)
            time = timeStamp.format(HTML_TIME_FORMATTER)
        }

        content.append("<tr><td class=\"min\"><small>$date</small></td><td>&nbsp;</td><td class=\"min\"><small>$time</small></td><td>&nbsp;</td><td><h2>$emoji</h2></td><td>$message</td></tr>\r\n")
    }

    private fun highlight(message: String, style: String = "highlighted"): String {
        return "<p class=\"$style outlined\">&nbsp;$message&nbsp;</p>"
    }

    fun Info(message: String, emoji: String = EMOJI_TEXT_BLANK_LINE) {
        val timeStamp = LocalDateTime.now()
        WriteToHTML(message, emoji, timeStamp)
        EchoPlainText(message, emoji, timeStamp)
    }

    fun Debug(message: String) {
        val timeStamp = LocalDateTime.now()
        WriteToHTML(highlight(message), EMOJI_DEBUG, timeStamp)
        EchoPlainText(message, EMOJI_DEBUG, timeStamp)
    }

    fun Error(message: String) {
        val timeStamp = LocalDateTime.now()
        WriteToHTML(highlight(message), EMOJI_ERROR, timeStamp)
        EchoPlainText(message, EMOJI_ERROR, timeStamp)
    }

    fun SkipLine() {
        WriteToHTML("")
        EchoPlainText("")
    }

    private fun wrapAsSublog(memoirTitle: String, memoirContent: String, style: String = "neutral"): String {
        val identifier = UUID.randomUUID().toString()
        return "\r\n\r\n<div class=\"memoir $style\">\r\n<label for=\"$identifier\">\r\n<input id=\"$identifier\" class=\"gone\" type=\"checkbox\">\r\n<h2>$memoirTitle</h2>\r\n<div class=\"$encapsulationTag\">\r\n$memoirContent\r\n</div></label>"
    }

    fun ShowMemoir(subordinate: Memoir, emoji: String = EMOJI_MEMOIR, style: String = "neutral") {
        val timeStamp = LocalDateTime.now()
        val subordinateContent = subordinate.Conclude()
        WriteToHTML(wrapAsSublog(subordinate.titleName, subordinateContent, style), emoji, timeStamp)
    }

    // TODO: attemptBase64Decode

    // TODO: JSON Pretty-print

    // TODO ShowException (Throwable???)

    // Decide if a single Show method or many??? Maybe keep the many but allow a "show()" to make an intelligent choice???
}
