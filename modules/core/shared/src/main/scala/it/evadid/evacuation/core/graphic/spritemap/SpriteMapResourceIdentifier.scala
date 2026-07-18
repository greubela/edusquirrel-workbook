package it.evadid.evacuation.core.graphic.spritemap

case class SpriteMapResourceIdentifier(id: String, layout: String, size: Int, description: String, folderName: Option[String] = None) {
  val desiredColsInSelection: Int = if (layout == "default") 9 else 7
}

object SpriteMapResourceIdentifier {

  val availableSpriteMaps: List[SpriteMapResourceIdentifier] = List(
    SpriteMapResourceIdentifier("default1", "default", 1, "Default (1px)"),
    SpriteMapResourceIdentifier("default3", "default", 3, "Default (3px)"),
    SpriteMapResourceIdentifier("default5", "default", 5, "Default (5px)"),
    SpriteMapResourceIdentifier("default16", "default", 16, "Default (16px)"),
    SpriteMapResourceIdentifier("default32", "default", 32, "Default (32px)"),

    //SpriteMapResourceIdentifier("topdown", 1, "Topdown (1px)"),
    //SpriteMapResourceIdentifier("default", 2, "Topdown (2px)"),
    SpriteMapResourceIdentifier("topdown8", "topdown", 8, "Topdown (8px) school"),
    SpriteMapResourceIdentifier("topdown16", "topdown", 16, "Topdown (16px) school"),
    SpriteMapResourceIdentifier("topdown24", "topdown", 24, "Topdown (24px) technical"),
    SpriteMapResourceIdentifier("topdown32", "topdown", 32, "Topdown (32px) interior"),
    //SpriteMapResourceIdentifier("topdown32t", "topdown", 32, "Topdown (32px) technical", Some("technical32")),
    SpriteMapResourceIdentifier("topdown48", "topdown", 48, "Topdown (48px) horror"),
  )

  def getFrom(name: String, size: Int): Option[SpriteMapResourceIdentifier] = {
    availableSpriteMaps.find(id => id.size == size && id.layout == name)
  }

  def getFrom(str: String): Option[SpriteMapResourceIdentifier] = try {
    val name = str.replaceAll("\\d*", "")
    val size = Integer.parseInt(str.replaceAll("\\D*", ""))
    Some(SpriteMapResourceIdentifier.getFrom(name, size).get)
  } catch {
    case e: Exception => {
      None
    }
  }


}