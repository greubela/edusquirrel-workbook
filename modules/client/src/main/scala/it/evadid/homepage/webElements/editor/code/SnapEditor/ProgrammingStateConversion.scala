package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.workbook.elements.interactionElements.programming.{SnapCanvasLayout, SnapTurtlePythonBridge}
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExerciseState.{PythonSource, SnapXml}

import scala.util.Try
import scala.util.matching.Regex

/**
 * The only place Snap XML and Python source are converted into each other.
 * Editing inside one editor never goes through here.
 */
object ProgrammingStateConversion {

  /** Line written in front of each Snap script so positions survive a later edit. */
  val ScriptMarkerPrefix = "# @script"

  def snapToPython(state: SnapXml): Either[String, PythonSource] =
    try
      val derived = SnapProgramDerivation.fromXml(state.xml)
      if !derived.pythonCompatible then
        Left(derived.applyBlockedMessage.getOrElse(
          "These blocks cannot convert to Python."
        ))
      else
        Right(PythonSource(renderPython(derived), snapBase = Some(state.xml)))
    catch
      case error: Throwable =>
        val detail = Option(error.getMessage).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)
        Left(s"Could not read the Snap project. ($detail)")

  def pythonToSnap(state: PythonSource): Either[String, SnapXml] =
    unchangedOriginal(state) match
      case Some(original) => Right(original)
      case None =>
        val cleaned = state.copy(source = stripBrokenMarkers(state.source))
        if hasMarker(cleaned.source) then convertMarked(cleaned)
        else convertUnmarked(cleaned)

  private def stripBrokenMarkers(source: String): String =
    source.linesIterator.filterNot { line =>
      AttemptedMarkerLine.matches(line) && !MarkerLine.matches(line)
    }.mkString("\n")

  private def unchangedOriginal(state: PythonSource): Option[SnapXml] =
    state.snapBase.flatMap { base =>
      snapToPython(SnapXml(base)) match
        case Right(generated) if generated.source == state.source => Some(SnapXml(base))
        case _ => None
    }

  private def convertMarked(state: PythonSource): Either[String, SnapXml] = {
    val sections = splitByMarkers(state.source)
    val layout = SnapCanvasLayout.placed(sections.flatMap(scriptsOf))
    val stripped = sections.flatMap(_.lines).mkString("\n")
    SnapTurtlePythonBridge.applyPython(stripped, layout, state.snapBase.getOrElse(""))
  }

  private def convertUnmarked(state: PythonSource): Either[String, SnapXml] = {
    val source = state.source
    val previousXml = state.snapBase.getOrElse("")
    val total = statementCount(source)
    val reused = state.snapBase.flatMap { base =>
      val layout = SnapProgramDerivation.fromXml(base).canvasLayout
      if SnapTurtlePythonBridge.layoutMatches(layout, total) then Some(layout) else None
    }
    val layout = reused.getOrElse {
      SnapCanvasLayout.placed(scriptsOf(Section(None, source.linesIterator.toList)))
    }
    SnapTurtlePythonBridge.applyPython(source, layout, previousXml)
  }

  private def renderPython(derived: SnapProgramDerivation.DerivedView): String = {
    val statements = SnapTurtlePythonBridge.topLevelStatements(derived.program.fullProgram)
    val definitions = statements.collect { case definition: BeDefineFunction => definition }
    val scripts = SnapTurtlePythonBridge.scriptStatements(statements)
    val definitionText = renderChunk(definitions)
    val scriptText = partitionScripts(scripts, derived.canvasLayout).map { case (x, y, chunk) =>
      s"$ScriptMarkerPrefix x=$x y=$y\n${renderChunk(chunk)}"
    }.mkString("\n\n")
    val parts = List(definitionText, scriptText).filter(_.nonEmpty)
    if parts.isEmpty then "" else parts.mkString("\n\n") + "\n"
  }

  private def partitionScripts(
      statements: List[BeExpression],
      layout: SnapCanvasLayout
  ): List[(Int, Int, List[BeExpression])] =
    if statements.isEmpty then Nil
    else if !SnapTurtlePythonBridge.layoutMatches(layout, statements.size) then
      List((SnapCanvasLayout.DefaultX, SnapCanvasLayout.DefaultY, statements))
    else
      var remaining = statements
      layout.scripts.map { script =>
        val (chunk, rest) = remaining.splitAt(script.callCount)
        remaining = rest
        (script.x, script.y, chunk)
      }

  private def renderChunk(statements: List[BeExpression]): String =
    if statements.isEmpty then ""
    else
      SnapTurtlePythonBridge.printedPython(
        BeStartProgram(BeSequence.optionalBody(statements))
      ).trim

  private final case class Section(position: Option[(Int, Int)], lines: List[String])

  /** A marker line, optionally with canvas coordinates. Invalid coordinates do not match. */
  private val MarkerLine: Regex = """^#\s*@script(?:\s+x=(-?\d+)\s+y=(-?\d+))?\s*$""".r

  /** `# @script ...` that is not a valid marker. Dropped so a typo cannot block the switch. */
  private val AttemptedMarkerLine: Regex = """^#\s*@script\b.*$""".r

  /** Top-level hat. Indented calls (inside a loop or function) stay in the current script. */
  private val ReceiveGoLine: Regex = """^receive_go\s*\([^)]*\)\s*(?:#.*)?$""".r

  private def hasMarker(source: String): Boolean =
    source.linesIterator.exists(MarkerLine.matches)

  private def splitByMarkers(source: String): List[Section] = {
    val sections = List.newBuilder[Section]
    val buffer = List.newBuilder[String]
    var position: Option[(Int, Int)] = None
    var started = false
    source.linesIterator.foreach { line =>
      MarkerLine.findFirstMatchIn(line) match
        case Some(matched) =>
          if started then
            sections += Section(position, buffer.result())
            buffer.clear()
          started = true
          position = markerPosition(matched)
        case None =>
          started = true
          buffer += line
    }
    if started then sections += Section(position, buffer.result())
    sections.result()
  }

  private def markerPosition(matched: Regex.Match): Option[(Int, Int)] =
    Option(matched.group(1)).flatMap { x =>
      Option(matched.group(2)).flatMap { y =>
        Try((x.toInt, y.toInt)).toOption
      }
    }

  /** First chunk keeps the section position. A later top-level receive_go starts a new script. */
  private def scriptsOf(section: Section): List[(Option[(Int, Int)], Int, Int)] =
    splitHats(section.lines).zipWithIndex.map { case (lines, index) =>
      val position = if index == 0 then section.position else None
      (position, statementCount(lines.mkString("\n")), lines.count(_.trim.nonEmpty))
    }

  private def splitHats(lines: List[String]): List[List[String]] = {
    val scripts = List.newBuilder[List[String]]
    var current = List.empty[String]
    lines.foreach { line =>
      if current.exists(_.trim.nonEmpty) && ReceiveGoLine.matches(line) then
        scripts += current
        current = List(line)
      else
        current = current :+ line
    }
    if current.exists(_.trim.nonEmpty) then scripts += current
    scripts.result().filter(_.exists(_.trim.nonEmpty))
  }

  private def statementCount(code: String): Int =
    if code.trim.isEmpty then 0
    else
      Try {
        val program = BeProgram.fromPythonString(code)
        SnapTurtlePythonBridge.scriptStatementCount(
          SnapTurtlePythonBridge.topLevelStatements(program.fullProgram)
        )
      }.getOrElse(0)
}
