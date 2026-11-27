package it.evadid.homepage.webElements.basic


import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.user.{AllUserInfo, UserConfig, UserCredentials}
import it.evadid.homepage.control.singletons.HtmlFullWorkbookApp
import it.evadid.homepage.webElements.UiDomSuite
import it.evadid.llm.LlmConfiguration
import it.evadid.sql.DatabaseConfigWithCredentials
import org.scalajs.dom
import scala.concurrent.{ExecutionContext, Future}

/** The session-connection form edits drafts and applies them to the signed-in user's credentials only. */
class SessionCredentialsEditorSpec extends UiDomSuite {
  private given ExecutionContext = ExecutionContext.global
  private lazy val page = HtmlFullWorkbookApp.fullInfo
  private val database = DatabaseConfigWithCredentials("mysql", "db.school.example", "3307", "lessons", "teacher", "secret")
  private val user = AllUserInfo.createNewUser("Connections", "connections@school.example", UserConfig(Nil, isOnlineAccount = false))
    .copy(credentials = UserCredentials(databaseConfig = Some(database)))

  private def signedIn[T](body: => Future[T]): Future[T] =
    page.userSessionControl.changeUser(Some(user)).flatMap(_ => body).transformWith { outcome =>
      page.userSessionControl.changeUser(None).transform(_ => outcome)
    }
  private def field(root: dom.Element, caption: String) =
    withText(root, "label", caption).headOption.map(one(_, "input")).getOrElse(fail(s"no field $caption"))
  private def status(root: dom.Element) = one(root, "[role=status]").textContent

  test("the form starts from the current connections and applies validated changes before closing") {
    signedIn {
      val closed = Var(0)
      mountedAsync(SessionCredentialsEditor(page, () => closed.update(_ + 1)).getDomElement()) { root =>
        assertEquals(List("Host", "Port", "Database", "User").map(caption => valueOf(field(root, caption))),
          List("db.school.example", "3307", "lessons", "teacher"))
        assertEquals(field(root, "Password").getAttribute("type"), "password")
        assertEquals(withText(root, "label", "Model").size, 0, "LLM fields stay hidden until enabled")

        typeInto(field(root, "Port"), "99999")
        click(buttonNamed(root, "Apply"))
        assert(status(root).endsWith("Invalid database port"), status(root))
        typeInto(field(root, "Port"), "3306")
        typeInto(field(root, "Host"), " ")
        click(buttonNamed(root, "Apply"))
        assert(status(root).endsWith("Enter database host, database name and user"), status(root))
        typeInto(field(root, "Host"), " db2.school.example ")

        click(withText(root, "label", "Use my LLM configuration").map(one(_, "input")).head)
        assertEquals(valueOf(field(root, "Model")), LlmConfiguration.defaultModel)
        typeInto(field(root, "Maximum output tokens"), "many")
        click(buttonNamed(root, "Apply"))
        assert(status(root).endsWith("Invalid token limit"), status(root))
        typeInto(field(root, "Maximum output tokens"), "-1")
        click(buttonNamed(root, "Apply"))
        assert(status(root).endsWith("Token limit must be positive"), status(root))
        typeInto(field(root, "Model"), "")
        click(buttonNamed(root, "Apply"))
        assert(status(root).endsWith("Enter LLM endpoint and model"), status(root))
        typeInto(field(root, "Model"), "tiny-model")
        typeInto(field(root, "Maximum output tokens"), "256")
        typeInto(field(root, "API key"), "")
        click(buttonNamed(root, "Apply"))
        eventually(closed.now() == 1, clue = "closing after applying").map { _ =>
          val credentials = page.current.userInfo.get.credentials
          assertEquals(credentials.databaseConfig, Some(database.copy(host = "db2.school.example", port = "3306")))
          assertEquals(credentials.llmApiConfig, Some(LlmConfiguration(LlmConfiguration.defaultEndpoint, "tiny-model", None, Some(256))))
        }
      }
    }
  }

  test("disabling a custom connection removes it; cancelling leaves the credentials unchanged") {
    signedIn {
      val closed = Var(0)
      mountedAsync(SessionCredentialsEditor(page, () => closed.update(_ + 1)).getDomElement()) { root =>
        typeInto(field(root, "Host"), "changed.example")
        click(buttonNamed(root, "Cancel"))
        assertEquals(closed.now(), 1)
        assertEquals(page.current.userInfo.get.credentials.databaseConfig, Some(database))
        click(withText(root, "label", "Use my MySQL database").map(one(_, "input")).head)
        assertEquals(withText(root, "label", "Host").size, 0)
        click(buttonNamed(root, "Apply"))
        eventually(closed.now() == 2).map(_ => assertEquals(page.current.userInfo.get.credentials.databaseConfig, None))
      }
    }
  }

  test("without a profile the form explains that one is needed") {
    page.userSessionControl.changeUser(None).map { _ =>
      mounted(SessionCredentialsEditor(page, () => ()).getDomElement()) { root =>
        assertEquals(withText(root, "label", "Host").size, 0, "no current database connection")
        click(buttonNamed(root, "Apply"))
        assert(status(root).endsWith("Create or log in to a profile first"), status(root))
      }
    }
  }
}
