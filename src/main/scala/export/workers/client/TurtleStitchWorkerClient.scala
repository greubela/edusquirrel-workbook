package `export`.workers.client

import `export`.traits.AbstractWorkerClient
import org.scalajs.dom.html.Canvas

import scala.concurrent.Future


case class TurtleStitchWorkerClient(canvas: Canvas) extends AbstractWorkerClient("startTurtleWorkerServer", Map("hi" -> "bye"), Some(canvas)) {


  def getGreenFlagAsLispCode(xml_content: String, language: String): Future[String] =
    ???
    
  
  
  
  /*
.enqueue(name, Map("a" -> a.toString, "b" -> b.toString))
    .map(_.data.get("value").flatMap(_.toIntOption).getOrElse(0))
  */
  
  
  
}
