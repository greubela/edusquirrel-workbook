package interactionPlugins.visualNovel

import contentmanagement.model.image.ImageDescription

case class VisualNovelPanel(
                             image: ImageDescription,
                             source: String,
                             description: String,
                             textContent: String
                           )
