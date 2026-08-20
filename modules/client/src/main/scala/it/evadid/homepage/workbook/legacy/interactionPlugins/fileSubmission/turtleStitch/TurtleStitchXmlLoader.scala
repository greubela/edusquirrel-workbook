package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch

import TurtleStitchProgramModel.*
import scala.scalajs.js

object TurtleStitchXmlLoader {

  // Upstream reference points in TurtleStitch:
  // - src/store.js: SnapSerializer.loadProjectModel (~335), loadScene (~368),
  //   loadScripts (~1358), loadScript (~1432), loadBlock (~1476), loadInput (~1596).
  // - src/scenes.js: Project/Scene constructors (~65/~114).
  // We intentionally keep tolerant parsing (DOM + string fallback) for workbook robustness.

  private var domUnavailableWarned = false

  def load(xml: String): Project = {
    val fromPrimary = projectFromParseResult(TurtleStitchXmlParser.parse(xml))
    if projectHasBlocks(fromPrimary) || !xml.contains("<block") then fromPrimary
    else
      TurtleStitchXmlParser
        .parseStringOnly(xml)
        .map(TurtleStitchModelMapper.toProject)
        .filter(projectHasBlocks)
        .getOrElse(fromPrimary)
  }

  private def projectFromParseResult(parseResult: TurtleStitchXmlParser.ParseResult): Project = {
    parseResult.fallbackReason.foreach { reason =>
      if (reason.contains("DOM parser is unavailable")) {
        if (!domUnavailableWarned) {
          domUnavailableWarned = true
          warnFallback(reason)
        }
      } else warnFallback(reason)
    }

    parseResult.document match {
      case Some(document) if TurtleStitchXmlValidation.validate(document).isValid =>
        TurtleStitchModelMapper.toProject(document)
      case Some(document) =>
        warnFallback(TurtleStitchXmlValidation.validate(document).errors.mkString("; "))
        TurtleStitchModelMapper.toProject(document)
      case None => TurtleStitchModelMapper.EmptyProject
    }
  }

  private def projectHasBlocks(project: Project): Boolean =
    project.scenes.exists { scene =>
      scene.stage.scripts.exists(_.blocks.nonEmpty) ||
        scene.stage.sprites.exists(_.scripts.exists(_.blocks.nonEmpty))
    }

  private def warnFallback(reason: String): Unit = {
    scala.util.Try {
      val globalConsole = js.Dynamic.global.selectDynamic("console")
      if (!(js.isUndefined(globalConsole) || globalConsole == null)) {
        globalConsole.selectDynamic("warn")(s"[WARN] TurtleStitchXmlLoader fallback: $reason")
      }
    }
    ()
  }
}
