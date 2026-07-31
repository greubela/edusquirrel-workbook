package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission

import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleLogic.TurtleRenderer
import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch.{TurtleStitchFromBeExpressionSerializer, TurtleStitchProgramModel, TurtleStitchProgramRenderer, TurtleStitchToBeExpressionParser, TurtleStitchXmlLoader}
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression

import java.nio.charset.{Charset, StandardCharsets}
import scala.util.Try

object TurtleFileSubmission {

  def renderFile(fileBytes: Array[Byte]): String = {
    val (existingPenTrailDataUrl, simulatedDataUrl) = renderFileAsTuple(fileBytes)
    if (existingPenTrailDataUrl.nonEmpty) existingPenTrailDataUrl else simulatedDataUrl
  }

  def renderFileAsTuple(fileBytes: Array[Byte]): (String, String) = {
    val xml = new String(fileBytes.map(_.toByte), "UTF-8")
    renderXmlAsTuple(xml)
  }

  def renderXmlAsTuple(xml: String): (String, String) = {
    val project = Try(TurtleStitchXmlLoader.load(xml)).getOrElse(TurtleStitchProgramModel.Project())

    val existingPenTrailDataUrl = project.scenes
      .lift(project.selectedScene - 1)
      .orElse(project.scenes.headOption)
      .flatMap(_.stage.pentrails)
      .filter(_.startsWith("data:image/png;base64,"))
      .getOrElse("")

    val simulatedDataUrl = Try {
      val commands = TurtleStitchProgramRenderer.commandsFrom(project)
      TurtleRenderer.renderToPngDataUrl(commands)
    }.getOrElse(TurtleRenderer.renderToPngDataUrl(Nil))

    (existingPenTrailDataUrl, simulatedDataUrl)
  }

  def loadProject(xml: String): TurtleStitchProgramModel.Project =
    TurtleStitchXmlLoader.load(xml)

  def parseToBeExpression(xml: String): BeExpression =
    TurtleStitchToBeExpressionParser.parseXml(xml)

  def parseFileBytesToBeExpression(fileBytes: Array[Byte], charset: Charset = StandardCharsets.UTF_8): BeExpression =
    parseToBeExpression(new String(fileBytes, charset))

  def parseToBeProgram(xml: String): BeProgram =
    BeProgram(parseToBeExpression(xml))

  def parseFileBytesToBeProgram(fileBytes: Array[Byte], charset: Charset = StandardCharsets.UTF_8): BeProgram =
    parseToBeProgram(new String(fileBytes, charset))

  def serializeFromBeExpression(expression: BeExpression, projectName: String = "fromBeExpression"): String =
    TurtleStitchFromBeExpressionSerializer.toXml(expression, projectName)

  def renderProgramAsSvg(xml: String): Option[String] =
    Try {
      val project = TurtleStitchXmlLoader.load(xml)
      TurtleStitchProgramRenderer.renderScriptsAsSvgDataUrl(project)
    }.getOrElse(None)
}
