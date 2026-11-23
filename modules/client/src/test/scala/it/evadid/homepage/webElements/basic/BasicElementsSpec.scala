package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.{HtmlAppElement, UiDomSuite}
import it.evadid.homepage.webElements.basic.HtmlButtonElement.ButtonConfig

class BasicElementsSpec extends UiDomSuite {
  test("a dropdown menu lists its labels and items; choosing an item runs its action") {
    val chosen = Var(List.empty[String])
    val menu = HtmlDropdownMenu(Var(true), List(
      HtmlDropdownMenu.menuLabel(Val("Signed in as Ada")),
      HtmlDropdownMenu.menuLabel("menu/heading"),
      HtmlDropdownMenu.menuItem("menu/catalogue", _ => chosen.update(_ :+ "catalogue")),
      HtmlDropdownMenu.menuItem(LanguageMapContentId("menu/reset"), _ => chosen.update(_ :+ "reset")),
      HtmlDropdownMenu.menuItem(Val("Log out"))))
    mounted(menu.getDomElement()) { root =>
      assertEquals(root.getAttribute("role"), "menu")
      assertEquals(all(root, "[role=presentation]").map(_.textContent).head, "Signed in as Ada")
      assert(all(root, "[role=presentation]")(1).textContent.contains(loadingText("menu/heading")))
      val items = all(root, "[role=menuitem]")
      assertEquals(items.size, 3)
      click(items(1))
      click(items.head)
      click(items(2))
      assertEquals(chosen.now(), List("reset", "catalogue"), "an item without an action does nothing")
    }
  }

  test("buttons show their label or icon, follow their visibility and styles, and report clicks") {
    val config = Var(HtmlButtonElement.stdConfig)
    val clicks = Var(0)
    val labeled = HtmlButtonElement.withTextLabel("basic/save", _ => clicks.update(_ + 1), config.signal)
    val icon = HtmlButtonElement.withSvgContent(svg.svg(svg.cls := "icon"), _ => clicks.update(_ + 10))
    val fixed = HtmlButtonElement.withTextLabel(LanguageMapContentId("basic/open"), _ => (), ButtonConfig(false, List("wide")))
    mounted(div(labeled.getDomElement(), icon.getDomElement(), fixed.getDomElement(), HtmlButtonElement.withTextLabel("basic/plain").getDomElement())) { root =>
      val List(save, svgButton, open, plain) = all(root, "button"): @unchecked
      assert(save.textContent.contains(loadingText("basic/save")))
      assert(save.classList.contains("button-labeled"))
      assertEquals(save.asInstanceOf[org.scalajs.dom.html.Button].style.visibility, "visible")
      config.set(ButtonConfig(isVisible = false, List("primary", "large")))
      assertEquals(save.asInstanceOf[org.scalajs.dom.html.Button].style.visibility, "hidden")
      assert(save.classList.contains("primary") && save.classList.contains("large") && save.classList.contains("button-labeled"))
      click(save)
      click(svgButton)
      click(plain)
      assertEquals(clicks.now(), 11)
      assert(svgButton.classList.contains("button-svg") && all(svgButton, "svg.icon").size == 1)
      assert(open.classList.contains("wide"))
      assertEquals(open.asInstanceOf[org.scalajs.dom.html.Button].style.visibility, "hidden")
    }
  }

  test("wrapping a DOM element keeps it and closes on outside clicks by default") {
    val element = div("content")
    val app = HtmlAppElement(element)
    assert(app.getDomElement() eq element)
    assert(app.dismissOnOutsideClick)
  }
}
