package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.llm.LlmConfiguration
import it.evadid.sql.DatabaseConfiguration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

/** Draft values exist only while this form is open. Applying never writes preferences. */
case class SessionCredentialsEditor(close: () => Unit) extends HtmlAppElement {
  private val current = fullInfo.current.userInfo.map(_.credentials)
  private val database = current.flatMap(_.databaseConfig)
  private val llm = current.flatMap(_.llmApiConfig)
  private val dbEnabled = Var(database.nonEmpty)
  private val llmEnabled = Var(llm.nonEmpty)
  private val host = Var(database.map(_.host).getOrElse(""))
  private val port = Var(database.map(_.port).getOrElse("3306"))
  private val schema = Var(database.map(_.database).getOrElse(""))
  private val user = Var(database.map(_.user).getOrElse(""))
  private val password = Var(database.map(_.password).getOrElse(""))
  private val endpoint = Var(llm.map(_.endpoint).getOrElse(LlmConfiguration.defaultEndpoint))
  private val model = Var(llm.map(_.model).getOrElse(LlmConfiguration.defaultModel))
  private val apiKey = Var(llm.flatMap(_.apiKey).getOrElse(""))
  private val tokens = Var(llm.flatMap(_.maxTokens).fold("")(_.toString))
  private val message = Var("")
  private val saving = Var(false)

  private def field(caption: String, state: Var[String], secret: Boolean = false): Element = label(
    span(caption), input(typ := (if secret then "password" else "text"), autoComplete := "off",
      value <-- state.signal, onInput.mapToValue --> state))

  private def applyChanges(): Unit = {
    val updated = Try {
      val credentials = fullInfo.current.userInfo.getOrElse(throw IllegalArgumentException("Create or log in to a profile first")).credentials
      val db = Option.when(dbEnabled.now()) {
        require(host.now().trim.nonEmpty && schema.now().trim.nonEmpty && user.now().trim.nonEmpty, "Enter database host, database name and user")
        require(Try(port.now().toInt).toOption.exists(p => p > 0 && p <= 65535), "Invalid database port")
        DatabaseConfiguration("mysql", host.now().trim, port.now().trim, schema.now().trim, user.now().trim, password.now())
      }
      val config = Option.when(llmEnabled.now()) {
        require(endpoint.now().trim.nonEmpty && model.now().trim.nonEmpty, "Enter LLM endpoint and model")
        val limit = Option(tokens.now().trim).filter(_.nonEmpty).map(text => Try(text.toInt).getOrElse(throw IllegalArgumentException("Invalid token limit")))
        require(limit.forall(_ > 0), "Token limit must be positive")
        LlmConfiguration(endpoint.now().trim, model.now().trim, Option(apiKey.now()).filter(_.nonEmpty), limit)
      }
      credentials.copy(databaseConfig = db, llmApiConfig = config)
    }
    updated match {
      case Failure(error) => message.set(error.getMessage)
      case Success(credentials) =>
        saving.set(true)
        fullInfo.usageControl.updateCredentials(credentials).onComplete {
          case Success(_) => close()
          case Failure(_) => saving.set(false); message.set("Credentials applied. Database synchronization failed; check the connection and schema.")
        }
    }
  }

  override def getDomElement(): Element = div(cls := "session-credentials", role := "dialog", aria.label := "Session connections",
    h3("Session connections"),
    p("These credentials are held in memory for this session. Enter them again after reloading. Leave custom connections disabled to use the server defaults."),
    label(input(typ := "checkbox", checked <-- dbEnabled.signal, onChange.mapToChecked --> dbEnabled), " Use my MySQL database"),
    child.maybe <-- dbEnabled.signal.map(enabled => Option.when(enabled)(div(
      field("Host", host), field("Port", port), field("Database", schema), field("User", user), field("Password", password, true)))),
    label(input(typ := "checkbox", checked <-- llmEnabled.signal, onChange.mapToChecked --> llmEnabled), " Use my LLM configuration"),
    child.maybe <-- llmEnabled.signal.map(enabled => Option.when(enabled)(div(
      field("Chat completion URL", endpoint), field("Model", model), field("API key (optional for local services)", apiKey, true), field("Maximum output tokens (optional)", tokens)))),
    p(role := "status", child.text <-- message.signal),
    button(typ := "button", "Apply", disabled <-- saving.signal, onClick --> (_ => applyChanges())),
    button(typ := "button", "Cancel", disabled <-- saving.signal, onClick --> (_ => close()))
  )
}
