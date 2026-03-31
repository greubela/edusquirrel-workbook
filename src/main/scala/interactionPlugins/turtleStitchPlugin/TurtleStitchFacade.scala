package interactionPlugins.turtleStitchPlugin

import contentmanagement.model.language.HumanLanguage
import contentmanagement.storage.DataStorage

import scala.concurrent.{ExecutionContext, Future}

@deprecated("Use TurtleStitchEditor directly", "2026-03")
object TurtleStitchFacade {

  val programSvgDataSrcStorage: DataStorage[(String, HumanLanguage), String] =
    TurtleStitchEditor.programSvgDataSrcStorage

  val programOutputDataSrcStorage: DataStorage[String, String] =
    TurtleStitchEditor.programOutputDataSrcStorage

  def downloadDst(xml: String)(using ec: ExecutionContext): Future[Unit] =
    TurtleStitchEditor.downloadDst(xml)
}
