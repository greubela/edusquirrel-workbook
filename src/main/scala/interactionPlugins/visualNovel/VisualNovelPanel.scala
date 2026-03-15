package interactionPlugins.visualNovel

import contentmanagement.model.file.*

case class VisualNovelPanel(
                             image: FileDescription,
                             source: String,
                             description: String,
                             textContent: String
                           )
