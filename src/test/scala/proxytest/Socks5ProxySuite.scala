package proxytest

import com.comcast.ip4s.*
import munit.FunSuite
import org.http4s.ember.client.{Socks5Credentials, Socks5Proxy}
import org.http4s.{Headers, Method}

class Socks5ProxySuite extends FunSuite with ProxyTestSupport:
    val credentials = Socks5Credentials("testuser", "testpass")
    val invalidCredentials = Socks5Credentials("wrong-user", "wrong-password")

    test("HTTP/1.1 request without auth"):
        val proxy = Socks5Proxy(host"127.0.0.1", port"1080")
        assertRequestThrough(proxy, Method.GET, payload = None, headers = Headers(), http2 = false)

    test("HTTP/2 request without auth"):
        val proxy = Socks5Proxy(host"127.0.0.1", port"1080")
        assertRequestThrough(proxy, Method.GET, payload = None, headers = Headers(), http2 = true)

    test("HTTP/1.1 request with auth"):
        val proxy = Socks5Proxy(host"127.0.0.1", port"1081", Some(credentials))
        assertRequestThrough(proxy, Method.GET, payload = None, headers = Headers(), http2 = false)

    test("HTTP/2 request with auth"):
        val proxy = Socks5Proxy(host"127.0.0.1", port"1081", Some(credentials))
        assertRequestThrough(proxy, Method.GET, payload = None, headers = Headers(), http2 = true)

    test("HTTP/1.1 request forwards headers and payload"):
        val proxy = Socks5Proxy(host"127.0.0.1", port"1080")
        assertRequestThrough(
            proxy,
            Method.POST,
            payload = Some("socks5 payload"),
            Headers("X-Echo-Test" -> "socks5 header"),
            http2 = false
        )

    test("HTTP/2 request forwards headers and payload"):
        val proxy = Socks5Proxy(host"127.0.0.1", port"1080")
        assertRequestThrough(
            proxy,
            Method.POST,
            payload = Some("socks5 HTTP/2 payload"),
            Headers("X-Echo-Test" -> "socks5 HTTP/2 header"),
            http2 = true
        )

    test("rejects invalid credentials"):
        val proxy = Socks5Proxy(host"127.0.0.1", port"1081", Some(invalidCredentials))
        assertRejected(request(Some(proxy), Method.GET, payload = None, headers = Headers(), http2 = false))

