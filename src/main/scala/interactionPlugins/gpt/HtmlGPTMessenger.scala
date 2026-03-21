package interactionPlugins.gpt

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.svg
import com.raquo.laminar.api.L.Var
import com.raquo.laminar.nodes.ReactiveSvgElement
import contentmanagement.model.chat
import contentmanagement.model.chat.MessengerModel
import contentmanagement.webElements.genericHtmlElements.editor.SimpleMessengerEditor

import util.Serializer
import workbook.model.abstractions.{HtmlWorkbookElement, WorkbookInteraction}
import workbook.model.info.WorkbookInfo
import workbook.model.interaction.*
import workbook.model.interaction.history.*
import workbook.model.interaction.sync.*
import workbook.htmlElements.basic.*

case class HtmlGPTMessenger(workbookInfoVar: Var[WorkbookInfo], textInteraction: WorkbookInteraction[String]) extends WorkbookInteraction[MessengerModel] {

  override def id: String = textInteraction.id + "_scaffolding"

  override def interactionVariable: InteractionVariable[MessengerModel] = {
    val sampleMessages = MessengerModel.testSample()

    val io: Serializer[MessengerModel] = new Serializer[MessengerModel] {
      override def serialize(obj: MessengerModel): String = obj.toJson

      override def deserialize(str: String): MessengerModel = MessengerModel.fromJson(str)
    }
    InteractionVariable[MessengerModel](this,
      List(InteractionVariableState(sampleMessages, System.currentTimeMillis(), UpdateImportance.DEFAULT)),
      List(SyncInformation(LocalStorageSync, SyncStrategy.SYNC_EVERYTHING)),
      io)
  }

  private val scaffoldingEditor = SimpleMessengerEditor(interactionVariable)

  private val scaffoldingButton = HtmlButtonElement(HtmlGPTMessenger.scaffoldingButtonSvg, event => {
    workbookInfoVar.now().fullscreenElement.setElementFullscreen(scaffoldingEditor.getDomElement())
  })

  private val domElement: L.Element = scaffoldingButton.getDomElement()

  override def getDomElement(): L.Element = domElement

}

object HtmlGPTMessenger {
  
  def scaffoldingButtonSvg = {
    svg.svg(
      svg.cls := "button-show-scaffolder",
      svg.viewBox := "0 0 24 24",
      svg.path(
        svg.cls := "button-fill",
        svg.d := "M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C16.9706 3 21 7.02944 21 12Z"
        //svg.d := "M17 9A5 5 0 0 0 7 9a1 1 0 0 0 2 0 3 3 0 1 1 3 3 1 1 0 0 0-1 1v2a1 1 0 0 0 2 0v-1.1A5 5 0 0 0 17 9z"
      ),
      svg.path(
        svg.d := "M10.5 8.67709C10.8665 8.26188 11.4027 8 12 8C13.1046 8 14 8.89543 14 10C14 10.9337 13.3601 11.718 12.4949 11.9383C12.2273 12.0064 12 12.2239 12 12.5V12.5V13",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round"),
      svg.path(
        svg.d := "M12 16H12.01",
        svg.strokeLineCap := "round",
        svg.strokeLineJoin := "round")
    )
  }

}