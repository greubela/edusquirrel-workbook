package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ui

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.homepage.control.BackendServerConfig
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.BlockFeedbackTestResultFormatter
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ai.CommandLlmClient
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.config.BlockFeedbackExerciseRegistry
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.ml.MlRouter
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.model.{FeedbackTestDisplay, UltrichsNewCoolFeedback}
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.runtime.PythonRuntimeService
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.service.BlockFeedbackService
import it.evadid.homepage.workbook.legacy.model.feedback.FeedbackStatus
import org.scalajs.dom
import todomove.datastructures.core.vm.code.others.BeStartProgram
import todomove.datastructures.core.vm.parsing.python.PythonParser
import todomove.webElementsOld.webElements.genericHtmlElements.editor.CodeMirrorEditor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.timers.{SetIntervalHandle, clearInterval, setInterval}
import scala.util.{Failure, Success}

object FeedbackDemoElement:

  private lazy val demoLlmClient = CommandLlmClient(BackendServerConfig.executor)

  private val defaultLanguage: HumanLanguage = AppLanguage.English

  private def genericSampleFromStatement(exerciseId: String): String =
    val statement =
      BlockFeedbackExerciseRegistry
        .getExercise(exerciseId)
        .flatMap(_.statementTranslations.get(AppLanguage.English))
        .getOrElse("")

    // Try to extract a signature like `foo(x, y)` from the statement.
    val Sig = "`([a-zA-Z_]\\w*)\\(([^`]*)\\)`".r
    statement match
      case Sig(fn, params) =>
        s"""def $fn($params):
           |    # Example starter (intentionally incomplete to see feedback)
           |    pass
           |""".stripMargin
      case _ =>
        """# Select an exercise above.
          |# If you see this, the demo couldn't infer a function signature.
          |""".stripMargin

  private def sampleCodeFor(exerciseId: String): String =
    exerciseId match
      case BlockFeedbackExerciseRegistry.addTwoNumbersExerciseId =>
        """def add(a, b):
          |    # Intentionally wrong example to see feedback:
          |    return a - b
          |""".stripMargin
      case BlockFeedbackExerciseRegistry.maxInListExerciseId =>
        """def max_in_list(xs):
          |    # Intentionally wrong example to see feedback:
          |    m = xs[0]
          |    for x in xs:
          |        if x < m:
          |            m = x
          |    return m
          |""".stripMargin
      case BlockFeedbackExerciseRegistry.balancedBracketsExerciseId =>
        """def balanced_brackets(s):
          |    # Intentionally wrong example to see feedback:
          |    # This only counts parentheses and ignores order/types.
          |    return s.count("(") == s.count(")")
          |""".stripMargin
      case BlockFeedbackExerciseRegistry.twoSumIndicesExerciseId =>
        """def two_sum_indices(nums, target):
          |    # Intentionally wrong example to see feedback:
          |    # This returns values instead of indices (and can reuse the same element).
          |    for a in nums:
          |        for b in nums:
          |            if a + b == target:
          |                return (a, b)
          |    return (-1, -1)
          |""".stripMargin

      case BlockFeedbackExerciseRegistry.palindromeExerciseId =>
        """def is_palindrome(s):
          |    # Intentionally incomplete: ignores case and non-alphanumeric characters.
          |    return s == s[::-1]
          |""".stripMargin

      case BlockFeedbackExerciseRegistry.gcdExerciseId =>
        """def gcd(a, b):
          |    # Intentionally wrong: this does not compute the gcd.
          |    return a - b
          |""".stripMargin

      case BlockFeedbackExerciseRegistry.countVowelsExerciseId =>
        """def count_vowels(s):
          |    # Intentionally wrong: counts only some vowels and ignores uppercase.
          |    c = 0
          |    for ch in s:
          |        if ch in "ae":
          |            c += 1
          |    return c
          |""".stripMargin

      case BlockFeedbackExerciseRegistry.runLengthEncodeExerciseId =>
        """def rle_encode(s):
          |    # Intentionally wrong: just returns the input.
          |    return s
          |""".stripMargin

      case BlockFeedbackExerciseRegistry.mergeSortedExerciseId =>
        """def merge_sorted(a, b):
          |    # Intentionally wrong: concatenates instead of merging.
          |    return a + b
          |""".stripMargin

      case BlockFeedbackExerciseRegistry.uniquePreserveOrderExerciseId =>
        """def unique(xs):
          |    # Intentionally wrong: set() destroys order.
          |    return list(set(xs))
          |""".stripMargin

      case BlockFeedbackExerciseRegistry.romanToIntExerciseId =>
        """def roman_to_int(s):
          |    # Intentionally wrong: ignores subtractive notation (e.g. IV, IX).
          |    values = {'I': 1, 'V': 5, 'X': 10, 'L': 50, 'C': 100, 'D': 500, 'M': 1000}
          |    total = 0
          |    for ch in s:
          |        total += values.get(ch, 0)
          |    return total
          |""".stripMargin

      case BlockFeedbackExerciseRegistry.intToRomanExerciseId =>
        """def int_to_roman(n):
          |    # Intentionally wrong: returns a decimal string.
          |    return str(n)
          |""".stripMargin

      case BlockFeedbackExerciseRegistry.normalizeWhitespaceExerciseId =>
        """def normalize_whitespace(s):
          |    # Intentionally incomplete: only strips outer whitespace.
          |    return s.strip()
          |""".stripMargin

      case BlockFeedbackExerciseRegistry.rotateListExerciseId =>
        """def rotate_list(xs, k):
          |    # Intentionally wrong: returns the list unchanged.
          |    return xs
          |""".stripMargin

      case BlockFeedbackExerciseRegistry.fizzBuzzScriptExerciseId =>
        """# Intentionally wrong: prints instead of collecting in a list,
          |# and the "FizzBuzz" case is missing entirely.
          |for n in range(1, 21):
          |    if n % 3 == 0:
          |        print("Fizz")
          |    elif n % 5 == 0:
          |        print("Buzz")
          |    else:
          |        print(n)
          |""".stripMargin

      case BlockFeedbackExerciseRegistry.evenSquaresScriptExerciseId =>
        """# Intentionally wrong: squares ALL numbers instead of only the even ones.
          |zahlen = list(range(1, 11))
          |ergebnisse = []
          |for n in zahlen:
          |    ergebnisse.append(n ** 2)
          |""".stripMargin

      case BlockFeedbackExerciseRegistry.fibonacciScriptExerciseId =>
        """# Intentionally wrong: adds the loop index i instead of the previous
          |# two Fibonacci numbers.
          |fibonacci = [1, 1]
          |for i in range(2, 10):
          |    fibonacci.append(fibonacci[-1] + i)
          |""".stripMargin

      case BlockFeedbackExerciseRegistry.primesScriptExerciseId =>
        """# Intentionally wrong: includes 1 because range(2, 1) is empty,
          |# so is_composite stays False and 1 passes the check.
          |primzahlen = []
          |for n in range(1, 51):
          |    is_composite = False
          |    for i in range(2, n):
          |        if n % i == 0:
          |            is_composite = True
          |    if not is_composite:
          |        primzahlen.append(n)
          |""".stripMargin

      case BlockFeedbackExerciseRegistry.wordCountScriptExerciseId =>
        """# Intentionally wrong: always sets count to 1 instead of incrementing,
          |# so repeated words are not counted correctly.
          |text = "die Katze saß auf der Matte die Katze saß"
          |wortanzahl = {}
          |for wort in text.split():
          |    wortanzahl[wort] = 1
          |""".stripMargin

      case _ => genericSampleFromStatement(exerciseId)

  private val pythonKeywords: Set[String] = Set(
    "False", "None", "True", "and", "as", "assert", "async", "await",
    "break", "class", "continue", "def", "del", "elif", "else", "except",
    "finally", "for", "from", "global", "if", "import", "in", "is",
    "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try",
    "while", "with", "yield", "self", "cls"
  )

  /** Extracts identifiers from Python source and classifies them.
   *  - Function name after `def`: "fd-token-def"  (yellowish, like cm-def)
   *  - Parameters, assigned vars, for-targets: "fd-token-var"  (light blue, like cm-variableName)
   *  Only non-keyword, multi-char names are included to avoid noise.
   */
  private def extractCodeSymbols(python: String): Map[String, String] =
    if python == null || python.trim.isEmpty then Map.empty
    else
      val result = scala.collection.mutable.LinkedHashMap.empty[String, String]
      val defRe = """def\s+([A-Za-z_]\w+)""".r
      for m <- defRe.findAllMatchIn(python) do
        val n = m.group(1)
        if !pythonKeywords.contains(n) then result(n) = "fd-token-def"
      val paramRe = """def\s+[A-Za-z_]\w*\s*\(([^)]*)\)""".r
      for m <- paramRe.findAllMatchIn(python) do
        m.group(1).split(",").foreach { raw =>
          val name = raw.trim.replaceAll("=.*", "").replaceAll(":.*", "").trim
          if name.matches("[A-Za-z_]\\w+") && !pythonKeywords.contains(name) && !result.contains(name) then
            result(name) = "fd-token-var"
        }
      val assignRe = """^\s*([A-Za-z_]\w+)\s*=[^=]""".r
      python.split("\n").foreach { line =>
        assignRe.findFirstMatchIn(line).foreach { m =>
          val n = m.group(1)
          if !pythonKeywords.contains(n) && n.length > 1 && !result.contains(n) then
            result(n) = "fd-token-var"
        }
      }
      // For-loop variables: for x in ...
      val forRe = """for\s+([A-Za-z_]\w+)\s+in""".r
      for m <- forRe.findAllMatchIn(python) do
        val n = m.group(1)
        if !pythonKeywords.contains(n) && !result.contains(n) then
          result(n) = "fd-token-var"
      result.toMap

  /** Splits `text` into a sequence of spans. Tokens matching a code symbol get
   *  a highlight class; the rest are plain unstyled spans. */
  private def highlightText(text: String, symbols: Map[String, String]): Seq[HtmlElement] =
    if symbols.isEmpty then Seq(span(text))
    else
      // Sort longest-first so "rotate_list" matches before "rotate"
      val sorted = symbols.keys.toSeq.sortBy(-_.length)
      val pattern = sorted.map(k => "\\b" + scala.util.matching.Regex.quote(k) + "\\b").mkString("|")
      val regex   = pattern.r
      val result  = scala.collection.mutable.ArrayBuffer.empty[HtmlElement]
      var last    = 0
      for m <- regex.findAllMatchIn(text) do
        if m.start > last then result += span(text.substring(last, m.start))
        result += span(cls := symbols.getOrElse(m.matched, "fd-token-var"), m.matched)
        last = m.end
      if last < text.length then result += span(text.substring(last))
      if result.isEmpty then Seq(span(text)) else result.toSeq

  def element(): HtmlElement =
    // Start loading Pyodide in the background immediately so it is ready
    // by the time the user submits their first code (execution timeout is ~4s).
    PythonRuntimeService.warmup()

    val exercises = BlockFeedbackExerciseRegistry.listExercises
    val defaultExerciseId = exercises.headOption.map(_.id).getOrElse("")

    val selectedExerciseIdVar = Var(defaultExerciseId)
    val pythonCodeVar = Var(sampleCodeFor(defaultExerciseId))
    val selectedLanguageVar = Var(defaultLanguage)
    val showDebugVar = Var(false)
    val showEventLogVar = Var(true)
    val showTestsVar = Var(true)
    val showFeedbackVar = Var(true)
    val showEditorVar = Var(true)

    def tx(lang: HumanLanguage, en: String, de: String): String =
      if lang == AppLanguage.German then de else en

    def tSig(en: String, de: String): Signal[String] =
      selectedLanguageVar.signal.map(lang => tx(lang, en, de))

    def toggleL(labelSig: Signal[String], flag: Var[Boolean]): HtmlElement =
      label(
        cls := "fd-toggle",
        input(typ := "checkbox", checked <-- flag.signal, onInput.mapToChecked --> flag.set),
        span(child.text <-- labelSig)
      )

    val isRunningVar = Var(false)
    val errorVar = Var(Option.empty[String])
    val feedbackVar = Var(Option.empty[UltrichsNewCoolFeedback])
    val eventLogVar = Var(Vector.empty[String])

    val typedTextVar  = Var("")
    val typingDoneVar = Var(true)
    var typingHandle: Option[SetIntervalHandle] = None

    def startTyping(fullText: String): Unit =
      typingHandle.foreach(clearInterval)
      typingHandle = None
      typedTextVar.set("")
      typingDoneVar.set(false)
      var idx = 0
      typingHandle = Some(setInterval(32.0) {
        if idx < fullText.length then
          typedTextVar.update(_ + fullText.charAt(idx).toString)
          idx += 1
        else
          typingHandle.foreach(clearInterval)
          typingHandle = None
          typingDoneVar.set(true)
      })

    def nowLabel: String =
      try new js.Date().toLocaleTimeString()
      catch case _: Throwable => "time"

    def logEvent(message: String): Unit =
      val line = s"[$nowLabel] $message"
      eventLogVar.update(lines => (lines :+ line).takeRight(80))

    // Session persistence (sessionStorage)
    val SS_KEY = "fdSession"

    def saveSession(): Unit =
      val fbJson: js.Any = feedbackVar.now() match
        case None => null.asInstanceOf[js.Any]
        case Some(fb) =>
          js.Dynamic.literal(
            summary        = fb.summary,
            displayHints   = js.Array(fb.displayHints*),
            displayTests   = js.Array(fb.displayTests.map { t =>
              val ea: js.Any = t.expectedActual.fold(null.asInstanceOf[js.Any]) { ea =>
                js.Dynamic.literal(
                  expectedLabel = ea.expectedLabel,
                  expected      = ea.expected,
                  actualLabel   = ea.actualLabel,
                  actual        = ea.actual
                )
              }
              js.Dynamic.literal(name = t.name, passed = t.passed, message = t.message, ea = ea)
            }*),
            allTestsPassed = fb.allTestsPassed,
            rawPython      = fb.rawPython,
            status         = fb.status.toString,
            normalizedScore = fb.normalizedScore
          )
      val state = JSON.stringify(js.Dynamic.literal(
        exerciseId   = selectedExerciseIdVar.now(),
        language     = selectedLanguageVar.now().toString,
        code         = pythonCodeVar.now(),
        eventLog     = js.Array(eventLogVar.now()*),
        showFeedback = showFeedbackVar.now(),
        showTests    = showTestsVar.now(),
        showEventLog = showEventLogVar.now(),
        showDebug    = showDebugVar.now(),
        error        = errorVar.now().orNull,
        feedback     = fbJson
      ))
      dom.window.sessionStorage.setItem(SS_KEY, state)

    def loadSession(): Unit =
      val raw = dom.window.sessionStorage.getItem(SS_KEY)
      if raw != null then
        try
          val s = JSON.parse(raw).asInstanceOf[js.Dynamic]
          val exId = s.exerciseId.asInstanceOf[String]
          if exId != null && exId.nonEmpty then selectedExerciseIdVar.set(exId)
          val savedCode = s.code.asInstanceOf[String]
          if savedCode != null then pythonCodeVar.set(savedCode)
          val lang = s.language.asInstanceOf[String]
          if lang == "German" then selectedLanguageVar.set(AppLanguage.German)
          val boolOr = (field: js.Dynamic, default: Boolean) =>
            if js.isUndefined(field) then default else field.asInstanceOf[Boolean]
          showFeedbackVar.set(boolOr(s.showFeedback, showFeedbackVar.now()))
          showTestsVar.set(boolOr(s.showTests, showTestsVar.now()))
          showEventLogVar.set(boolOr(s.showEventLog, showEventLogVar.now()))
          showDebugVar.set(boolOr(s.showDebug, showDebugVar.now()))
          val logArr = s.eventLog.asInstanceOf[js.Array[String]]
          if !js.isUndefined(logArr.asInstanceOf[js.Any]) && logArr != null then
            eventLogVar.set(logArr.toSeq.toVector)
          val err = s.error
          if !js.isUndefined(err) && err != null then
            errorVar.set(Some(err.asInstanceOf[String]))
          val fbRaw = s.feedback
          if !js.isUndefined(fbRaw) && fbRaw != null then
            val fbD = fbRaw.asInstanceOf[js.Dynamic]
            val displayHints = fbD.displayHints.asInstanceOf[js.Array[String]].toSeq
            val displayTests = fbD.displayTests.asInstanceOf[js.Array[js.Dynamic]].toSeq.map { td =>
              val eaRaw = td.ea
              val ea: Option[BlockFeedbackTestResultFormatter.ExpectedActual] =
                if js.isUndefined(eaRaw) || eaRaw == null then None
                else
                  val eaD = eaRaw.asInstanceOf[js.Dynamic]
                  Some(BlockFeedbackTestResultFormatter.ExpectedActual(
                    expectedLabel = eaD.expectedLabel.asInstanceOf[String],
                    expected      = eaD.expected.asInstanceOf[String],
                    actualLabel   = eaD.actualLabel.asInstanceOf[String],
                    actual        = eaD.actual.asInstanceOf[String]
                  ))
              FeedbackTestDisplay(
                name          = td.name.asInstanceOf[String],
                passed        = td.passed.asInstanceOf[Boolean],
                message       = td.message.asInstanceOf[String],
                expectedActual = ea
              )
            }
            val statusStr = fbD.status.asInstanceOf[String]
            val status = FeedbackStatus.values.find(_.toString == statusStr).getOrElse(FeedbackStatus.FINISHED)
            feedbackVar.set(Some(UltrichsNewCoolFeedback(
              summary        = fbD.summary.asInstanceOf[String],
              tests          = Seq.empty,
              generalHints   = Seq.empty,
              displayHints   = displayHints,
              displayTests   = displayTests,
              allTestsPassed = fbD.allTestsPassed.asInstanceOf[Boolean],
              rawPython      = fbD.rawPython.asInstanceOf[String],
              status         = status,
              normalizedScore = fbD.normalizedScore.asInstanceOf[Double]
            )))
            typingDoneVar.set(true)
        catch case _: Throwable => () // corrupt session, ignore

    loadSession()

    def hasCodeMirrorFacade: Boolean =
      try {
        val v = js.Dynamic.global.selectDynamic("EduSquirrelCodeMirror")
        !js.isUndefined(v) && v != null
      } catch {
        case _: Throwable => false
      }

    def statementFor(exerciseId: String, lang: HumanLanguage): String =
      exercises
        .find(_.id == exerciseId)
        .flatMap(_.statementTranslations.get(lang))
        .getOrElse("")

    def renderInlineCode(text: String): Seq[Node] =
      val safe = Option(text).getOrElse("")
      val parts = safe.split("`", -1).toSeq
      parts.zipWithIndex.map { case (part, idx) =>
        if idx % 2 == 1 then span(cls := "fd-inline-code", part)
        else span(part)
      }

    def feedbackGlowClass(feedback: Option[UltrichsNewCoolFeedback], error: Option[String]): String =
      val base = "fd-card fd-feedback-card"
      if error.exists(_.trim.nonEmpty) then s"$base fd-feedback-card--active fd-feedback-card--bad"
      else
        feedback match
          case Some(fb) =>
            val hasTests = fb.displayTests.nonEmpty
            val allPassed = hasTests && fb.allTestsPassed
            val anyFailed = fb.displayTests.exists(!_.passed)
            val tone =
              if allPassed then "fd-feedback-card--good"
              else if anyFailed then "fd-feedback-card--bad"
              else "fd-feedback-card--warn"
            s"$base fd-feedback-card--active $tone"
          case None => base

    def runFeedback(): Unit =
      errorVar.set(None)
      feedbackVar.set(None)
      isRunningVar.set(true)
      logEvent("Run feedback started")

      val programExpr =
        try
          val parsed = PythonParser.parsePython(pythonCodeVar.now())
          BeStartProgram(parsed)
        catch
          case t: Throwable =>
            isRunningVar.set(false)
            errorVar.set(Option(t.getMessage).filter(_.nonEmpty).orElse(Some(t.toString)))
            logEvent("Parse failed: " + Option(t.getMessage).getOrElse(t.toString))
            return

      val req =
        BlockFeedbackService
          .requestForExerciseId(
            exerciseId = selectedExerciseIdVar.now(),
            studentProgram = programExpr,
            submissionNr = 1,
            humanLanguage = selectedLanguageVar.now()
          )
          .copy(pythonSourceOverride = Some(pythonCodeVar.now()))

      BlockFeedbackService
        .generateFeedback(req, demoLlmClient)
        .onComplete {
          case Success(feedback) =>
            isRunningVar.set(false)
            feedbackVar.set(Some(feedback))
            logEvent("Feedback generated")
            saveSession()
            val primary = feedbackMessage(feedback).trim
            val hints   = feedbackHints(feedback).map(_.trim).filter(_.nonEmpty).distinct
            val items   =
              if hints.isEmpty then Seq(primary)
              else if hints.contains(primary) then hints
              else primary +: hints
            startTyping(items.mkString("\n\n"))
          case Failure(ex) =>
            isRunningVar.set(false)
            errorVar.set(Option(ex.getMessage).filter(_.nonEmpty).orElse(Some(ex.toString)))
            logEvent("Feedback failed: " + Option(ex.getMessage).getOrElse(ex.toString))
            saveSession()
        }

    def feedbackMessage(feedback: UltrichsNewCoolFeedback): String =
      val hint = feedback.displayHints.headOption.filter(_.trim.nonEmpty)
      val firstFail = feedback.displayTests.find(!_.passed)
      val msg = firstFail.map(_.message).filter(_.trim.nonEmpty)
      val expectedActualDetails =
        firstFail
          .flatMap(_.expectedActual)
          .map(info => s"${info.expectedLabel}: ${info.expected}\n${info.actualLabel}: ${info.actual}")
      hint.orElse(msg).orElse(expectedActualDetails).getOrElse(feedback.summary)

    def feedbackHints(feedback: UltrichsNewCoolFeedback): Seq[String] =
      val hints = feedback.displayHints.map(_.trim).filter(_.nonEmpty)
      if hints.nonEmpty then hints else Seq(feedback.summary)

    def renderFeedbackBlock(rawText: String, showCursor: Boolean = false, codeSymbols: Map[String, String] = Map.empty): HtmlElement =
      val normalized = Option(rawText).getOrElse("").replace("\r\n", "\n").trim
      val cursorNode: HtmlElement = span(cls := "fd-cursor")
      if normalized.isEmpty then
        if showCursor then p(cursorNode) else div(cls := "fd-empty", "No feedback")
      else
        val lines = normalized.split("\n", -1).toSeq.map(_.trim).filter(_.nonEmpty)
        def isStepLine(line: String): Boolean =
          line.matches("^\\d+\\.\\s+.+") || line.startsWith("-")

        def stripStep(line: String): String =
          line.replaceFirst("^\\d+\\.\\s+", "").stripPrefix("-").trim

        val hasSteps = lines.exists(isStepLine)
        if hasSteps then
          val introLines = lines.takeWhile(l => !isStepLine(l))
          val intro = introLines.mkString(" ").trim
          val steps = lines.filter(isStepLine).map(stripStep)
          val lastIdx = steps.length - 1
          div(
            if intro.nonEmpty then p(highlightText(intro, codeSymbols)*) else emptyNode,
            ol(steps.zipWithIndex.map { case (step, i) =>
              val segs = highlightText(step, codeSymbols)
              if showCursor && i == lastIdx then li((segs :+ cursorNode)*)
              else li(segs*)
            })
          )
        else
          val segs = highlightText(normalized, codeSymbols)
          if showCursor then p((segs :+ cursorNode)*)
          else p(segs*)

    def statusLabel: String =
      if isRunningVar.now() then "Evaluating\u2026" else "ready"

    def statusClass: String =
      if isRunningVar.now() then "fd-status fd-status--running" else "fd-status fd-status--done"

    def toggle(labelText: String, flag: Var[Boolean]): HtmlElement =
      label(
        cls := "fd-toggle",
        input(
          typ := "checkbox",
          checked <-- flag.signal,
          onInput.mapToChecked --> flag.set
        ),
        span(labelText)
      )

    def renderTestItems(tests: Seq[FeedbackTestDisplay], kind: String): HtmlElement =
      if tests.isEmpty then div(cls := "fd-empty", "No tests")
      else
        ul(
          cls := "fd-list",
          tests.map { t =>
            val clsName = if kind == "pass" then "fd-list-item fd-list-item--pass" else "fd-list-item fd-list-item--fail"
            li(
              cls := clsName,
              div(strong(t.name)),
              Option(t.message).filter(_.trim.nonEmpty).map(msg => div(msg)).getOrElse(div("No message")),
              t.expectedActual
                .map(info =>
                  div(
                    s"${info.expectedLabel}: ${info.expected}",
                    br(),
                    s"${info.actualLabel}: ${info.actual}"
                  )
                )
                .getOrElse(emptyNode)
            )
          }
        )

    def debugMessage(feedback: UltrichsNewCoolFeedback, exerciseId: String): String =
      val visibleTestByName: Map[String, String] =
        BlockFeedbackExerciseRegistry
          .byExerciseId
          .get(exerciseId)
          .map { ex =>
            (ex.config.visibleTests ++ ex.config.hiddenTests)
              .map(t => t.name -> t.code)
              .toMap
          }
          .getOrElse(Map.empty)

      val testsBlock =
        if feedback.tests.isEmpty then "tests: <none>"
        else
          val lines = feedback.tests.map { t =>
            val status = if t.passed then "PASS" else "FAIL"
            val msg = t.message.filter(_.trim.nonEmpty).map(m => s" message=${m.trim}").getOrElse("")
            val code = visibleTestByName.getOrElse(t.name, "<hidden>")
            s"[$status] ${t.name} code=${code} expected=${t.expected} actual=${t.actual}$msg"
          }
          ("tests:" +: lines).mkString("\n")

      feedback.debug match
        case None => "(no debug info)"
        case Some(d) =>
          Seq(
            s"llmEligible=${d.llmEligible}",
            s"llmProxyAttempted=${d.llmProxyAttempted}",
            s"aiHintAdded=${d.aiHintAdded}",
            s"llmRewriteCount=${d.llmRewriteCount}" +
              (if d.llmLastGateReasons.nonEmpty then s" (lastIssues: ${d.llmLastGateReasons.mkString(", ")})"
               else if d.llmRewriteCount > 0 then " (passed after rewrite)"
               else ""),
            s"functionNameMismatch=${if d.functionNameMismatch.isEmpty then "none" else d.functionNameMismatch.mkString(", ")}",
            s"planHintsCount=${d.planHintsCount}",
            s"ruleHintsCount=${d.ruleHintsCount}",
            s"runtimeHintsCount=${d.runtimeHintsCount}",
            s"mlModelLoaded=${MlRouter.isReady}",
            s"testsTotal=${d.testsTotal}",
            s"testsFailed=${d.testsFailed}",
            s"hasRuntimeError=${d.hasRuntimeError}",
            s"hasEmptySource=${d.hasEmptySource}",
            d.rawRuntimeError.map(e => s"rawRuntimeError=${e.take(200).replace("\n", " ↵ ")}").getOrElse("rawRuntimeError=none"),
            s"primaryIssue=${d.primaryIssue}",
            s"templateId=${d.templateId.getOrElse("")}",
            testsBlock
          ).mkString("\n")

    div(
      cls := "fd-page",
      idAttr := "feedback-demo",
      div(
        cls := "fd-hero",
        h2(cls := "fd-hero-title", "Feedback Demo"),
        p(cls := "fd-hero-subtitle", child.text <-- tSig(
          "Select an exercise, edit the code, and run feedback to review results.",
          "W\u00e4hle eine Aufgabe, bearbeite den Code und f\u00fchre das Feedback aus."
        ))
      ),
      div(
        cls := "fd-grid",
        div(
          cls := "fd-col fd-col--left",
          div(
            cls := "fd-card",
            div(
              cls := "fd-card-header",
              h3(cls := "fd-card-title", child.text <-- tSig("Session", "Sitzung")),
              span(cls := statusClass, child.text <-- isRunningVar.signal
                .combineWith(selectedLanguageVar.signal)
                .map((r, lang) => if r then tx(lang, "Evaluating\u2026", "Bewertung l\u00e4uft\u2026") else tx(lang, "ready", "bereit")))
            ),
            div(
              cls := "fd-field",
              label(child.text <-- tSig("Exercise", "Aufgabe")),
              select(
                value <-- selectedExerciseIdVar.signal,
                onChange.mapToValue --> selectedExerciseIdVar.set,
                exercises.map { ex =>
                  option(
                    value := ex.id,
                    child.text <-- selectedLanguageVar.signal.map { lang =>
                      ex.titleTranslations.getOrElse(lang, ex.id)
                    }
                  )
                }
              )
            ),
            div(
              cls := "fd-field",
              label(child.text <-- tSig("Language", "Sprache")),
              select(
                value <-- selectedLanguageVar.signal.map(_.toString),
                onChange.mapToValue.map {
                  case s if s == AppLanguage.German.toString => AppLanguage.German
                  case _ => AppLanguage.English
                } --> selectedLanguageVar.set,
                option(value := AppLanguage.English.toString, "English"),
                option(value := AppLanguage.German.toString, "Deutsch")
              )
            ),
            div(
              cls := "fd-field",
              label(child.text <-- tSig("Statement", "Aufgabenstellung")),
              div(
                cls := "fd-list-item fd-statement",
                children <-- selectedExerciseIdVar.signal
                  .combineWith(selectedLanguageVar.signal)
                  .map { (id, lang) => renderInlineCode(statementFor(id, lang)) }
              )
            ),
            div(
              button(
                cls := "fd-button",
                disabled <-- isRunningVar.signal,
                onClick --> (_ => runFeedback()),
                children <-- isRunningVar.signal.combineWith(selectedLanguageVar.signal).map { (r, lang) =>
                  if r then
                    Seq(
                      span(cls := "fd-flask"),
                      span(cls := "fd-button-text", tx(lang, "Running", "L\u00e4uft..."))
                    )
                  else
                    Seq(span(cls := "fd-button-text", tx(lang, "Run feedback", "Feedback ausf\u00fchren")))
                }
              )
            ),
            div(
              cls := "fd-field",
              label(child.text <-- tSig("Panels", "Bereiche")),
              toggleL(tSig("Show feedback", "Feedback anzeigen"), showFeedbackVar),
              toggleL(tSig("Show tests", "Tests anzeigen"), showTestsVar),
              toggleL(tSig("Show event log", "Ereignisprotokoll"), showEventLogVar),
              toggleL(tSig("Show debug", "Debug anzeigen"), showDebugVar)
            )
          ),
          child.maybe <-- showEventLogVar.signal.map { show =>
            if show then
              Some(
                div(
                  cls := "fd-card",
                  div(
                    cls := "fd-card-header",
                  h3(cls := "fd-card-title", child.text <-- tSig("Event log", "Ereignisprotokoll"))
                ),
                child <-- eventLogVar.signal.combineWith(selectedLanguageVar.signal).map { (lines, lang) =>
                  if lines.isEmpty then div(cls := "fd-empty", tx(lang, "No events yet", "Noch keine Ereignisse"))
                    else div(cls := "fd-event-log", lines.map(line => span(line)))
                  }
                )
              )
            else None
          }
        ),
        div(
          cls := "fd-col fd-col--right",
          child.maybe <-- showEditorVar.signal.map { show =>
            if show then
              Some(
                div(
                  cls := "fd-card",
                  div(
                    cls := "fd-card-header",
                    h3(cls := "fd-card-title", child.text <-- tSig("Python editor", "Python Editor"))
                  ),
                  div(
                    cls := "fd-editor",
                    CodeMirrorEditor(pythonCodeVar).getDomElement()
                  )
                )
              )
            else None
          },
          child.maybe <-- showFeedbackVar.signal.map { show =>
            if show then
              Some(
                div(
                  className <-- feedbackVar.signal
                    .combineWith(errorVar.signal)
                    .map { (fb, err) => feedbackGlowClass(fb, err) },
                  div(
                    cls := "fd-card-header",
                    h3(
                      cls := "fd-card-title",
                      child.text <-- tSig("Feedback", "Feedback"),
                      child.maybe <-- feedbackVar.signal.combineWith(selectedLanguageVar.signal).map { (fb, lang) =>
                        val usedLlm = fb.flatMap(_.debug).exists(_.aiHintAdded)
                        if usedLlm then
                          val label = if lang == AppLanguage.German then "KI" else "AI"
                          Some(span(cls := "fd-ai-badge",
                            span(cls := "fd-ai-badge__star fd-ai-badge__star--left", "✦"),
                            span(cls := "fd-ai-badge__label", label),
                            span(cls := "fd-ai-badge__star fd-ai-badge__star--right", "✦")
                          ))
                        else None
                      }
                    )
                  ),
                  child.maybe <-- errorVar.signal.map {
                    case Some(err) => Some(div(cls := "fd-list-item fd-list-item--fail", s"Error: $err"))
                    case None => None
                  },
                  child.maybe <-- feedbackVar.signal.combineWith(typingDoneVar.signal).map {
                    case (Some(fb), done) =>
                      val primary = feedbackMessage(fb).trim
                      val hints = feedbackHints(fb).map(_.trim).filter(_.nonEmpty).distinct
                      val items =
                        if hints.isEmpty then Seq(primary)
                        else if hints.contains(primary) then hints
                        else primary +: hints
                      val symbols = extractCodeSymbols(pythonCodeVar.now())
                      Some(
                        if !done then
                          div(
                            cls := "fd-feedback",
                            child <-- typedTextVar.signal.map(t => renderFeedbackBlock(t, showCursor = true, symbols))
                          )
                        else
                          div(
                            cls := "fd-feedback",
                            items.map(s => renderFeedbackBlock(s, codeSymbols = symbols))
                          )
                      )
                    case (None, _) =>
                      Some(div(cls := "fd-empty", child.text <-- tSig("Run feedback to see results", "Feedback ausf\u00fchren um Ergebnisse zu sehen")))
                  }
                )
              )
            else None
          },
          child.maybe <-- showTestsVar.signal.map { show =>
            if show then
              Some(
                div(
                  cls := "fd-card",
                  div(
                    cls := "fd-card-header",
                    h3(cls := "fd-card-title", child.text <-- tSig("Tests", "Tests"))
                  ),
                  child.maybe <-- feedbackVar.signal.map {
                    case Some(fb) =>
                      val passed = fb.displayTests.filter(_.passed)
                      val failed = fb.displayTests.filterNot(_.passed)
                      Some(
                        div(
                          div(cls := "fd-field", label(child.text <-- tSig("Passed", "Bestanden")), renderTestItems(passed, "pass")),
                          div(cls := "fd-field", label(child.text <-- tSig("Failed", "Fehlgeschlagen")), renderTestItems(failed, "fail"))
                        )
                      )
                    case None => Some(div(cls := "fd-empty", child.text <-- tSig("Run feedback to see test results", "Feedback ausf\u00fchren um Testergebnisse zu sehen")))
                  }
                )
              )
            else None
          },
          child.maybe <-- showDebugVar.signal.map { show =>
            if show then
              Some(
                div(
                  cls := "fd-card",
                  div(
                    cls := "fd-card-header",
                    h3(cls := "fd-card-title", child.text <-- tSig("Debug", "Debug"))
                  ),
                  child.maybe <-- feedbackVar.signal.map {
                    case Some(fb) => Some(pre(debugMessage(fb, selectedExerciseIdVar.now())))
                    case None => Some(div(cls := "fd-empty", child.text <-- tSig("No debug info", "Keine Debug-Informationen")))
                  }
                )
              )
            else None
          }
        )
      ),
      selectedLanguageVar.signal --> { lang =>
        Option(dom.document.getElementById("fdBackBtn")).foreach { el =>
          el.textContent = "\u2190 " + tx(lang, "Back", "Zur\u00fcck")
        }
      },
      // Persist session on every state change
      selectedExerciseIdVar.signal --> { _ => saveSession() },
      selectedLanguageVar.signal   --> { _ => saveSession() },
      pythonCodeVar.signal         --> { _ => saveSession() },
      eventLogVar.signal           --> { _ => saveSession() },
      feedbackVar.signal           --> { _ => saveSession() },
      errorVar.signal              --> { _ => saveSession() },
      showFeedbackVar.signal       --> { _ => saveSession() },
      showTestsVar.signal          --> { _ => saveSession() },
      showEventLogVar.signal       --> { _ => saveSession() },
      showDebugVar.signal          --> { _ => saveSession() },
      selectedExerciseIdVar.signal.changes --> { id =>
        pythonCodeVar.set(sampleCodeFor(id))
        errorVar.set(None)
        feedbackVar.set(None)
        logEvent("Exercise changed")
        saveSession()
      },
      onMountCallback { _ =>
        // Force-save on every navigation away from this page
        dom.window.addEventListener("pagehide", (_: dom.Event) => saveSession())
      }    )