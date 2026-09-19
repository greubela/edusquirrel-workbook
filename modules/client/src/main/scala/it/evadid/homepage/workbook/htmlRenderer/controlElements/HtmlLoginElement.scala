package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.workbook.abstractions.TypeOfTextDisplay.PLAINTEXT

case class HtmlLoginElement() extends HtmlAppElement {

  private val startWithAccount: Element = {
    div("Start with Account")
  }
  private val continueWithAccount: Element = {
    div(
      div("Request LoginToken"),
      div("Continue with LoginToken")
    )
  }

  // Start and Continue together (Insert Mail --> Insert Name or Token)

  private val localStartElement: Element = {
    div("Start Local Usage",
      ul(
        li("✓ No data leaves your device"),
        li("✗ No access to LLM feedback"),
      )
    )
  }

  private val localContinueElement: Element = {
    div("Continue with StorageFile")
  }

  private def createEmailInput(): Element = {
    input(
      label(text <-- laminarHelper.plaintextStringSignal("basic/enterMail")),
      required := true,
      typ := "email"
    )
  }

  private val onlineStartElement: Element = {
    div("Start Online Usage",
      ul(
        li("✓ Your process is backed up online"),
        li("✓ Share progress with a teacher")
      ),
      input("Enter Name"),
      createEmailInput(),

      div("Register")
    )
  }

  private val onlineContinueElement: Element = {
    div(
      div("Continue Online Usage"),
      createEmailInput(),
      div("Request Login Token"),

      input("Enter Login Token"),
      div("Log in")
    )
  }

  private val infoPanel: Element = {
    div("Error go here!")
  }

  lazy val domElement: Element = {
    div(
      cls := "login-container",
      div(
        cls := "login-section first-login",
        div(
          cls := "login-section-title",
          text <-- laminarHelper.contentIdStringSignal("basic/onlineLoginSection", PLAINTEXT, List())
        ),
        div(
          cls := "login-section-container",
          onlineStartElement,
          onlineContinueElement,
          localStartElement,
          localContinueElement,
        ),
      )
    )
  }

  override def getDomElement(): Element = domElement
}
