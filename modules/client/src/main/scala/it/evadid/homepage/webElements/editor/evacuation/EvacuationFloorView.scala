package it.evadid.homepage.webElements.editor.evacuation

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.matrix.PositionInMatrix
import it.evadid.evacuation.eva2.model.EvaFloorMap
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.model.evacuation.EvacuationTile

case class EvacuationFloorView(floorMap: Signal[EvaFloorMap], locked: Signal[Boolean], click: PositionInMatrix => Unit)
    extends HtmlAppElement {
  private def textFor(key: String): Signal[String] = laminarHelper.plaintextStringSignal(s"digitalWorkbooks/$key")
  private val planSignal = floorMap.map(EvacuationFloorAdapter.encode)
  // Rebuild only when dimensions change, so painting keeps the focused cell mounted.
  private def grid(dim: it.evadid.core.datastructures.matrix.MatrixDimension): Element =
    div(cls := "evacuation-grid", (0 until dim.rows).map(row => div(cls := "evacuation-row",
      (0 until dim.cols).map(col => {
        val index = row * dim.cols + col
        val tile = planSignal.map(_.tiles.lift(index).getOrElse(EvacuationTile.Floor))
        val occupied = planSignal.map(_.people.contains(index))
        button(typ := "button", cls := "evacuation-cell",
          cls <-- tile.map(kind => s"evacuation-cell--${kind.toString.toLowerCase}"),
          cls.toggle("evacuation-cell--person") <-- occupied, disabled <-- locked,
          aria.label <-- textFor("evacuationCell").combineWith(
            tile.flatMapSwitch(kind => textFor(s"evacuation$kind")),
            occupied.flatMapSwitch(present => if (present) textFor("evacuationPerson") else Val("")))
            .map((template, tileName, personName) =>
              template.replace("{row}", (row + 1).toString).replace("{col}", (col + 1).toString)
                .replace("{tile}", tileName).replace("{person}", personName)),
          onClick --> (_ => click(dim.positions(index))))
      }))))
  override def getDomElement(): Element = div(cls := "evacuation-viewport",
    child <-- floorMap.map(_.floorMatrix.dim).distinct.map(grid))
}
