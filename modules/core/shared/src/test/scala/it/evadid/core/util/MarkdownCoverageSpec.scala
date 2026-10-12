package it.evadid.core.util

import munit.FunSuite

class MarkdownCoverageSpec extends FunSuite {
  private def html(input: String) = MarkdownToHtml.transform(input)

  test("normalizes line endings and distinguishes soft and explicit line breaks") {
    assertEquals(html("one\r\ntwo\rthree  \nfour\\\nfive\n\nend"), "<p>one two three<br />four<br />five</p><p>end</p>")
    assertEquals(html("\n \n"), "")
  }

  test("paragraphs stop before headings, rules, quotes and lists") {
    assertEquals(html("intro\n## Title\n***\n> quote\n- item\nend"),
      "<p>intro</p><h2>Title</h2><hr /><blockquote><p>quote</p></blockquote><ul><li>item</li></ul><p>end</p>")
    assertEquals(html("####### text"), "<p>####### text</p>")
  }

  test("lists retain indented continuation text and switch between ordered and unordered lists") {
    assertEquals(html("1. first\n  continued\n2. second\n\n+ third\n* fourth"),
      "<ol><li>first continued</li><li>second</li></ol><ul><li>third</li><li>fourth</li></ul>")
  }

  test("nested quotes retain their block structure") {
    assertEquals(html("> # title\n>\n> > inner\n> outer"),
      "<blockquote><h1>title</h1><blockquote><p>inner</p></blockquote><p>outer</p></blockquote>")
  }

  test("closed and unterminated fences escape code without formatting its contents") {
    assertEquals(html("```scala\n**x** < y & z\n```\nafter"),
      "<pre><code class=\"language-scala\">**x** &lt; y &amp; z\n</code></pre><p>after</p>")
    assertEquals(html("```\nx"), "<pre><code>x</code></pre>")
    assertEquals(html("```\n```"), "<pre><code></code></pre>")
  }

  test("escaped punctuation remains literal while unescaped emphasis renders") {
    assertEquals(html("\\*literal\\* \\_plain\\_ **bold** _italic_ ***both*** __strong__ ___all___"),
      "<p>*literal* _plain_ <strong>bold</strong> <em>italic</em> <strong><em>both</em></strong> <strong>strong</strong> <strong><em>all</em></strong></p>")
    assertEquals(html("\\[x](url)"), "<p>[x](url)</p>")
  }

  test("inline code preserves backslashes and literal placeholder text") {
    assertEquals(html("`\\*x\\* < y` @@CODE0@@ `z`"),
      "<p><code>\\*x\\* &lt; y</code> @@CODE0@@ <code>z</code></p>")
    assertEquals(html("\\`literal\\`"), "<p>`literal`</p>")
    assertEquals(html("@@MDTOKEN0@@ `z`"), "<p>@@MDTOKEN0@@ <code>z</code></p>")
  }

  test("links escape ampersands once and preserve punctuation in attributes") {
    assertEquals(html("[**go**](https://example.test/a_b_c?q=1&x=2 \"A & B\")"),
      "<p><a href=\"https://example.test/a_b_c?q=1&amp;x=2\" title=\"A &amp; B\"><strong>go</strong></a></p>")
    assertEquals(html("[cost $5](https://example.test/$5)"),
      "<p><a href=\"https://example.test/$5\">cost $5</a></p>")
    assertEquals(html("**cost $5**"), "<p><strong>cost $5</strong></p>")
    assertEquals(html("[\\*go\\*](/page)"), "<p><a href=\"/page\">*go*</a></p>")
  }

  test("images keep alt text plain and escape attribute quotes") {
    assertEquals(html("![**A** \"B\"](/a_b_c.png \"Title\")"),
      "<p><img src=\"/a_b_c.png\" alt=\"**A** &quot;B&quot;\" title=\"Title\" /></p>")
    assertEquals(html("![x](/image)"), "<p><img src=\"/image\" alt=\"x\" /></p>")
  }

  test("HTML input stays escaped and malformed inline constructs remain text") {
    assertEquals(html("<script>\"x\" & 'y'</script> [broken]( `open"),
      "<p>&lt;script&gt;&quot;x&quot; &amp; &#39;y&#39;&lt;/script&gt; [broken]( `open</p>")
  }
}
