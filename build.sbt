import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import com.typesafe.sbt.packager.docker.*
import NativePackagerHelper.*
import com.typesafe.sbt.packager.linux.LinuxPlugin.autoImport.daemonUser

import java.io.File
import scala.collection.mutable

val scalatraVersion = "2.7.0"
val jettyVersion = "9.4.28.v20200408"
val json4sVersion = "4.0.7"
val scalatagsVersion = "0.12.0"
val scaladgetVersion = "1.10.0"
val scalajsDomVersion = "1.10.0"
val scalaJWTVersion = "4.2.0"
val rosHttpVersion = "3.0.0"
val httpComponentsVersion = "4.5.12"
val autowireVersion = "0.3.3"
val boopickleVersion = "1.4.0"
def laminarVersion = "0.14.2"

def circeVersion = "0.14.15"
def http4sVersion = "0.23.16"
def tapirVersion = "1.11.50"

val Resolvers = Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases"),
  Resolver.bintrayRepo("hmil", "maven")
)

lazy val defaultSettings = Seq(
  organization := "org.openmole",
  scalaVersion := "3.7.3",
  resolvers := Resolvers,
  scalacOptions ++= Seq("-Xmax-inlines:100")
)

lazy val shared = project.in(file("shared")) settings (defaultSettings) enablePlugins (ScalaJSPlugin) settings(
  libraryDependencies ++= Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-server" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
    "io.circe" %% "circe-generic" % circeVersion
  )
)

lazy val go = taskKey[Unit]("go")

lazy val client = project.in(file("client")) enablePlugins (ExecNpmPlugin) settings (defaultSettings) settings(
  skip in packageJSDependencies := false,
  libraryDependencies ++= Seq(
    "org.openmole.scaladget" %%% "tools" % scaladgetVersion,
    "org.openmole.scaladget" %%% "bootstrapnative" % scaladgetVersion,
    "com.raquo" %%% "laminar" % laminarVersion,
    "com.softwaremill.sttp.tapir" %%% "tapir-sttp-client4" % tapirVersion,
    "com.softwaremill.sttp.tapir" %%% "tapir-json-circe" % tapirVersion,
    "com.lihaoyi" %%% "upickle" % "4.1.0"
  )
) dependsOn (shared)

lazy val server = project.in(file("server")) settings (defaultSettings) settings (
  //Compile / doc := new java.io.File(""),
  libraryDependencies ++= Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion,
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.typelevel" %% "cats-effect" % "3.6.2",
    "io.circe" %% "circe-parser" % circeVersion,
    "io.circe" %% "circe-yaml" % "1.15.0",
    "com.lihaoyi" %% "scalatags" % scalatagsVersion,
    "org.json4s" %% "json4s-jackson" % json4sVersion,
    "org.apache.httpcomponents.client5" % "httpclient5" % "5.5",
    "com.lihaoyi" %% "upickle" % "4.2.1",
    "org.apache.httpcomponents.client5" % "httpclient5" % "5.5",
    "com.typesafe.slick" %% "slick" % "3.6.1",
    "com.h2database" % "h2" % "2.3.232",
    "dev.profunktor" %% "http4s-jwt-auth" % "2.0.9",
    "com.github.pathikrit" %% "better-files" % "3.9.2",
    "commons-codec" % "commons-codec" % "1.19.0",
    "org.apache.commons" % "commons-lang3" % "3.18.0",
    "io.github.arainko" %% "ducktape" % "0.2.9",
    "io.kubernetes" % "client-java" % "24.0.0",
    "dev.optics" %% "monocle-core"  % "3.3.0",
    "dev.optics" %% "monocle-macro" % "3.3.0",
    "com.google.guava" % "guava" % "33.4.8-jre",
    "com.github.eikek" %% "emil-common" % "0.19.0",  // the core library
    "com.github.eikek" %% "emil-javamail" % "0.19.0", // implementation module
    "org.slf4j" % "slf4j-jdk14" % "2.0.17"

  )
) dependsOn (shared) 

//lazy val application = project.in(file("application")) settings (defaultSettings) dependsOn(server) enablePlugins (JavaServerAppPackaging)


val prefix = "/opt/docker/application/"
lazy val application = project.in(file("application")) settings (defaultSettings) dependsOn(server) enablePlugins (JavaServerAppPackaging) settings(
//  daemonUserUid in Docker := None,
//  daemonUser in Docker    := "openmoleconnect",
//  dockerChmodType := DockerChmodType.UserGroupWriteExecute,
//  dockerAdditionalPermissions += (DockerChmodType.UserGroupPlusExecute, "/opt/docker/bin/application"),
//  dockerAdditionalPermissions += (DockerChmodType.UserGroupWriteExecute, "/home/demiourgos728/.openmole-connect"),
  libraryDependencies += "com.github.scopt" %% "scopt" % "4.1.0",
  Docker / mappings ++=
    Seq(
      (dependencyFile in client in Compile).value -> s"$prefix/webapp/js/connect-deps.js",
      (fullOptJS in client in Compile).value.data -> s"$prefix/webapp/js/connect.js"
    ) ++ doMapping((resourceDirectory in client in Compile).value, prefix)
      ++ doMapping((cssFile in client in target).value, s"$prefix/webapp/css/")
      ++ doMapping((resourceDirectory in client in Compile).value / "webapp" / "fonts", s"$prefix/webapp/fonts/"),
  Docker / packageName := "openmole/openmole-connect",
  Docker / organization := "openmole",
  dockerBaseImage := "openjdk:25-slim"
)

def doMapping(from: java.io.File, toBase: String): Seq[(File, String)] = {
  val fromPath = s"${from.getAbsolutePath}"
  val list =
    recursiveFileList(from).toSeq.filterNot { _.getName == "webapp" }
      .map { f => f -> s"$toBase${f.getAbsolutePath diff fromPath}" }

  list
}

def recursiveFileList(dir: File): Array[File] = {
  if (dir.exists()) {
    val these = dir.listFiles
    these ++ these.filter(_.isDirectory).flatMap(recursiveFileList)
  } else Array()
}
