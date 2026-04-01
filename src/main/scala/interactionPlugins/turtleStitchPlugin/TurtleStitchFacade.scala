package interactionPlugins.turtleStitchPlugin

import datastructures.core.language.HumanLanguage
import datastructures.web.storage.AsyncDataCache

import scala.concurrent.{ExecutionContext, Future}

@deprecated("Use TurtleStitchEditor directly", "2026-03")
object TurtleStitchFacade {
  
  val programSvgDataSrcStorage: AsyncDataCache[(String, HumanLanguage), String] =
    TurtleStitchEditor.programSvgDataSrcStorage

  val programOutputDataSrcStorage: AsyncDataCache[String, String] =
    TurtleStitchEditor.programOutputDataSrcStorage

  def downloadDst(xml: String)(using ec: ExecutionContext): Future[Unit] =
    TurtleStitchEditor.downloadDst(xml)
}
