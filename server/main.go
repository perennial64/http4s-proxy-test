package main

import (
	"bytes"
	"context"
	"crypto/tls"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httputil"
	"time"

	corechain "github.com/go-gost/core/chain"
	corehandler "github.com/go-gost/core/handler"
	corelistener "github.com/go-gost/core/listener"
	corelogger "github.com/go-gost/core/logger"
	coreservice "github.com/go-gost/core/service"
	xauth "github.com/go-gost/x/auth"
	"github.com/go-gost/x/chain"
	xhandlerhttp "github.com/go-gost/x/handler/http"
	xhandlerhttp2 "github.com/go-gost/x/handler/http2"
	socks4 "github.com/go-gost/x/handler/socks/v4"
	socks5 "github.com/go-gost/x/handler/socks/v5"
	xlistenerhttp2 "github.com/go-gost/x/listener/http2"
	xlistenertcp "github.com/go-gost/x/listener/tcp"
	xlistenertls "github.com/go-gost/x/listener/tls"
	xlogger "github.com/go-gost/x/logger"
	xmetadata "github.com/go-gost/x/metadata"
	xservice "github.com/go-gost/x/service"
	"golang.org/x/net/http2"
)

const (
	httpAddr       = "127.0.0.1:8080"
	httpAuthAddr   = "127.0.0.1:8081"
	httpsAddr      = "127.0.0.1:8443"
	httpsAuthAddr  = "127.0.0.1:8444"
	http2Addr      = "127.0.0.1:8445"
	http2AuthAddr  = "127.0.0.1:8446"
	socks5Addr     = "127.0.0.1:1080"
	socks5AuthAddr = "127.0.0.1:1081"
	socks4Addr     = "127.0.0.1:1082"
	socks4AuthAddr = "127.0.0.1:1083"
	backendAddr    = "127.0.0.1:9000"
	backendTLSAddr = "127.0.0.1:9443"

	proxyUser = "testuser"
	proxyPass = "testpass"

	certFile = "./certs/server.crt"
	keyFile  = "./certs/server.key"
)

// proxyMarker is written as the first bytes of every connection the proxies
// dial out.
var proxyMarker = []byte("PROXYMARKER\n")

type markingRouter struct {
	corechain.Router
}

func (r markingRouter) Dial(ctx context.Context, network, address string, opts ...corechain.DialOption) (net.Conn, error) {
	conn, err := r.Router.Dial(ctx, network, address, opts...)
	if err != nil {
		return nil, err
	}
	if _, err := conn.Write(proxyMarker); err != nil {
		conn.Close()
		return nil, err
	}
	return conn, nil
}

func main() {
	log := xlogger.NewLogger(xlogger.LevelOption(corelogger.InfoLevel))

	auther := corehandler.AutherOption(xauth.NewAuthenticator(xauth.AuthsOption(map[string]string{proxyUser: proxyPass})))
	socks4Auther := corehandler.AutherOption(xauth.NewAuthenticator(xauth.AuthsOption(map[string]string{proxyUser: ""})))

	router := markingRouter{chain.NewRouter(corechain.LoggerRouterOption(log))}

	tlsConfig, err := loadTLSConfig(certFile, keyFile)
	if err != nil {
		log.Fatal(err)
	}

	if err := startBackends(log, backendAddr, backendTLSAddr, tlsConfig); err != nil {
		log.Fatal(err)
	}

	var services []proxyService

	mustInit := func(name, addr string, tlsWrap bool, h corehandler.Handler) {
		var ln corelistener.Listener
		if tlsWrap {
			ln = xlistenertls.NewListener(
				corelistener.AddrOption(addr),
				corelistener.TLSConfigOption(tlsConfig),
				corelistener.LoggerOption(log),
			)
		} else {
			ln = xlistenertcp.NewListener(
				corelistener.AddrOption(addr),
				corelistener.LoggerOption(log),
			)
		}
		if err := ln.Init(xmetadata.NewMetadata(nil)); err != nil {
			log.Fatalf("%s: init listener: %v", name, err)
		}
		if err := h.Init(xmetadata.NewMetadata(nil)); err != nil {
			log.Fatalf("%s: init handler: %v", name, err)
		}
		services = append(services, proxyService{name, ln, h, addr})
	}

	baseOpts := []corehandler.Option{corehandler.RouterOption(router), corehandler.LoggerOption(log)}

	mustInit("http", httpAddr, false, xhandlerhttp.NewHandler(baseOpts...))
	mustInit("http-auth", httpAuthAddr, false, xhandlerhttp.NewHandler(append(baseOpts, auther)...))

	mustInit("https", httpsAddr, true, xhandlerhttp.NewHandler(baseOpts...))
	mustInit("https-auth", httpsAuthAddr, true, xhandlerhttp.NewHandler(append(baseOpts, auther)...))

	mustInitHTTP2 := func(name, addr string, h corehandler.Handler) {
		config := tlsConfig.Clone()
		config.NextProtos = []string{"h2"}
		ln := xlistenerhttp2.NewListener(
			corelistener.AddrOption(addr),
			corelistener.TLSConfigOption(config),
			corelistener.LoggerOption(log),
		)
		if err := ln.Init(xmetadata.NewMetadata(nil)); err != nil {
			log.Fatalf("%s: init listener: %v", name, err)
		}
		if err := h.Init(xmetadata.NewMetadata(nil)); err != nil {
			log.Fatalf("%s: init handler: %v", name, err)
		}
		services = append(services, proxyService{name, ln, h, addr})
	}

	mustInitHTTP2("http2", http2Addr, xhandlerhttp2.NewHandler(baseOpts...))
	mustInitHTTP2("http2-auth", http2AuthAddr, xhandlerhttp2.NewHandler(append(baseOpts, auther)...))

	mustInit("socks5", socks5Addr, false, socks5.NewHandler(baseOpts...))
	mustInit("socks5-auth", socks5AuthAddr, false, socks5.NewHandler(append(baseOpts, auther)...))

	mustInit("socks4", socks4Addr, false, socks4.NewHandler(baseOpts...))
	mustInit("socks4-auth", socks4AuthAddr, false, socks4.NewHandler(append(baseOpts, socks4Auther)...))

	for _, svc := range services {
		s := xservice.NewService(svc.name, svc.ln, svc.h, xservice.LoggerOption(log))
		go func(name string, s coreservice.Service) {
			log.Fatalf("%s: %v", name, s.Serve())
		}(svc.name, s)
		log.Infof("%s listening on %s", svc.name, svc.addr)
	}

	select {}
}

type viaProxyKey struct{}

type markedConn struct {
	net.Conn
	leftover []byte
}

func (c *markedConn) Read(p []byte) (int, error) {
	if len(c.leftover) > 0 {
		n := copy(p, c.leftover)
		c.leftover = c.leftover[n:]
		return n, nil
	}
	return c.Conn.Read(p)
}

type markerListener struct {
	net.Listener
}

func (l *markerListener) Accept() (net.Conn, error) {
	conn, err := l.Listener.Accept()
	if err != nil {
		return nil, err
	}
	conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	prefix := make([]byte, len(proxyMarker))
	n, _ := io.ReadFull(conn, prefix)
	conn.SetReadDeadline(time.Time{})

	mc := &markedConn{Conn: conn}
	viaProxy := n == len(prefix) && bytes.Equal(prefix, proxyMarker)
	if !viaProxy {
		mc.leftover = prefix[:n]
	}
	return &taggedConn{markedConn: mc, viaProxy: viaProxy}, nil
}

type taggedConn struct {
	*markedConn
	viaProxy bool
}

// startBackends runs plain HTTP and TLS HTTP/1.1+HTTP/2 servers that report
// whether the connection carried proxyMarker, then echo the complete request.
func startBackends(log corelogger.Logger, addr, tlsAddr string, tlsConfig *tls.Config) error {
	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		request, err := httputil.DumpRequest(r, true)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		result := "no proxy"
		if viaProxy, _ := r.Context().Value(viaProxyKey{}).(bool); viaProxy {
			result = "with proxy"
		}
		fmt.Fprintf(w, "%s\n%s", result, request)
	})

	start := func(addr string, config *tls.Config) error {
		ln, err := net.Listen("tcp", addr)
		if err != nil {
			return err
		}
		var listener net.Listener = &markerListener{ln}
		if config != nil {
			listener = tls.NewListener(listener, config)
		}

		srv := &http.Server{
			Handler: handler,
			ConnContext: func(ctx context.Context, c net.Conn) context.Context {
				if tlsConn, ok := c.(*tls.Conn); ok {
					c = tlsConn.NetConn()
				}
				if tc, ok := c.(*taggedConn); ok {
					return context.WithValue(ctx, viaProxyKey{}, tc.viaProxy)
				}
				return ctx
			},
		}
		if config != nil {
			if err := http2.ConfigureServer(srv, nil); err != nil {
				listener.Close()
				return err
			}
		}
		go func() {
			log.Fatalf("backend %s: %v", addr, srv.Serve(listener))
		}()
		log.Infof("backend listening on %s", addr)
		return nil
	}

	if err := start(addr, nil); err != nil {
		return err
	}

	config := tlsConfig.Clone()
	config.NextProtos = []string{"h2", "http/1.1"}
	return start(tlsAddr, config)
}

type proxyService struct {
	name string
	ln   corelistener.Listener
	h    corehandler.Handler
	addr string
}

func loadTLSConfig(certFile, keyFile string) (*tls.Config, error) {
	cert, err := tls.LoadX509KeyPair(certFile, keyFile)
	if err != nil {
		return nil, fmt.Errorf("load %s / %s: %w", certFile, keyFile, err)
	}
	return &tls.Config{Certificates: []tls.Certificate{cert}}, nil
}

