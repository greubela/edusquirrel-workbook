package it.evadid.server

import munit.{AnyFixture, FunSuite}
import play.api.Mode
import play.api.mvc.Results.*
import play.core.server.{Server, ServerConfig}
import java.net.{URI, InetAddress}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.Properties

class ForwardedAddressSpec extends FunSuite {
  private val trustedProxies = List("127.0.0.1", "192.0.2.1")

  private def echoServer(trusted: String): Server = {
    val properties = new Properties()
    // Use the same indexed property format as deployment/server/docker-compose.yml.
    properties.setProperty("play.http.forwarded.version", "x-forwarded")
    properties.setProperty("play.http.forwarded.trustedProxies.0", trusted)
    val config = ServerConfig(port = Some(0), address = "127.0.0.1", mode = Mode.Test, properties = properties)
    BackendServer.createServer(config) { (action, _) =>
      { case _ => action { request =>
        Ok(request.connection.remoteAddress.getHostAddress)
      }}
    }
  }

  /** One stateless echo server per trusted-proxy configuration, shared by the suite. */
  private val servers = new Fixture[Map[String, Server]]("forwarding servers") {
    private var running = Map.empty[String, Server]
    def apply(): Map[String, Server] = running
    override def beforeAll(): Unit = running = trustedProxies.map(trusted => trusted -> echoServer(trusted)).toMap
    override def afterAll(): Unit = running.values.foreach(_.stop())
  }
  override def munitFixtures: Seq[AnyFixture[?]] = List(servers)

  private def observedAddress(trusted: String, forwarded: String): String = {
    val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:${servers()(trusted).httpPort.get}/"))
      .header("X-Forwarded-For", forwarded).GET().build()
    val client = HttpClient.newHttpClient()
    try client.send(request, HttpResponse.BodyHandlers.ofString()).body()
    finally client.close()
  }

  test("trusted proxy forwarding supplies the IPv6 address used by the command logger") {
    val address = "2001:db8::42"
    assertEquals(observedAddress("127.0.0.1", address), InetAddress.getByName(address).getHostAddress)
  }

  test("forwarding chains stop at the nearest untrusted client rather than accepting a spoofed prefix") {
    assertEquals(observedAddress("127.0.0.1", "198.51.100.99, 198.51.100.42"), "198.51.100.42")
  }

  test("an untrusted connection cannot spoof the logged client address") {
    assertEquals(observedAddress("192.0.2.1", "198.51.100.7"), "127.0.0.1")
  }
}
