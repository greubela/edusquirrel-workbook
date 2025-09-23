package workbook.workbookHtmlElements.helper

import com.raquo.laminar.api.L.*
import workbook.workbookHtmlElements.abstractions.CssSwitchableClass

object HtmlHelper {


  def ensureOneStyleFromListSet(domElement: Element, availableStyles: Seq[CssSwitchableClass], setStyle: CssSwitchableClass): Unit = {
    //println("Called ensureOneStyleFromListSet on" + domElement.ref + ": " + availableStyles.map(_.correspondingClassString) + " -> " + setStyle.correspondingClassString)
    if (domElement != null && availableStyles != null && setStyle != null) {
      availableStyles.foreach(curClass => domElement.ref.classList.remove(curClass.correspondingClassString))
      domElement.ref.classList.add(setStyle.correspondingClassString)
    } else {
      println("[WARNING] HtmlHelper: ensureOneStyleFromListSet: One of the arguments is null.")
    }
  }


}
