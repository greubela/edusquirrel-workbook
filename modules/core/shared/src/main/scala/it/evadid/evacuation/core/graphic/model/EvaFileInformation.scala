package it.evadid.evacuation.core.graphic.model

import upickle.default.ReadWriter

case class EvaFileInformation(fileName: String, fileData: Array[Byte]) derives ReadWriter {
  def fileType: String = fileName.split("\\.").last.trim
}
