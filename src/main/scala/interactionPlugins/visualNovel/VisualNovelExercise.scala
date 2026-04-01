package interactionPlugins.visualNovel

import interactionPlugins.slideshow.{SlideDeckExercise, SlidePanel, SlideView}
import workbook.model.info.AllWorkbookInfo

@deprecated("Renamed to SlideDeckExercise in interactionPlugins.slideshow", "2026-04-01")
type VisualNovelPanelView = SlideView

@deprecated("Renamed to SlideDeckExercise in interactionPlugins.slideshow", "2026-04-01")
object VisualNovelExercise {
  def apply(
             workbookInfo: AllWorkbookInfo,
             id: String,
             titleMapId: String,
             panels: List[SlidePanel]
           ): SlideDeckExercise =
    SlideDeckExercise(workbookInfo, id, titleMapId, panels)
}
