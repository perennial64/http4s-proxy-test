import org.scalajs.jsenv.nodejs.NodeJSEnv
import scala.scalanative.build.Mode

val scala3Version = "3.3.8"
val testCertificate = file("certs/server.crt").getAbsolutePath
val testTrustStore = file("certs/truststore.p12").getAbsolutePath

ThisBuild / scalaVersion := scala3Version
ThisBuild / organization := "xyz.perennial"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .in(file("."))
    .settings(
        name := "http4s-proxy-test",
        resolvers += Resolver.mavenLocal,
        libraryDependencies ++= Seq(
            "org.http4s" %%% "http4s-ember-client" % "0.23.38-101-c15e49d-SNAPSHOT",
            "org.typelevel" %%% "munit-cats-effect" % "2.2.1" % Test
        ),
        testFrameworks += new TestFramework("munit.Framework")
    )
    .jvmSettings(
        Test / fork := true,
        Test / javaOptions ++= Seq(
            s"-Djavax.net.ssl.trustStore=$testTrustStore",
            "-Djavax.net.ssl.trustStorePassword=changeit"
        )
    )
    .jsSettings(
        scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
        Test / jsEnv := new NodeJSEnv(
            NodeJSEnv.Config().withEnv(Map("NODE_EXTRA_CA_CERTS" -> testCertificate))
        )
    )
    .nativeSettings(
        nativeConfig ~= (_.withMode(Mode.debug)),
        Test / envVars += "SSL_CERT_FILE" -> testCertificate
    )

