package it.evadid.evacuation.core.io.util

import scala.concurrent.Future

trait ResourceReader {

  protected def resolveFullName(resourceName: String): String

  def getResourceText(resourceName: String): Future[String]

  def getResourceLines(resourceName: String): Future[List[String]]

  def getResourceBytes(resourceName: String): Future[Array[Byte]]

}
