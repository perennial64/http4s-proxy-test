import scala.scalanative.build.Mode

val scala3Version = "3.3.8"

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
            "org.scalameta" %%% "munit" % "1.3.6",
            "org.http4s" %%% "http4s-ember-client" % "0.23.38-101-c15e49d-SNAPSHOT"
        ),
        testFrameworks += new TestFramework("munit.Framework")
    )
    .jsSettings(
        scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
    )
    .nativeSettings(
        nativeConfig ~= (_.withMode(Mode.debug))
    )

