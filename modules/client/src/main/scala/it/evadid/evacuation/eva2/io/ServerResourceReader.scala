package it.evadid.evacuation.eva2.io

import it.evadid.evacuation.core.io.util.ResourceReader
import org.scalajs.dom

import java.util
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.Thenable.Implicits.*
import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}

object ServerResourceReader extends ResourceReader {

  private implicit val context: ExecutionContext = ExecutionContext.global

  override def resolveFullName(resourceName: String): String = {
    if (resourceName.startsWith("/")) throw new IllegalArgumentException("Invalid resource name: must not begin with / in server mode!")
    "./" + resourceName
  }

  override def getResourceBytes(resourceName: String): Future[Array[Byte]] = {
    println("getResourceBytes(" + resourceName + "), resolving to: " + resolveFullName(resourceName))
    dom.fetch(resolveFullName(resourceName)).toFuture.flatMap(_.arrayBuffer().toFuture).map(parseByteBuffer)
  }

  private def parseByteBuffer(buf: ArrayBuffer): Array[Byte] = {
    val typedBuffer = TypedArrayBuffer.wrap(buf)
    val arr = new Array[Byte](typedBuffer.remaining)
    typedBuffer.get(arr)
    arr
  }

  override def getResourceLines(resourceName: String): Future[List[String]] = {
    getResourceText(resourceName).map(_.split("\n")).map(_.toList)
  }

  override def getResourceText(resourceName: String): Future[String] = {
    println("getResourceBytes(" + resourceName + "), resolving to: " + resolveFullName(resourceName))
    dom.fetch(resolveFullName(resourceName)).toFuture.flatMap(_.text().toFuture)
  }


  def test(): Unit = {
    getResourceLines("DefaultMooreTileMap.txt").foreach(println(_))
    getResourceBytes("DefaultMooreTileMap.txt").foreach(res => println(util.Arrays.toString(res)))
    getResourceBytes("DefaultMooreTileMap.txt").foreach(res => println(new String(res)))
  }

}
