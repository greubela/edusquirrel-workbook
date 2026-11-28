package it.evadid.server

import it.evadid.llm.LlmConfiguration
import it.evadid.sql.DatabaseConfigWithCredentials
import it.evadid.util.JvmUtils

/** Complete server configuration, read once at startup; invalid or partial settings fail before serving requests. */
final case class ServerConfig(
    port: Int,
    hostname: String,
    version: String,
    allowedOrigins: Set[String],
    tokenSecret: String,
    database: Option[DatabaseConfigWithCredentials],
    sqlEditor: ServerConfig.SqlEditor,
    mail: Option[ServerConfig.Mail],
    llm: ServerConfig.Llm,
    archiveRecipient: String) {

  /** The backend's own database; commands that store rows fail with this hint when it is not configured. */
  def backendDatabase: DatabaseConfigWithCredentials = database.getOrElse(throw IllegalStateException(
    "The backend database is not configured; set SQL_HOST, SQL_PORT, SQL_DATABASE, SQL_USER and SQL_PW."))

  override def toString: String = s"ServerConfig(port=$port, hostname=$hostname, version=$version, " +
    s"database=${database.map(_.withoutCredentials)}, mail=${mail.map(_.sender)}, credentials=redacted)"
}

object ServerConfig {
  /** Exact teaching databases the SQL editor may open with its dedicated account. */
  final case class SqlEditor(databases: Set[String], credentials: Option[(String, String)]) {
    override def toString: String = s"SqlEditor($databases, credentials=redacted)"
  }

  final case class Mail(sender: String, password: String, host: String, port: String) {
    override def toString: String = s"Mail($sender, $host:$port, password=redacted)"
  }

  /** Server-side LLM defaults, used only when a request supplies no configuration of its own. */
  final case class Llm(endpoint: String, model: String, programmingHintModel: String,
                       apiKey: Option[String], maxTokens: Option[Int]) {
    def configuration(programmingHint: Boolean): LlmConfiguration =
      LlmConfiguration(endpoint, if programmingHint then programmingHintModel else model, apiKey, maxTokens)

    override def toString: String = s"Llm($endpoint, $model, $programmingHintModel, apiKey=redacted)"
  }

  def fromEnvironment(): ServerConfig = from(JvmUtils.env)

  /** `env` yields trimmed, non-empty values only, as [[JvmUtils.env]] does. */
  def from(env: String => Option[String]): ServerConfig = {
    def required(name: String, purpose: String): String =
      env(name).getOrElse(throw IllegalStateException(s"$name must be set: $purpose"))
    def group[A](names: Seq[String], purpose: String)(build: Seq[String] => A): Option[A] =
      Option.when(names.exists(env(_).nonEmpty))(build(names.map(required(_, purpose))))
    def integer(name: String): Option[Int] =
      env(name).map(value => value.toIntOption.getOrElse(throw IllegalStateException(s"$name must be an integer")))

    ServerConfig(
      port = integer("SERVER_PORT").getOrElse(9000),
      hostname = env("SERVER_HOSTNAME").getOrElse("[unknown]"),
      version = env("SERVER_VERSION").getOrElse("[unknown]"),
      allowedOrigins = allowedOriginsFrom(env("SERVER_ALLOWED_ORIGINS")),
      // A blank key would let anyone forge signed login tokens, so it is never defaulted.
      tokenSecret = required("SERVER_SECRET", "the signing secret of login tokens"),
      database = group(Seq("SQL_HOST", "SQL_PORT", "SQL_DATABASE", "SQL_USER", "SQL_PW"),
        "the backend database needs all connection settings") { case Seq(host, port, database, user, password) =>
        DatabaseConfigWithCredentials(env("DB_FORMAT").getOrElse("mysql"), host, port, database, user, password)
      },
      sqlEditor = SqlEditor(
        env("SQL_EDITOR_DATABASES").toSeq.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty).toSet,
        group(Seq("SQL_EDITOR_USER", "SQL_EDITOR_PW"), "the SQL editor account needs a user and password") {
          case Seq(user, password) => user -> password
        }),
      mail = group(Seq("MAIL_EMAIL", "MAIL_PW"), "mail delivery needs a sender address and password") {
        case Seq(sender, password) => Mail(sender, password,
          env("MAIL_SMTP_HOST").getOrElse("mail.lima-city.de"), env("MAIL_SMTP_PORT").getOrElse("587"))
      },
      llm = Llm(env("OPENAI_API_URL").getOrElse(LlmConfiguration.defaultEndpoint),
        env("OPENAI_MODEL").getOrElse(LlmConfiguration.defaultModel),
        env("OPENAI_PROGRAMMING_HINT_MODEL").getOrElse("gpt-5"), env("OPENAI_API_KEY"), integer("OPENAI_MAX_TOKENS")),
      archiveRecipient = env("SUBMISSION_ARCHIVE_MAIL").getOrElse("andre.greubel@hu-berlin.de"))
  }

  /** Exact browser origins (comma-separated) that may call the server directly, for a local client against a local
    * backend. Leave it unset behind the deployment's Caddy proxy, which sets CORS headers itself.
    */
  private[server] def allowedOriginsFrom(value: Option[String]): Set[String] =
    value.toSeq.flatMap(_.split(",")).map(_.trim.stripSuffix("/")).filter(_.nonEmpty).toSet
}
