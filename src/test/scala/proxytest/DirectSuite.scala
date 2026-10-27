package proxytest

import munit.FunSuite
import org.http4s.{Headers, Method}

class DirectSuite extends FunSuite with ProxyTestSupport:
    test("direct HTTP/1.1 request completes and does not use a proxy"):
        assertDirectRequest(Method.GET, payload = None, headers = Headers(), http2 = false)

    test("direct http/2 request completes and does not use a proxy"):
        assertDirectRequest(Method.GET, payload = None, headers = Headers(), http2 = true)

    test("direct HTTP/1.1 request echoes headers and payload"):
        assertDirectRequest(
            Method.POST,
            payload = Some("direct payload"),
            Headers("X-Echo-Test" -> "direct header"),
            http2 = false
        )

    test("direct HTTP/2 request echoes headers and payload"):
        assertDirectRequest(
            Method.POST,
            payload = Some("direct HTTP/2 payload"),
            Headers("X-Echo-Test" -> "direct HTTP/2 header"),
            http2 = true
        )

