package proxytest

import com.comcast.ip4s.*
import munit.FunSuite
import org.http4s.ember.client.Socks4Proxy
import org.http4s.{Headers, Method}

class Socks4ProxySuite extends FunSuite with ProxyTestSupport:
    test("HTTP/1.1 request without auth"):
        val proxy = Socks4Proxy(host"127.0.0.1", port"1082")
        assertRequestThrough(proxy, Method.GET, payload = None, headers = Headers(), http2 = false)

    test("HTTP/2 request without auth"):
        val proxy = Socks4Proxy(host"127.0.0.1", port"1082")
        assertRequestThrough(proxy, Method.GET, payload = None, headers = Headers(), http2 = true)

    test("HTTP/1.1 request with auth (userid)"):
        val proxy = Socks4Proxy(host"127.0.0.1", port"1083", Some("testuser"))
        assertRequestThrough(proxy, Method.GET, payload = None, headers = Headers(), http2 = false)

    test("HTTP/2 request with auth (userid)"):
        val proxy = Socks4Proxy(host"127.0.0.1", port"1083", Some("testuser"))
        assertRequestThrough(proxy, Method.GET, payload = None, headers = Headers(), http2 = true)

    test("HTTP/1.1 request forwards headers and payload"):
        val proxy = Socks4Proxy(host"127.0.0.1", port"1082")
        assertRequestThrough(
            proxy,
            Method.PATCH,
            payload = Some("socks4 payload"),
            Headers("X-Echo-Test" -> "socks4 header"),
            http2 = false
        )

    test("HTTP/2 request forwards headers and payload"):
        val proxy = Socks4Proxy(host"127.0.0.1", port"1082")
        assertRequestThrough(
            proxy,
            Method.PATCH,
            payload = Some("socks4 HTTP/2 payload"),
            Headers("X-Echo-Test" -> "socks4 HTTP/2 header"),
            http2 = true
        )

    test("rejects invalid userid"):
        val proxy = Socks4Proxy(host"127.0.0.1", port"1083", Some("wrong-user"))
        assertRejected(request(Some(proxy), Method.GET, payload = None, headers = Headers(), http2 = false))

