package it.evadid.server.database

import it.evadid.sql.DatabaseConfigWithCredentials
import java.sql.{Connection, DriverManager, SQLException}
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.Using
import munit.FunSuite

/** Execute the complete standalone script, including its guard, in an already selected database. */
class DatabaseSetupMysqlSpec extends FunSuite {
  private lazy val statements: Seq[String] = Using.resource(Source.fromResource(DatabaseSqlScripts.name)) { source =>
    CCJSqlParserUtil.parseStatements(source.mkString).asScala.toSeq.map(_.toString)
  }
  private def executeScript(connection: Connection): Unit = Using.resource(connection.createStatement()) { statement =>
    statements.foreach(sql => statement.execute(sql))
  }
  private def execute(connection: Connection, sql: String): Unit =
    Using.resource(connection.createStatement())(_.execute(sql))
  private def tables(connection: Connection): Set[String] =
    Using.resource(connection.getMetaData.getTables(connection.getCatalog, null, "%", Array("TABLE", "VIEW"))) { rows =>
      Iterator.continually(rows.next()).takeWhile(identity).map(_ => rows.getString("TABLE_NAME")).toSet
    }

  override def beforeEach(context: BeforeEach): Unit = MysqlTestDatabase.withConnection(MysqlTestDatabase.reset)

  test("standalone setup creates all tables in the selected empty database without changing its catalog") {
    MysqlTestDatabase.withConnection { connection =>
      MysqlTestDatabase.clearSchema(connection)
      val selected = connection.getCatalog
      executeScript(connection)
      assertEquals(connection.getCatalog, selected)
      assertEquals(tables(connection), DatabaseSqlScripts.tables.map(table => DatabaseSqlScripts.identifier(table.getTable.getName)).toSet)
      assert(DatabaseSchemaInspector.inspect(connection).valid)
    }
  }

  test("standalone setup rejects an existing conflicting table and preserves its data") {
    MysqlTestDatabase.withConnection { connection =>
      MysqlTestDatabase.clearSchema(connection)
      execute(connection, "CREATE TABLE `user` (legacy_value VARCHAR(32) NOT NULL)")
      execute(connection, "INSERT INTO `user` VALUES ('existing development data')")
      val error = intercept[SQLException](executeScript(connection))
      assert(error.getMessage.contains("setup_requires_empty_database"))
      assertEquals(tables(connection), Set("user"))
      Using.resource(connection.createStatement()) { statement =>
        Using.resource(statement.executeQuery("SELECT legacy_value FROM `user`")) { rows =>
          assert(rows.next())
          assertEquals(rows.getString(1), "existing development data")
          assert(!rows.next())
        }
      }
    }
  }

  test("standalone setup fails without a selected database and leaves the existing database unchanged") {
    MysqlTestDatabase.withConnection { selected =>
      val originalTables = tables(selected)
      val config = DatabaseConfigWithCredentials.readFromEnv().copy(database = "")
      Using.resource(DriverManager.getConnection(config.jdbcUrl, config.user, config.password)) { connection =>
        assertEquals(connection.getCatalog, "")
        val error = intercept[SQLException](executeScript(connection))
        assertEquals(error.getErrorCode, 1046) // No database selected.
        assertEquals(connection.getCatalog, "")
      }
      assertEquals(tables(selected), originalTables)
    }
  }

  test("standalone setup also rejects an existing view before creating application tables") {
    MysqlTestDatabase.withConnection { connection =>
      MysqlTestDatabase.clearSchema(connection)
      execute(connection, "CREATE VIEW existing_view AS SELECT 1 AS value")
      try {
        val error = intercept[SQLException](executeScript(connection))
        assert(error.getMessage.contains("setup_requires_empty_database"))
        assertEquals(tables(connection), Set("existing_view"))
      } finally execute(connection, "DROP VIEW existing_view")
    }
  }
}
