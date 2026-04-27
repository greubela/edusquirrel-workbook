package contentmanagement.webElements.genericHtmlElements.editor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{option as optionTag, *}
import contentmanagement.webElements.HtmlAppElement
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
case class SimpleSelectorEditor[T](
  forVar: Var[T],
  options: List[T],
  labels: List[LanguageMap[HumanLanguage]]
) extends HtmlAppElement {

  require(
    options.length == labels.length,
    "SimpleSelectorEditor requires the number of options to match the number of labels."
  )

  private val optionEntries = options
    .zip(labels)
    .zipWithIndex
    .map { case ((value, label), index) => (value, label, index.toString) }

  private val valueToIndex: Map[T, String] = optionEntries.map { case (value, _, indexString) => value -> indexString }.toMap
  private val indexToValue: Map[String, T] = optionEntries.map { case (value, _, indexString) => indexString -> value }.toMap

  private val fallbackIndex = optionEntries.headOption.map(_._3).getOrElse("")

  private val selectElement = select(
    cls := "simple-selector-editor__select",
    optionEntries.map { case (_, label, indexString) =>
      val labelText = label.getInLanguage(AppLanguage.default())
      optionTag(
        value := indexString,
        labelText
      )
    },
    controlled(
      value <-- forVar.signal.map(value => valueToIndex.getOrElse(value, fallbackIndex)),
      onChange.mapToValue
        .map(indexToValue.get)
        .collect { case Some(value) => value } --> forVar.writer
    )
  )

  private val domElement = div(
    cls := "simple-selector-editor",
    selectElement
  )

  override def getDomElement(): L.Element = domElement
}

object SimpleSelectorEditor {
  def apply[T](
    forVar: Var[T],
    options: List[T],
    labelGenerator: T => LanguageMap[HumanLanguage]
  ): SimpleSelectorEditor[T] = {
    SimpleSelectorEditor(forVar, options, options.map(labelGenerator))
  }
}
