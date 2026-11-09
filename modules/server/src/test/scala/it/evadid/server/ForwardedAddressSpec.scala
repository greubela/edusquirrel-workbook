package it.evadid.server

import munit.FunSuite
import play.api.Mode
import play.api.mvc.Results.*
import play.core.server.ServerConfig
import java.net.{URI, InetAddress}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.Properties

class ForwardedAddressSpec extends FunSuite {
  private def observedAddress(trusted: String, forwarded: String): String = {
    val properties = new Properties()
    // Use the same indexed property format as deployment/server/docker-compose.yml.
    properties.setProperty("play.http.forwarded.version", "x-forwarded")
    properties.setProperty("play.http.forwarded.trustedProxies.0", trusted)
    val config = ServerConfig(port = Some(0), address = "127.0.0.1", mode = Mode.Test, properties = properties)
    val server = BackendServer.createServer(config) { action =>
      { case _ => action { request =>
        Ok(request.connection.remoteAddress.getHostAddress)
      }}
    }
    try {
      val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:${server.httpPort.get}/"))
        .header("X-Forwarded-For", forwarded).GET().build()
      val client = HttpClient.newHttpClient()
      try client.send(request, HttpResponse.BodyHandlers.ofString()).body()
      finally client.close()
    } finally server.stop()
  }

  test("trusted proxy forwarding supplies the IPv6 address used by the command logger") {
    val address = "2001:db8::42"
    assertEquals(observedAddress("127.0.0.1", address), InetAddress.getByName(address).getHostAddress)
  }

  test("an untrusted connection cannot spoof the logged client address") {
    assertEquals(observedAddress("192.0.2.1", "198.51.100.7"), "127.0.0.1")
  }
}
