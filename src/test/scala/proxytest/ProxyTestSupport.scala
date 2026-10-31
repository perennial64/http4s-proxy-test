package proxytest

import cats.effect.IO
import com.comcast.ip4s.Host
import fs2.io.net.Network
import munit.CatsEffectSuite
import org.http4s.ember.client.*
import org.http4s.syntax.literals.uri
import org.http4s.{BasicCredentials, Header, Headers, Method, Request, Uri}
import org.typelevel.ci.CIString

trait ProxyTestSupport:
    self: CatsEffectSuite =>

    protected val h1Backend = uri"http://127.0.0.1:9000/"
    protected val h2Backend = uri"https://127.0.0.1:9443/"

    protected def request(
        proxy: Option[Proxy],
        method: Method,
        payload: Option[String],
        headers: Headers,
        http2: Boolean
    ): IO[String] =
        val uri = if http2 then h2Backend else h1Backend
        val base = Request[IO](method = method, uri = uri, headers = headers)
        val outgoing = payload.fold(base)(base.withEntity)
        val default = EmberClientBuilder.default[IO].withoutProxyIgnoredHosts
        val builder = proxy.fold(default)(default.withProxy)
        (if http2 then builder.withHttp2 else builder).build.use(_.expect[String](outgoing))

    protected def assertRequestThrough(
        proxy: Proxy,
        method: Method,
        payload: Option[String],
        headers: Headers,
        http2: Boolean
    ): IO[Unit] =
        assertRequestEcho("with proxy", Some(proxy), method, payload, headers, http2)

    protected def assertDirectRequest(
        method: Method,
        payload: Option[String],
        headers: Headers,
        http2: Boolean
    ): IO[Unit] =
        assertRequestEcho("no proxy", None, method, payload, headers, http2)

    private def assertRequestEcho(
        route: String,
        proxy: Option[Proxy],
        method: Method,
        payload: Option[String],
        headers: Headers,
        http2: Boolean
    ): IO[Unit] =
        request(proxy, method, payload, headers, http2).map: response =>
            val protocol = if http2 then "HTTP/2.0" else "HTTP/1.1"

            assert(response.startsWith(s"$route\n${method.name} / $protocol\r\n"), response)
            payload.foreach(value => assert(response.endsWith(s"\r\n\r\n$value"), response))
            headers.foreach: h =>
                assert(response.contains(s"\r\n${h.name}: ${h.value}\r\n"), response)

    protected def assertRejected(response: IO[String]): IO[Unit] =
        response.attempt.map(result => assert(result.isLeft))

