package contentmanagement.webElements.genericHtmlElements.editor

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{label as labelTag, *}
import contentmanagement.webElements.HtmlAppElement
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
case class SimpleBooleanEditor(forVar: Var[Boolean], label: LanguageMap[HumanLanguage]) extends HtmlAppElement {

  private val labelText = label.getInLanguage(AppLanguage.default())

  private val checkbox = input(
    typ := "checkbox",
    cls := "simple-boolean-editor__checkbox",
    controlled(
      checked <-- forVar.signal,
      onInput.mapToChecked --> forVar.writer
    )
  )

  private val domElement = div(
    cls := "workbook-interaction simple-boolean-editor",
    labelTag(
      cls := "simple-boolean-editor__body",
      checkbox,
      span(
        cls := "simple-boolean-editor__label-text",
        labelText
      )
    )
  )

  override def getDomElement(): L.Element = domElement
}
