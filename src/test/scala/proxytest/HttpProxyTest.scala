package proxytest

import com.comcast.ip4s.*
import munit.FunSuite
import org.http4s.ember.client.HttpProxy
import org.http4s.{BasicCredentials, Headers, Method, Uri}

class HttpProxySuite extends FunSuite with ProxyTestSupport:
    val credentials = BasicCredentials("testuser", "testpass")
    val invalidCredentials = BasicCredentials("wrong-user", "wrong-password")

    test("HTTP/1.1 request without auth"):
        val proxy = HttpProxy(Uri.Scheme.http, host"127.0.0.1", Some(port"8080"))
        assertRequestThrough(proxy, Method.GET, payload = None, headers = Headers(), http2 = false)

    test("HTTP/2 request without auth"):
        val proxy = HttpProxy(Uri.Scheme.http, host"127.0.0.1", Some(port"8080"))
        assertRequestThrough(proxy, Method.GET, payload = None, headers = Headers(), http2 = true)

    test("HTTP/1.1 request with auth"):
        val proxy = HttpProxy(Uri.Scheme.http, host"127.0.0.1", Some(port"8081"), Some(credentials))
        assertRequestThrough(proxy, Method.GET, payload = None, headers = Headers(), http2 = false)

    test("HTTP/2 request with auth"):
        val proxy = HttpProxy(Uri.Scheme.http, host"127.0.0.1", Some(port"8081"), Some(credentials))
        assertRequestThrough(proxy, Method.GET, payload = None, headers = Headers(), http2 = true)

    test("HTTP/1.1 request forwards headers and payload"):
        val proxy = HttpProxy(Uri.Scheme.http, host"127.0.0.1", Some(port"8080"))
        assertRequestThrough(
            proxy,
            Method.POST,
            payload = Some("http payload"),
            Headers("X-Echo-Test" -> "http header"),
            http2 = false
        )

    test("HTTP/2 request forwards headers and payload"):
        val proxy = HttpProxy(Uri.Scheme.http, host"127.0.0.1", Some(port"8080"))
        assertRequestThrough(
            proxy,
            Method.POST,
            payload = Some("http HTTP/2 payload"),
            Headers("X-Echo-Test" -> "http HTTP/2 header"),
            http2 = true
        )

    test("rejects invalid credentials"):
        val proxy = HttpProxy(Uri.Scheme.http, host"127.0.0.1", Some(port"8081"), Some(invalidCredentials))
        assertRejected(request(Some(proxy), Method.GET, payload = None, headers = Headers(), http2 = false))

