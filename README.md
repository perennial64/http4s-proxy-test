# http4s proxy tests

Cross-platform test suite for proxy support in the http4s ember client, run against go server
(https://github.com/go-gost/gost) as established HTTP, HTTPS, SOCKS4 and SOCKS5 proxy provider.

The tests live here rather than in http4s because there is no Scala proxy server to test against, and external server
does not fit cleanly into the ember client's own test suite.

Until proxy support is merged, https://github.com/perennial64/http4s.git proxy-support branch artifact is expected to
be present in maven local.

- `./test.sh` — builds the server and the tests, starts the server, and runs the suite on all platforms. Should work
out of the box on traditional systems.
- `./flake.nix` — a dev shell and a `.#test` target, which run the suite in a deterministic environment (native
libraries for native platform).

