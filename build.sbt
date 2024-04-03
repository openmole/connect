import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import com.typesafe.sbt.packager.docker._
import NativePackagerHelper._
import com.typesafe.sbt.packager.linux.LinuxPlugin.autoImport.daemonUser

import scala.collection.mutable

val scalatraVersion = "2.7.0"
val jettyVersion = "9.4.28.v20200408"
val json4sVersion = "4.0.7"
val scalatagsVersion = "0.12.0"
val scaladgetVersion = "1.10.0"
val scalajsDomVersion = "1.10.0"
val scalaJWTVersion = "4.2.0"
val rosHttpVersion = "3.0.0"
val skuberVersion = "2.6.7"
val httpComponentsVersion = "4.5.12"
val slickVersion = "3.5.0-RC1"
val h2Version = "1.4.200"
val autowireVersion = "0.3.3"
val boopickleVersion = "1.4.0"
def laminarVersion = "0.14.2"

def circeVersion = "0.14.6"
def endpoints4SVersion = "1.11.1"
def endpointCirceVersion = "2.5.1"
def endpointHTT4ServerVersion = "10.3.1"
def http4sVersion = "0.23.16"

val Resolvers = Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases"),
  Resolver.bintrayRepo("hmil", "maven")
)

val skuberProject = RootProject(uri("https://github.com/doriordan/skuber.git#30refactor"))

lazy val defaultSettings = Seq(
  organization := "org.openmole",
  version := "0.6-SNAPSHOT",
  scalaVersion := "3.3.3",
  resolvers := Resolvers
)

lazy val shared = project.in(file("shared")) settings (defaultSettings) enablePlugins (ScalaJSPlugin) settings(
  libraryDependencies ++= Seq(
    "org.endpoints4s" %%% "algebra" % endpoints4SVersion,
    "org.endpoints4s" %%% "json-schema-circe" % endpointCirceVersion,
    "io.circe" %% "circe-generic" % circeVersion
  )
)

lazy val go = taskKey[Unit]("go")

lazy val client = project.in(file("client")) enablePlugins (ExecNpmPlugin) settings (defaultSettings) settings(
  skip in packageJSDependencies := false,
  libraryDependencies ++= Seq(
    "org.openmole.scaladget" %%% "tools" % scaladgetVersion,
    "org.openmole.scaladget" %%% "bootstrapnative" % scaladgetVersion,
   // "com.lihaoyi" %%% "autowire" % autowireVersion,
   // "io.suzaku" %%% "boopickle" % boopickleVersion,
    "com.raquo" %%% "laminar" % laminarVersion,
    "org.endpoints4s" %%% "xhr-client" % "5.3.0"
  )
) dependsOn (shared)
//lazy val client = project.in(file("client")) enablePlugins (ExecNpmPlugin) settings (defaultSettings) settings(
//  skip in packageJSDependencies := false,
//  libraryDependencies ++= Seq(
////    "com.lihaoyi" %%% "scalatags" % scalatagsVersion,
////    "org.openmole.scaladget" %%% "tools" % scaladgetVersion,
////    "org.openmole.scaladget" %%% "bootstrapnative" % scaladgetVersion,
//    //"org.scala-js" %%% "scalajs-dom" % scalajsDomVersion,
//    "com.lihaoyi" %%% "autowire" % autowireVersion,
//    "io.suzaku" %%% "boopickle" % boopickleVersion,
//    "org.endpoints4s" %%% "xhr-client" % "5.1.0"
//  )
//) dependsOn (shared)

lazy val server = project.in(file("server")) settings (defaultSettings) settings (
  libraryDependencies ++= Seq(
    "org.endpoints4s" %% "http4s-server" % endpointHTT4ServerVersion excludeAll(ExclusionRule(organization = "com.lihaoyi")),
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    //"org.http4s" %% "http4s-blaze-client" % "0.23.16",
    "io.circe" %% "circe-parser" % circeVersion,
    "io.circe" % "circe-yaml_3" % "1.15.0",
    //"com.raquo" %% "domtypes" % "18.0.0",
    "com.lihaoyi" %% "scalatags" % scalatagsVersion,
//    "org.scalatra" %% "scalatra" % scalatraVersion,
//    "org.eclipse.jetty" % "jetty-webapp" % jettyVersion,
//    "org.eclipse.jetty" % "jetty-server" % jettyVersion,
    "org.json4s" %% "json4s-jackson" % json4sVersion,
    //"com.pauldijou" %% "jwt-core" % scalaJWTVersion,
   // "org.scalaj" %% "scalaj-http" % "2.4.2",
    //"io.skuber" %% "skuber" % skuberVersion,
    //"io.monix" %% "monix" % "3.0.0",
//    "com.pauldijou" %% "jwt-core" % scalaJWTVersion,
//    "org.scalaj" %% "scalaj-http" % "2.4.2",
    "io.skuber" %% "skuber" % skuberVersion cross CrossVersion.for3Use2_13,
    //"io.monix" %% "monix" % "3.0.0",
    "org.apache.httpcomponents" % "httpclient" % httpComponentsVersion,
    "org.apache.httpcomponents" % "httpmime" % httpComponentsVersion,
    "com.typesafe.slick" %% "slick" % slickVersion,
    "com.h2database" % "h2" % h2Version,
//    "io.suzaku" %% "boopickle" % boopickleVersion,
//    "com.lihaoyi" %% "autowire" % autowireVersion,
    "dev.profunktor" %% "http4s-jwt-auth" % "1.2.2",
    "com.github.pathikrit" %% "better-files" % "3.9.2",
    "commons-codec" % "commons-codec" % "1.16.1",
    "org.apache.commons" % "commons-lang3" % "3.14.0"
  )
) dependsOn (shared, skuberProject) enablePlugins (ScalatraPlugin)

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
      ++ doMapping((cssFile in client in target).value, s"$prefix/webapp/css/"),
  Docker / packageName := "openmole-connect",
  Docker / organization := "openmole"
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
