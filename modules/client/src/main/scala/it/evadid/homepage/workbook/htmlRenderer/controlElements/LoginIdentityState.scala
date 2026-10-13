package it.evadid.homepage.workbook.htmlRenderer.controlElements

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.user.User

/** Suggest student mail addresses until an address is entered or restored. */
private[controlElements] final class LoginIdentityState(initialName: String, initialEmail: String) {
  val name: Var[String] = Var(initialName)
  val email: Var[String] = Var(initialEmail)
  private var emailEntered = initialEmail.trim.nonEmpty

  def changeName(value: String): Unit = {
    name.set(value)
    if (!emailEntered) email.set(User.deriveHuMail(value).getOrElse(""))
  }

  def changeEmail(value: String): Unit = {
    emailEntered = value.trim.nonEmpty
    email.set(value)
  }
}
