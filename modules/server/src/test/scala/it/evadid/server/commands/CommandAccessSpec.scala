package it.evadid.server.commands

import it.evadid.core.datastructures.user.User
import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.distribution.commandTypes.SubmissionCommands.submitSessionCommand
import it.evadid.distribution.commandTypes.SqlEditorCommands
import munit.FunSuite
import java.net.InetAddress
import java.time.Instant
import scala.concurrent.Future

class CommandAccessSpec extends FunSuite {
  import CommandAccess.*
  private val owner = User("Owner", "owner", "owner@example.org")
  private val other = User("Other", "other", "other@example.org")

  test("record and mail rules admit only the signed-in owner of every addressed record") {
    val records = OwnRecords[Seq[String]](identity)
    assert(records.allows(Seq("owner", "owner"), Some(owner)))
    assert(!records.allows(Seq("owner", "other"), Some(owner)))
    assert(!records.allows(Seq("owner"), None))
    val mail = OwnMail[String](identity)
    assert(mail.allows(owner.mail, Some(owner)))
    assert(!mail.allows(owner.mail, Some(other)))
    assert(SignedIn.allows((), Some(other)) && !SignedIn.allows((), None))
    assert(Public.allows((), None))
  }

  test("a handler's rule must match its command's login declaration, and only public handlers serve anonymous requests") {
    val anonymous = RequestContext(Instant.now(), InetAddress.getLoopbackAddress, None)
    val submit = Handler(submitSessionCommand, Public, (_, _, _) => Future.never)
    assert(submit.availableTo(anonymous))
    val editor = Handler(SqlEditorCommands.execute, SignedIn, (_, _, _) => Future.never)
    assert(!editor.availableTo(anonymous))
    assert(editor.availableTo(anonymous.copy(user = Some(owner))))
    intercept[IllegalArgumentException](Handler(SqlEditorCommands.execute, Public, (_, _, _) => Future.never))
    intercept[IllegalArgumentException](Handler(submitSessionCommand, SignedIn, (_, _, _) => Future.never))
    val unlisted = ExecutionCommandFactory[String, String]("fixture", requiresLogin = true)
    intercept[IllegalArgumentException](Handler(unlisted, Public, (_, _, _) => Future.never))
  }

  test("every backend handler declares a rule consistent with its command") {
    BackendCommandHandler(it.evadid.server.TestServerConfig(), it.evadid.server.account.SignedTokens("secret"),
      it.evadid.server.database.schema.SchemaValidation())
  }
}
