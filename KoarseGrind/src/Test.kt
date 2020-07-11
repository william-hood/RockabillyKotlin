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

package rockabilly.koarsegrind

import rockabilly.memoir.*
import rockabilly.toolbox.UNSET_STRING
import rockabilly.toolbox.forceParentDirectoryExistence
import rockabilly.toolbox.stdout
import java.io.File
import java.io.PrintWriter
import kotlin.concurrent.thread

enum class TestPriority {
    HAPPY_PATH, CRITICAL, NORMAL, LOW
}

fun String.toTestPriority(): TestPriority {
    if (this.toUpperCase().startsWith("N")) { return TestPriority.NORMAL }
    if (this.toUpperCase().startsWith("L")) { return TestPriority.LOW }
    if (this.toUpperCase().startsWith("C")) { return TestPriority.CRITICAL }
    return TestPriority.HAPPY_PATH
}

internal const val INFO_ICON = "ℹ️"
internal const val IN_PROGRESS_NAME = "(test in progress)"
internal const val SETUP = "setup"
internal const val CLEANUP = "cleanup"
internal const val UNSET_DESCRIPTION = "(no details)"

abstract class Test (name: String, detailedDescription: String = UNSET_DESCRIPTION, testCaseID: String = "", vararg categories: String) {
    // Client code must implement or override
    open fun setup(): Boolean { return true }
    open fun cleanup(): Boolean { return true }
    abstract fun performTest();

    // Class Members
    internal var topLevelMemoir: Memoir? = null
    internal var setupMemoir: Memoir? = null
    internal var cleanupMemoir: Memoir? = null
    private var parentArtifactsDirectory = UNSET_STRING
    var priority = TestPriority.NORMAL
    private var executionThread: Thread? = null
    internal var wasSetup = false
    internal var wasRun = false
    internal var wasCleanedUp = false

    // Assertions
    protected val assert = Enforcer(TestConditionalType.ASSERTION, this)
    protected val require = Enforcer(TestConditionalType.REQUIREMENT, this)
    protected val consider = Enforcer(TestConditionalType.CONSIDERATION, this)

    //Should this be internal???
    val Results = ArrayList<TestResult>()

    // For readability these are now passed into the base constructor
    // and are no longer abstract properties
    internal var identifier = testCaseID
    internal var name = name
    private var detailedDescription = detailedDescription
    private var categories: Array<out String> = categories

    // If there's ever some reason to call it something other than a test, change this.
    open protected val echelonName = "Test"

    private val categorization: String
        get() {
            val result = StringBuilder()
            categories.forEach {
                if (result.length > 0 ) { result.append('/') }
                result.append(it)
            }

            return result.toString()
        }

    val log: Memoir
     get() = // https://stackoverflow.com/questions/4065518/java-how-to-get-the-caller-function-name/46590924
         when (Thread.currentThread().stackTrace[1].methodName) { // slot [2]???
             "Setup" -> this.setupMemoir!!
             "Cleanup" -> this.cleanupMemoir!!
             else -> this.topLevelMemoir!!
         }

    // In C#: [MethodImpl(MethodImplOptions.Synchronized)]
    // Basically this needs to be thread safe.
    internal val progress: Float
    get() {
        // According to https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/synchronized.html
        // "Deprecated: Synchronization on any object is not supported on every platform and will be removed from the common standard library soon."
        synchronized(this) {
            var result: Float = 0.toFloat()
            if (wasSetup) result += 0.33.toFloat()
            if (wasRun) result += 0.34.toFloat()
            if (wasCleanedUp) result += 0.33.toFloat()
            return result
        }
    }

    // For some reason in the C# version this was open/virtual
    fun addResult(thisResult: TestResult) {
        topLevelMemoir!!.showTestResult((thisResult)) // Should be Log instead of topLevelMemoir???
        Results.add(thisResult)
    }

    val overallStatus: TestStatus
        get() {
            if ((Results.size < 1)) return TestStatus.INCONCLUSIVE
            var finalValue = TestStatus.PASS
            Results.forEach {
                finalValue = finalValue + it.status
            }

            return finalValue;
        }

    // Is virtual/open in C#
    val artifactsDirectory: String
        get() = parentArtifactsDirectory + File.separatorChar + IN_PROGRESS_NAME

    // Is virtual/open in C#
    val identifiedName: String
        get() {
            if (identifier.length < 1) return name
            return "$identifier - $name"
        }

    // Is virtual/open in C#
    val prefixedName: String
        get() = "$overallStatus - $identifiedName"

    // Is virtual/open in C#
    val logFileName: String
        get() = "$identifiedName Log.html"

    private fun filterForSummary(it: String): String {
        // C# return Regex.Replace(Regex.Replace(it, "[,\r\f\b]", ""), "[\t\n]", " ");
        return it.replace(Regex("[,\r\b]"), "").replace(Regex("[\t\n]"), "")
    }

    internal val summaryDataRow: ArrayList<String>
        get() {
            val result = ArrayList<String>()
            result.add(filterForSummary(categorization))
            result.add(filterForSummary(priority.toString()))
            result.add(filterForSummary(identifier))
            result.add(filterForSummary(name))
            result.add(filterForSummary(detailedDescription))
            result.add(filterForSummary(overallStatus.toString()))

            val reasoning = StringBuilder()
            Results.forEach {
                if (! it.status.isPassing()) {
                    if (reasoning.length > 0) {
                        reasoning.append("; ")
                    }
                        reasoning.append(it.description)

                        if (it.hasFailures) {
                            it.failures.forEach { thisFailure ->
                                if (reasoning.length > 0) {
                                    reasoning.append("; ")
                                }

                                reasoning.append(thisFailure.javaClass.simpleName)
                            }
                        }
                }
            }

            result.add(filterForSummary(reasoning.toString()))
            return result
        }

    private fun getResultForIncident(status: TestStatus, section: String, failure: Throwable): TestResult {
        var reportedSection = section
        if (section.length > 0) {
            reportedSection = "($section)"
        }

        val result = TestResult(status, "$identifiedName$section: An unanticipated failure occurred.")
        result.failures.add(failure)
        return result
    }


    // Is virtual/open in C#
    fun getResultForFailure(thisFailure: Throwable, section: String = "") = getResultForIncident(TestStatus.FAIL, section, thisFailure)

    // Is virtual/open in C#
    fun getResultForPreclusionInSetup(thisPreclusion: Throwable) = getResultForIncident(TestStatus.INCONCLUSIVE, SETUP, thisPreclusion)

    // Is virtual/open in C#
    fun getResultForPreclusion(thisPreclusion: Throwable) = getResultForIncident(TestStatus.INCONCLUSIVE, "", thisPreclusion)

    // Is virtual/open in C#
    fun reportFailureInCleanup(thisFailure: Throwable, additionalMessage: String = "") {
        var message = StringBuilder()
        if (additionalMessage.length > 0) { message.append(" ") }
        message.append(additionalMessage)

        // This is a direct translation from C#. Spacing looks suspicious... ???
        topLevelMemoir!!.error("$identifiedName$CLEANUP: An unanticipated failure occurred$additionalMessage.") // Should be Log instead of topLevelMemoir???
        topLevelMemoir!!.showThrowable(thisFailure)
    }

    private val indicateSetup: Memoir
        get() = Memoir("Setup - $echelonName $identifiedName", stdout)

    private val indicateCleanup: Memoir
        get() = Memoir("Cleanup - $echelonName $identifiedName", stdout)

    private val indicateBody: Memoir
        get() = Memoir("$echelonName $identifiedName", stdout)

    // C# version used topLevelMemoir. This would not work during Setup() or Cleanup()
    fun waitSeconds(howMany: Long) {
        log.info("Waiting $howMany seconds...", INFO_ICON)
        Thread.sleep(1000 * howMany)
    }

    // C# version used topLevelMemoir. This would not work during Setup() or Cleanup()
    fun waitMilliseconds(howMany: Long) {
        log.info("Waiting $howMany milliseconds...", INFO_ICON)
        Thread.sleep(howMany)
    }

    fun interrupt() {
        try {
            executionThread?.interrupt()
            // C# version was followed by executionThread.Abort() three times in a row.
        } catch (dontCare: Exception) {
            // Deliberate NO-OP
        }
    }

    fun makeSubjective() {
        addResult(TestResult(TestStatus.SUBJECTIVE, "This test case requires analysis by appropriate personnel to determine pass/fail status"))
    }

    // This was virtual/open in the C# version
    fun runTest(rootDirectory: String) {
        if (KILL_SWITCH) {
            // Decline to run
            // Deliberate NO-OP
        } else {
            var setupResult = true
            var cleanupResult = true
            parentArtifactsDirectory = rootDirectory
            val expectedFileName = artifactsDirectory + File.separatorChar + logFileName

            forceParentDirectoryExistence(expectedFileName)
            topLevelMemoir = Memoir(name, stdout, PrintWriter(expectedFileName), ::logHeader)

            if (detailedDescription != UNSET_DESCRIPTION) {
                topLevelMemoir!!.writeToHTML("<small><i>$detailedDescription</i></small>", EMOJI_TEXT_BLANK_LINE)
            }

            topLevelMemoir!!.skipLine()
            val before = SetupEnforcement(this)

            // SETUP
            try {
                setupMemoir = indicateSetup
                try {
                    setupResult = setup()
                } finally {
                    wasSetup = true
                    if (setupMemoir!!.wasUsed) {
                        var style = "decaf_orange_light_roast"
                        if (setupResult) { style = "decaf_green_light_roast" }
                        topLevelMemoir!!.showMemoir(setupMemoir!!, EMOJI_SETUP, style)
                    }
                }
            } catch (thisFailure: Throwable) {
                setupResult = false
                addResult(getResultForPreclusionInSetup(thisFailure))
            } finally {
                if (!SetupEnforcement(this).matches(before)) {
                    setupResult = false
                    addResult(TestResult(TestStatus.INCONCLUSIVE, "PROGRAMMING ERROR: It is illegal to change the identifier, name, or priority in Setup.  This must happen in the constructor. Setup may also not add Test Results."))
                }
            }

            // RUN THE ACTUAL TEST
            if (setupResult && (! KILL_SWITCH)) {
                try {
                    executionThread = thread(start = true) { performTest() }
                    executionThread!!.join()
                } catch (thisFailure: Throwable) {
                    addResult(getResultForFailure(thisFailure))
                } finally {
                    wasRun = true
                    executionThread = null
                }
            } else {
                addResult(TestResult(TestStatus.INCONCLUSIVE, "Declining to perform test case $identifiedName because setup method failed."))
            }

            // CLEANUP
            cleanupMemoir = indicateCleanup
            try {
                cleanupResult = cleanup()
                wasCleanedUp = true
            } catch (thisFailure: Throwable) {
                reportFailureInCleanup(thisFailure)
            } finally {
                if (cleanupMemoir!!.wasUsed) {
                    var style = "decaf_orange_light_roast"
                    if (cleanupResult) { style = "decaf_green_light_roast" }
                    topLevelMemoir!!.showMemoir(cleanupMemoir!!, EMOJI_CLEANUP, style)
                }
            }

            val overall = overallStatus.toString()
            val emoji = overallStatus.memoirIcon
            topLevelMemoir!!.writeToHTML("<h2>Overall Status: $overall</h2>", emoji)
            topLevelMemoir!!.echoPlainText("Overall Status: $overall", emoji)
            topLevelMemoir!!.conclude()
        }
    }
}