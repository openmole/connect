import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import com.typesafe.sbt.packager.docker._
import NativePackagerHelper._

import scala.collection.mutable

val scalatraVersion = "2.7.0"
val jettyVersion = "9.4.28.v20200408"
val json4sVersion = "3.6.8"
val scalatagsVersion = "0.9.1"
val scaladgetVersion = "1.3.3"
val scalajsDomVersion = "1.0.0"
val scalaJWTVersion = "4.2.0"
val rosHttpVersion = "3.0.0"
val skuberVersion = "2.4.0"
val httpComponentsVersion = "4.5.12"
val slickVersion = "3.3.2"
val h2Version = "1.4.200"
val autowireVersion = "0.3.2"
val boopickleVersion = "1.3.3"

val Resolvers = Seq(Resolver.sonatypeRepo("snapshots"),
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.bintrayRepo("hmil", "maven")
)

lazy val defaultSettings = Seq(
  organization := "openmole.org",
  version := "0.1.1",
  scalaVersion := "2.13.2",
  resolvers := Resolvers
)

lazy val shared = project.in(file("shared")) settings (defaultSettings: _*) enablePlugins (ScalaJSPlugin)

lazy val go = taskKey[Unit]("go")

lazy val client = project.in(file("client")) enablePlugins (ExecNpmPlugin) settings (defaultSettings) settings(
  skip in packageJSDependencies := false,
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalatags" % scalatagsVersion,
    "org.openmole.scaladget" %%% "tools" % scaladgetVersion,
    "org.openmole.scaladget" %%% "bootstrapnative" % scaladgetVersion,
    "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion,
    "com.lihaoyi" %%% "autowire" % autowireVersion,
    "io.suzaku" %%% "boopickle" % boopickleVersion
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
    "org.scalaj" %% "scalaj-http" % "2.4.2",
    "io.skuber" %% "skuber" % skuberVersion,
    "io.monix" %% "monix" % "3.0.0",
    "org.apache.httpcomponents" % "httpclient" % httpComponentsVersion,
    "org.apache.httpcomponents" % "httpmime" % httpComponentsVersion,
    "com.typesafe.slick" %% "slick" % slickVersion,
    "com.h2database" % "h2" % h2Version,
    "io.suzaku" %% "boopickle" % boopickleVersion,
    "com.lihaoyi" %% "autowire" % autowireVersion
  )
  ) dependsOn (shared) enablePlugins (ScalatraPlugin)

val prefix = "/opt/docker/application/target"
lazy val application = project.in(file("application")) settings (defaultSettings) dependsOn(server) enablePlugins (JavaServerAppPackaging) settings(
  mappings in Docker ++= Seq(
    (dependencyFile in client in Compile).value -> s"$prefix/webapp/js/connect-deps.js",
    (fullOptJS in client in Compile).value.data -> s"$prefix/webapp/js/connect.js"
  ) ++ doMapping((resourceDirectory in client in Compile).value, prefix)
    ++ doMapping((cssFile in client in target).value, s"$prefix/webapp/css/"),
  packageName in Docker := "openmole-connect",
  organization in Docker := "openmole"
)

def doMapping(from: java.io.File, toBase: String): Seq[(File, String)] = {
  val fromPath = s"${from.getAbsolutePath}"
  val list = recursiveFileList(from).toSeq.filterNot {
    _.getName == "webapp"
  }.map { f =>
    f -> s"$toBase${f.getAbsolutePath diff fromPath}"
  }

  list
}

def recursiveFileList(dir: File): Array[File] = {
  val these = dir.listFiles
  these ++ these.filter(_.isDirectory).flatMap(recursiveFileList)
}