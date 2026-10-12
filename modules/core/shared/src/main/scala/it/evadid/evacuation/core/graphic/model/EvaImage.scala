package it.evadid.evacuation.core.graphic.model

import upickle.default.ReadWriter

sealed trait EvaImage derives ReadWriter {

}

object EvaImage {


  case class PathBasedEvaImage(fullFilePath: String) extends EvaImage derives ReadWriter

  case class DataBasedEvaImage(fullFileName: String, fileType: String, data: Array[Byte]) extends EvaImage derives ReadWriter


  def fromPath(pFullFilePath: String): PathBasedEvaImage = new PathBasedEvaImage(pFullFilePath)

  def fromData(fileInformation: EvaFileInformation): DataBasedEvaImage = new DataBasedEvaImage(fileInformation.fileName, fileInformation.fileType, fileInformation.fileData)

}

