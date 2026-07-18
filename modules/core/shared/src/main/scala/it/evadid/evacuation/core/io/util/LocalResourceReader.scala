package it.evadid.evacuation.core.io.util

import java.io.FileNotFoundException
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

object LocalResourceReader extends ResourceReader {

  private implicit val context: ExecutionContext = ExecutionContext.global

  override def resolveFullName(resourceName: String): String = {
    assert(resourceName.charAt(0) != '.', "Invalid resource name '" + resourceName + "': must not begin with . in local mode!")
    if (resourceName.startsWith("/")) resourceName else "/" + resourceName
  }

  override def getResourceText(resourceName: String): Future[String] = getResourceLines(resourceName).map(_.mkString("\n"))

  override def getResourceLines(resourceName: String): Future[List[String]] = Future {
    try {
      Source.fromResource(resourceName).getLines().toList
    } catch {
      case ex: Throwable => throw new FileNotFoundException("Cannot load resource '" + resourceName + "'")
    }


  }


  override def getResourceBytes(resourceName: String): Future[Array[Byte]] = Future {
    getClass.getResource(resolveFullName(resourceName)).openStream().readAllBytes()
  }



}
