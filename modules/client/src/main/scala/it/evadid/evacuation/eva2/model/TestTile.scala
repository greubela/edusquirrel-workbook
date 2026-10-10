package it.evadid.evacuation.eva2.model

case class TestTile(nr: Int) derives upickle.default.ReadWriter{

  override val toString: String = "[" + nr + "]"

}
