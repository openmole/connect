import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

import scala.collection.mutable

val scalatraVersion = "2.6.5"
val jettyVersion = "9.4.19.v20190610"
val json4sVersion = "3.6.7"
val scalatagsVersion = "0.7.0"
val scaladgetVersion = "1.2.7"
val scalajsDomVersion = "0.9.7"
val scalaJWTVersion = "3.1.0"
val rosHttpVersion = "2.2.4"

val Resolvers = Seq(Resolver.sonatypeRepo("snapshots"),
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.bintrayRepo("hmil", "maven")
)

lazy val defaultSettings = Seq(
  organization := "openmole.org",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.12.8",
  resolvers := Resolvers
)

lazy val shared = project.in(file("shared")) settings (defaultSettings: _*) enablePlugins (ScalaJSPlugin)

lazy val go = taskKey[Unit]("go")

lazy val client = project.in(file("client")) enablePlugins (ExecNpmPlugin) settings (defaultSettings) settings(
  skip in packageJSDependencies := false,
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalatags" % scalatagsVersion,
    "fr.iscpif.scaladget" %%% "tools" % scaladgetVersion,
    "fr.iscpif.scaladget" %%% "bootstrapnative" % scaladgetVersion,
    "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion
  )
) dependsOn (shared)

lazy val server = project.in(file("server")) settings (defaultSettings) settings (
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "scalatags" % scalatagsVersion,
    "org.scalatra" %% "scalatra" % scalatraVersion,
    "org.eclipse.jetty" % "jetty-webapp" % jettyVersion,
    "org.eclipse.jetty" % "jetty-server" % jettyVersion,
    "org.json4s" %% "json4s-jackson" % json4sVersion,
    "com.pauldijou" %% "jwt-core" % scalaJWTVersion,
    "fr.hmil" %% "roshttp" % rosHttpVersion
  )
  ) dependsOn (shared) enablePlugins (ScalatraPlugin)

lazy val application = project.in(file("application")) settings (defaultSettings) dependsOn (server) enablePlugins(JavaServerAppPackaging) settings(
  dockerCommands := Seq(
    Cmd("FROM", "gafiatulin/alpine-sbt as simop"),
    Cmd("RUN", "apk update && apk add bash git sudo nodejs-npm"),
    Cmd("RUN", "adduser connect -g \"\" -D -h /var/connect/"),
    Cmd("USER", "connect"),
    Cmd("RUN", "cd /var/connect && git clone https://gitlab.openmole.org/openmole/openmole-connect && cd openmole-connect && sbt go")
  ) ++ dockerCommands.value.dropRight(3) ++ Seq(
    Cmd("COPY", "--from=simop", "--chown=root:root", "/var/connect/openmole-connect/application/target/webapp", "/opt/docker/application/target/webapp"),
    Cmd("USER", "1001:0"),
    ExecCmd("ENTRYPOINT", "/opt/docker/bin/application")
  ),
  packageName in Docker := "openmole-connect"
)

lazy val bootstrap = project.in(file("target/bootstrap")) settings (defaultSettings) settings (
  go := {

    val jsBuild = (fullOptJS in client in Compile).value.data
    val appTarget = (target in application in Compile).value


    val clientResources = (resourceDirectory in client in Compile).value
    val dependencyJS = (dependencyFile in client in Compile).value
    val depsCSS = (cssFile in client in Compile).value

    IO.copyFile(jsBuild, appTarget / "webapp/js/connect.js")
    IO.copyFile(dependencyJS, appTarget / "webapp/js/connect-deps.js")
    IO.copyDirectory(depsCSS, appTarget / "webapp/css")
    IO.copyDirectory(clientResources, appTarget)
  }) dependsOn(client, server)
