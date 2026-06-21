package it.evadid.evacuation.eva1.control.modes

import it.evadid.evacuation.core.graphic.model.{EvaFileInformation, EvaImage}
import it.evadid.evacuation.eva1.control.{BasicPaneControlMode, Eva1Control}
import it.evadid.evacuation.eva1.model.ProgramState
import it.evadid.evacuation.html.EvaHtmlFactory
import it.evadid.evacuation.html.elements.ObservableInputElement.HtmlInputChangeListener
import org.scalajs.dom.{Element, document}

class ConfigurationMode extends BasicPaneControlMode {


  override def onEnteringMode(): Unit = {
  }

  override def onLeavingMode(): Unit = {
  }

  def handleFileSelected(info: EvaFileInformation): Unit = {
    ProgramState.instance.backgroundImage.setValue(Some(EvaImage.fromData(info)))
  }


  private def createSizeControlElement: Element = {
    val root = document.createElement("div")

    val onChange: List[HtmlInputChangeListener[Int]] = List(_ => {
      val newMode = new ConfigurationMode()
      Eva1Control.setNewControlMode(newMode)
    })

    root.appendChild(EvaHtmlFactory.createNumberForm(ProgramState.instance.graphicConfig.widthDimension, Some(100), Some(100), Some(4000), onChange))
    root.appendChild(EvaHtmlFactory.createNumberForm(ProgramState.instance.graphicConfig.heightDimension, Some(100), Some(100), Some(4000), onChange))

    root
  }

  private def createImageControlElement: Element = {
    val root = document.createElement("div")
    root.appendChild(EvaHtmlFactory.createFileUploadButton("Upload Background Image", "upload-background-button", Map(), handleFileSelected))
    root.appendChild(EvaHtmlFactory.createButton("del-background-img-button", "Delete Background Image", _ => ProgramState.instance.backgroundImage.setValue(None)))
    root.appendChild(EvaHtmlFactory.createNumberForm(ProgramState.instance.graphicConfig.backgroundImageTransparency, Some(1), Some(0), Some(255), List(_ => Eva1Control.redrawMainArea())))
    root
  }

  override def getControlElement: Element = {

    val root = document.createElement("div")

    root.appendChild(EvaHtmlFactory.createRadioButtonForm(ProgramState.instance.programConfig.evacuationStrategy))
    root.appendChild(EvaHtmlFactory.boxElement("Pane Dimension", createSizeControlElement))
    root.appendChild(EvaHtmlFactory.boxElement("Background Image", createImageControlElement))

    root
  }

}
