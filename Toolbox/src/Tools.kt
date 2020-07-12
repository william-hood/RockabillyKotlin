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

package rockabilly.toolbox

import java.io.*
import java.net.URL
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.AbstractMap.SimpleEntry
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import kotlin.collections.ArrayList

val stdout = PrintWriter(System.out)
val stderr = PrintWriter(System.err)

private val quickDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd kk-mm-ss.SSS")

val QuickTimeStamp: String
    get() = quickDateFormat.format(LocalDateTime.now())

@Throws(FileNotFoundException::class)
// TODO: Only used in Delimited Data Manager. Candidate for obsolescence.
fun openForReading(filePath: String?): BufferedReader? {
    return BufferedReader(FileReader(filePath)) // (filePath,
    // FileMode.Open,
    // FileAccess.Read,
    // FileShare.Read);
}

// Legacy method readLineFromInputStream() is OBSOLETE
// val check = BufferedReader(InputStreamReader(rawInputStream))
// check.readLine()
// There is also readLine for stdin and File.forEachLine() for files...

// Legacy method readEntireInputStream() is OBSOLETE
//  val check = BufferedReader(InputStreamReader(rawInputStream))
//  check.readText()
// File.readLines() can get the whole file as an array of lines
// Creating the BufferedInputStream as shown above also provides readLines()
// String(rawInputStream.readAllBytes())

// Based on http://stackoverflow.com/questions/8668905/directory-does-not-exist-with-filewriter
fun forceParentDirectoryExistence(fileName: String?) {
    var file: File? = File(fileName)
    var parent_directory = file!!.parentFile
    parent_directory?.mkdirs()
    parent_directory = null
    file = null
}

fun forceDirectoryExistence(directory: String) {
    forceParentDirectoryExistence(directory + File.separator + "DELTHIS.txt")
}

private const val REPLACER = "\u25a2"
fun filterOutNonPrintables(candidate: String): String? {
    return candidate.replace("\\p{C}".toRegex(), REPLACER)
}

private const val nullString = "(null)"
fun robustGetString(candidate: Any?): String? {
    return if (candidate == null) nullString else try {
        filterOutNonPrintables(candidate.toString())
    } catch (dontCare: NullPointerException) {
        nullString
    } catch (dontCare: Throwable) {
        "(ERROR)"
    }
}

fun getOperatingSystemName(): String? {
    return System.getProperty("os.name")
}

fun getCurrentWorkingDirectory(): String? {
    return System.getProperty("user.dir")
}

fun getUserHomeFolder(): String? {
    return System.getProperty("user.home")
}

fun getShortFileName(completeFilePath: String): String? {
    val baseExt = completeFilePath.substring(completeFilePath
            .lastIndexOf(File.separatorChar) + 1)
    return if (completeFilePath.contains(".")) {
        baseExt.substring(0, baseExt.lastIndexOf('.'))
    } else baseExt
}

// TODO: Should this still exist? Does Memoir handle it better?
fun depictFailure(thisFailure: Throwable): String? {
    val stacktraceWriter = StringWriter()
    thisFailure.printStackTrace(PrintWriter(stacktraceWriter))
    /*
		 * Throwable cause = thisFailure.getCause(); if (cause != null) {
		 * stacktraceWriter.append(Symbols.NewLine);
		 * stacktraceWriter.append("Caused by " + FX.arrow());
		 * stacktraceWriter.append(depictFailure(cause)); }
		 */return stacktraceWriter.toString()
}

// From http://stackoverflow.com/questions/2546078/java-random-long-number-in-0-x-n-range
fun nextLong(rng: Random, n: Long): Long {
    // error checking and 2^x checking removed for simplicity.
    var bits: Long
    var `val`: Long
    do {
        bits = rng.nextLong() shl 1 ushr 1
        `val` = bits % n
    } while (bits - `val` + (n - 1) < 0L)
    return `val`
}

fun randomInt(min: Int, max: Int): Int {
    return min + Random().nextInt(max - min)
}

fun randomInteger(min: Int, max: Int): Int {
    return randomInt(min, max)
}

fun StringIsEmpty(candidate: String?): Boolean {
    if (candidate == null) return true
    if (candidate === "") return true
    return if (candidate.length < 1) true else false
}

fun StringsMatch(x: String?, y: String?): Boolean {
    if (x == null && y == null) return true
    if (x == null && y != null) return false
    return if (x != null && y == null) false else x!!.compareTo(y!!) == 0
}

fun StringsMatchCaseInspecific(x: String?, y: String?): Boolean {
    if (x == null && y == null) return true
    if (x == null && y != null) return false
    return if (x != null && y == null) false else x!!.toUpperCase().compareTo(y!!.toUpperCase()) == 0
}

fun StringArrayContains(candidateArray: Array<String?>,
                        candidateString: String?): Boolean {
    for (cursor in candidateArray.indices) {
        if (StringsMatch(candidateArray[cursor], candidateString)) return true
    }
    return false
}

fun StringArrayContainsCaseInspecific(
        candidateArray: Array<String?>, candidateString: String?): Boolean {
    for (cursor in candidateArray.indices) {
        if (StringsMatchCaseInspecific(candidateArray[cursor],
                        candidateString)) return true
    }
    return false
}

// Ported this from the legacy code.
// TODO: In Kotlin it might make more sense to use File(<complete-path>).deleteRecursively()
fun hardDelete(fullPath: String) {
    val check = File(fullPath)
    if (check.isDirectory) {
        val contents = check.list()
        for (thisFile in contents) {
            hardDelete(fullPath + File.separator + thisFile)
        }
    }
    check.delete()
}

@Throws(IOException::class)
fun copyCompletely(sourcePath: String, destinationPath: String) {
    val check = File(sourcePath)
    if (check.isDirectory) {
        val contents = check.list()
        for (thisFile in contents) {
            copyCompletely(sourcePath + File.separator + thisFile, destinationPath + File.separator + thisFile)
        }
    } else {
        forceParentDirectoryExistence(destinationPath)
        Files.copy(check.toPath(), File(destinationPath).toPath())
    }
}

fun sortMarkupTags(target: String): ArrayList<String>? {
    val result = ArrayList<String>()
    var thisString = StringBuilder()
    var levelsIn = 0
    for (index in 0 until target.length) {
        val thisChar = target[index]
        if (levelsIn > 0) {
            if (thisChar == '<') {
                levelsIn++
            } else if (thisChar == '>') {
                levelsIn--
            }
            thisString.append(thisChar)
            if (levelsIn == 0) {
                if (thisString.length > 0) {
                    result.add(thisString.toString())
                    thisString = StringBuilder()
                }
            }
        } else {
            if (thisChar == '<') {
                if (thisString.length > 0) {
                    result.add(thisString.toString())
                    thisString = StringBuilder()
                }
                levelsIn = 1
            }
            thisString.append(thisChar)
        }
    }
    if (thisString.length > 0) {
        result.add(thisString.toString())
        thisString = StringBuilder()
    }
    return result
}

// Based on http://stackoverflow.com/questions/13592236/parse-a-uri-string-into-name-value-collection
fun getUrlParameters(url: URL): ArrayList<SimpleEntry<String, String>>? {
    val result = ArrayList<SimpleEntry<String, String>>()
    val params = url.query.split("&".toRegex()).toTypedArray()
    for (thisParam in params) {
        val keyValuePair = thisParam.split("=".toRegex()).toTypedArray()
        result.add(SimpleEntry(keyValuePair[0], keyValuePair[1]))
    }
    return result
}

@Throws(IOException::class)
fun getCRC32(filePath: String?): Long {
    val file = FileInputStream(filePath)
    val check = CheckedInputStream(file, CRC32())
    val instream = BufferedInputStream(check)
    while (instream.read() != -1) {
        // Read file in completely
    }
    val result = check.checksum.value
    instream.close()
    return result
}
