package it.evadid.evacuation.core.graphic.sprites.traits

import it.evadid.evacuation.core.graphic.spritemap.FrameData

trait Sprite {

  val name: String
  val id: Int

  def frameData: FrameData

}


object Sprite {
  import it.evadid.evacuation.core.graphic.sprites.*
  private case class StoredSprite(kind: String, payload: ujson.Value) derives upickle.default.ReadWriter

  given upickle.default.ReadWriter[Sprite] = upickle.default.readwriter[StoredSprite].bimap(
    sprite => sprite match {
      case value: BasicSprite => StoredSprite("BasicSprite", upickle.default.writeJs(value))
      case value: BasicFloorSprite => StoredSprite("BasicFloorSprite", upickle.default.writeJs(value))
      case value: BasicPersonSprite => StoredSprite("BasicPersonSprite", upickle.default.writeJs(value))
      case value: BasicAnimatedSprite => StoredSprite("BasicAnimatedSprite", upickle.default.writeJs(value))
      case value: AnimatedPersonSprite => StoredSprite("AnimatedPersonSprite", upickle.default.writeJs(value))
      case value: BasicOverlaySprite => StoredSprite("BasicOverlaySprite", upickle.default.writeJs(value))
      case value: AnimatedOverlaySprite => StoredSprite("AnimatedOverlaySprite", upickle.default.writeJs(value))
      case other => throw new IllegalArgumentException(s"Unsupported sprite: ${other.getClass.getName}")
    }, stored => stored.kind match {
      case "BasicSprite" => upickle.default.read[BasicSprite](stored.payload)
      case "BasicFloorSprite" => upickle.default.read[BasicFloorSprite](stored.payload)
      case "BasicPersonSprite" => upickle.default.read[BasicPersonSprite](stored.payload)
      case "BasicAnimatedSprite" => upickle.default.read[BasicAnimatedSprite](stored.payload)
      case "AnimatedPersonSprite" => upickle.default.read[AnimatedPersonSprite](stored.payload)
      case "BasicOverlaySprite" => upickle.default.read[BasicOverlaySprite](stored.payload)
      case "AnimatedOverlaySprite" => upickle.default.read[AnimatedOverlaySprite](stored.payload)
      case other => throw new IllegalArgumentException(s"Unknown sprite: $other")
    })

  private[traits] def subtypeCodec[T <: Sprite: scala.reflect.ClassTag]: upickle.default.ReadWriter[T] =
    upickle.default.readwriter[Sprite].bimap(value => value, value =>
      summon[scala.reflect.ClassTag[T]].unapply(value)
        .getOrElse(throw new IllegalArgumentException("Unexpected sprite type")))
}
