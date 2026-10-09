# SQL editor

`SqlCommandExercise` stores a SQL string as the learner answer and a
`SqlDatabaseConfig` as the exercise's database reference. The regular workbook
factory and renderer registries recognize it. The renderer binds the answer to
workbook synchronization and opens the editor using the same bound state.

```scala
import it.evadid.workbook.elements.interactionElements.sql.*

SqlCommandExercise(
  elementId = "sql-students",
  databaseConfig = SqlDatabaseConfig("school_exercises"),
  initialSql = "SELECT id, name FROM students;"
)
```

All SQL editor and renderer classes are in
`modules/client/src/main/scala/it/evadid/homepage/webElements/sqlEditor`.
The interaction and wire types live in shared core; the JDBC handler lives in
the server. Changes to existing classes only add registrations and SQL language
support.

## Test/design workbook sample

Open `homepage/workbookDesign/` and select **SQL editor** in the test workbook.
The sample joins `students` and `teachers`, filters grades 10 and above, and sorts
by student name. Its tables and foreign key also populate the database diagram.

Load the synthetic data in
`resources/workbookresources/TestWorkbook/sql-editor-demo.sql` with a provisioning
account. The script creates `school_exercises`, its tables, and three sample
students. Repeated imports preserve existing rows. Set `SQL_EDITOR_DATABASES` to
include `school_exercises`, configure the dedicated teaching credentials as below,
and sign in. The initial query returns Lina and Sam with their respective teachers.
Without a configured database, the sample still opens for text/block editing and
shows the connection/configuration indicator.

The sample uses a small, reproducible teaching dataset. Each exercise selects its
database and starter SQL; the block templates use generic table and column names.
To use another dataset, provision it on your backend, allow-list its database name, and
set the exercise's `SqlDatabaseConfig` and `initialSql`. Table names, columns, and
relationships in the diagram come from that database's JDBC metadata.

## Editing and execution

The text tab reuses `CodeMirrorEditor`, with MySQL syntax support and its existing
textarea fallback. The block tab is a SQL-specific, Snap-like palette and clause
workspace: add, edit, drag, remove, and reorder clauses. Its blocks contain SQL,
including generic `AVG(column_name)` and `COUNT(column_name)` templates.
`SnapPaletteCatalog` supplies the existing comparison selector ordering; the SQL
palette maps those selectors to SQL operators, including `<>` for inequality.
SQL clauses have their own palette because turtle and VM control blocks do not
represent SQL statements. Arbitrary SQL is retained
as raw clause content. Comments, quoted strings, and nested queries are preserved;
switching tabs never converts SQL through Python or the programming VM.

Run executes one JDBC SQL statement. Separate statements should be run separately;
MySQL multi-query execution is disabled. Result tables show column labels and
SQL NULL values, and updates show affected row counts. At most 500 rows are shown;
truncation is reported. Queries have a ten-second timeout, connection attempts a
five-second timeout, and socket reads a fifteen-second timeout.

Connection status distinguishes unavailable database connections, missing server
configuration, and unavailable backend access. SQL syntax errors retain the
connected indicator. The database diagram below the results displays tables,
columns, primary keys, and foreign-key arrows from JDBC metadata. It refreshes
after execution, including schema-changing commands. Schema lookup errors are
reported independently from execution results.

## Server configuration

The browser calls the existing backend execution transport. Database credentials
are never included in workbook definitions or learner state. The command requires
the existing backend login.

The handler reuses the existing server `DatabaseConfig` host, port, and JDBC URL:

- `SQL_HOST` and `SQL_PORT`: MySQL endpoint.
- `SQL_EDITOR_DATABASES`: comma-separated allow-list of exercise database names.
- `SQL_EDITOR_USER` and `SQL_EDITOR_PW`: dedicated teaching database credentials.

Configure that MySQL account with privileges **only on the exercise databases**.
SQL may reference qualified table names; MySQL grants enforce access to those
names as well as commands that change the current database. Never use the account
or synchronization database credentials for arbitrary learner SQL. The endpoint
rejects database names that are absent from the allow-list or contain characters
other than letters, digits, and underscores. Missing teaching credentials do not
fall back to the application's account database credentials.

Teaching data and any required tables must already exist. DML and DDL changes
commit according to MySQL's normal autocommit behavior; exercises sharing a
database share its mutable contents.

## Validation

```text
sbt 'client/compile' 'server/compile'
sbt 'client/testOnly *SqlBlocksSpec'
sbt 'coreJVM/testOnly *SqlCommandExerciseSpec' 'coreJS/testOnly *SqlCommandExerciseSpec'
sbt 'server/testOnly *SqlEditor*Spec'
```

The tests cover lossless editing of nested/quoted/commented SQL, clause ordering,
workbook definition and wire serialization, allow-list validation, SQL NULL,
result-set cleanup, row limits, and metadata wildcard escaping. A live MySQL
connection additionally requires the configuration above and a signed-in client.
