package it.evadid.server.database

import it.evadid.util.logging.BasicLogger
import java.sql.SQLException
import munit.FunSuite

class SqlFailureSpec extends FunSuite {
  List("08001" -> "connect", "28000" -> "credentials", "23000" -> "constraint",
    "22001" -> "column limits", "40001" -> "conflicted", "42000" -> "table/column").foreach { (state, reason) =>
    test(s"SQLState $state gives actionable context without exposing vendor data") {
      val error = intercept[SQLException] {
        SqlFailure("reading account", BasicLogger()) { throw SQLException("private bound value", state, 17) }
      }
      assert(error.getMessage.contains("reading account"))
      assert(error.getMessage.contains(reason))
      assert(error.getMessage.contains(s"SQLState=$state, errorCode=17"))
      assert(!error.getMessage.contains("private bound value"))
      assertEquals(error.getCause, null)
    }
  }

  test("nested SQL boundaries keep the most specific diagnostic") {
    val logger = BasicLogger()
    val error = intercept[SQLException] {
      SqlFailure("account request", logger) {
        SqlFailure("reading user table", logger) { throw SQLException("private", "42000", 17) }
      }
    }
    assert(error.getMessage.contains("reading user table"))
    assert(!error.getMessage.contains("account request"))
  }
}
