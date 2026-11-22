package it.evadid.server

import jakarta.mail.{AuthenticationFailedException, MessagingException, SendFailedException}
import java.net.{ConnectException, SocketTimeoutException, UnknownHostException}
import java.net.http.HttpTimeoutException
import java.io.IOException
import javax.net.ssl.SSLException
import munit.FunSuite

class ExternalServiceFailureSpec extends FunSuite {
  private val secret = "private-service-credentials"

  List(
    AuthenticationFailedException(secret) -> "MAIL_PW",
    MessagingException(secret, SocketTimeoutException(secret)) -> "timed out",
    MessagingException(secret, ConnectException(secret)) -> "SMTP connection failed",
    MessagingException(secret, UnknownHostException(secret)) -> "DNS",
    MessagingException(secret, SSLException(secret)) -> "TLS",
    SendFailedException(secret) -> "rejected delivery"
  ).foreach { (failure, expected) =>
    test(s"mail delivery explains $expected without echoing message or credentials") {
      val error = intercept[IllegalStateException] { SendMailCommand.withDeliveryContext { throw failure } }
      assert(error.getMessage.contains(expected))
      assert(!error.getMessage.contains(secret))
      assertEquals(error.getCause, null)
    }
  }

  List(HttpTimeoutException(secret) -> "timed out", IOException(secret) -> "network access",
    SSLException(secret) -> "TLS").foreach { (failure, expected) =>
    test(s"LLM transport explains $expected without leaking endpoint or credentials") {
      val error = intercept[IllegalStateException] { CompleteChatWithLLMCommand.withRequestContext { throw failure } }
      assert(error.getMessage.contains(expected))
      assert(!error.getMessage.contains(secret))
      assertEquals(error.getCause, null)
    }
  }

  List(401 -> "API key", 403 -> "permissions", 404 -> "endpoint or model", 429 -> "quota", 503 -> "retry later").foreach { (status, expected) =>
    test(s"LLM HTTP $status explains the provider rejection without retaining its body") {
      val error = intercept[IllegalStateException] { CompleteChatWithLLMCommand.decodeResponse(status, secret) }
      assert(error.getMessage.contains(status.toString))
      assert(error.getMessage.contains(expected))
      assert(!error.getMessage.contains(secret))
    }
  }

  test("LLM success responses with incompatible payloads identify the response contract safely") {
    List(secret -> "invalid JSON", s"""{"private":"$secret"}""" -> "choices[0].message.content").foreach { (body, expected) =>
      val error = intercept[IllegalStateException] { CompleteChatWithLLMCommand.decodeResponse(200, body) }
      assert(error.getMessage.contains(expected))
      assert(!error.getMessage.contains(secret))
      assertEquals(error.getCause, null)
    }
  }
}
