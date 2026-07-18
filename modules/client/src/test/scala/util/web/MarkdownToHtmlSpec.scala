package util.web

import it.evadid.core.util.MarkdownToHtml

import munit.FunSuite

class MarkdownToHtmlSpec extends FunSuite {

  test("renders headings, paragraphs and emphasis") {
    val markdown = """# Title
      |
      |## Subtitle
      |This has **bold**, *italic*, and `code`.
      |""".stripMargin

    val html = MarkdownToHtml.transform(markdown)

    assert(html.contains("<h1>Title</h1>"))
    assert(html.contains("<h2>Subtitle</h2>"))
    assert(html.contains("<strong>bold</strong>"))
    assert(html.contains("<em>italic</em>"))
    assert(html.contains("<code>code</code>"))
  }

  test("renders ordered and unordered lists") {
    val markdown = """- one
      |- two
      |
      |1. first
      |2. second
      |""".stripMargin

    val html = MarkdownToHtml.transform(markdown)

    assert(html.contains("<ul><li>one</li><li>two</li></ul>"))
    assert(html.contains("<ol><li>first</li><li>second</li></ol>"))
  }

  test("renders links images quotes and horizontal rule") {
    val markdown = """> quoted
      |
      |[OpenAI](https://openai.com)
      |![alt](https://example.com/img.png)
      |---
      |""".stripMargin

    val html = MarkdownToHtml.transform(markdown)

    assert(html.contains("<blockquote>"))
    assert(html.contains("<a href=\"https://openai.com\""))
    assert(html.contains("<img src=\"https://example.com/img.png\""))
    assert(html.contains("<hr />"))
  }

  test("renders fenced code blocks and escapes html") {
    val markdown = """```scala
      |val x = 1 < 2
      |```
      |""".stripMargin

    val html = MarkdownToHtml.transform(markdown)

    assert(html.contains("<pre><code class=\"language-scala\">"))
    assert(html.contains("val x = 1 &lt; 2"))
  }
}
