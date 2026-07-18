package it.evadid.evacuation.core.graphic.model

sealed trait EvaImage {

}

object EvaImage {


  case class PathBasedEvaImage(fullFilePath: String) extends EvaImage

  case class DataBasedEvaImage(fullFileName: String, fileType: String, data: Array[Byte]) extends EvaImage


  def fromPath(pFullFilePath: String): PathBasedEvaImage = new PathBasedEvaImage(pFullFilePath)

  def fromData(fileInformation: EvaFileInformation): DataBasedEvaImage = new DataBasedEvaImage(fileInformation.fileName, fileInformation.fileType, fileInformation.fileData)

}

