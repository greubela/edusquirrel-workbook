package interactionPlugins.fileSubmission.turtleStitch

import datastructures.core.vm.code.BeExpression
import org.scalajs.dom

import java.nio.charset.{Charset, StandardCharsets}
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.typedarray.Uint8Array
import scala.scalajs.js.JSConverters.*

object TurtleStitchBeExpressionAdapter {

  /** Converts TurtleStitch XML text directly into a [[BeExpression]]. */
  def fromXml(xml: String): BeExpression =
    TurtleStitchToBeExpressionParser.parseXml(xml)

  /**
   * Converts file contents (UTF-8 XML bytes by default) into a [[BeExpression]].
   */
  def fromFileBytes(fileBytes: Array[Byte], charset: Charset = StandardCharsets.UTF_8): BeExpression = {
    val xml = new String(fileBytes, charset)
    fromXml(xml)
  }

  /**
   * Reads a browser [[dom.File]] and converts the XML to a [[BeExpression]].
   */
  def fromFile(file: dom.File, charset: Charset = StandardCharsets.UTF_8)(using ec: ExecutionContext): Future[BeExpression] =
    file.arrayBuffer().toFuture.map { buffer =>
      val bytes = new Uint8Array(buffer).toArray.map(_.toByte)
      fromFileBytes(bytes, charset)
    }
}
