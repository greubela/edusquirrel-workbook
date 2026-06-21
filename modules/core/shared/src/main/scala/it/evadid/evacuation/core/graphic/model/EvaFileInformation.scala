package it.evadid.evacuation.core.graphic.model

case class EvaFileInformation(fileName: String, fileData: Array[Byte]) {
  def fileType: String = fileName.split("\\.").last.trim
}
