package it.evadid.homepage.workbook.htmlRenderer

import com.raquo.laminar.DomApi
import com.raquo.laminar.api.L.*
import it.evadid.core.util.MarkdownToHtml

/** Inline Markdown (bold, code, links) for legends, labels and table cells whose texts come from language maps.
 * Block structure is not rendered; the result is a single span, so it fits inside form controls.
 */
object InlineMarkdown {
  def element(markdown: Signal[String]): Element =
    span(cls := "markdown-content", child <-- markdown.map(text =>
      foreignHtmlElement(DomApi.unsafeParseHtmlString(s"<span>${MarkdownToHtml.transformInline(text)}</span>"))))
}
