import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

val scalatraVersion = "2.6.5"
val jettyVersion = "9.4.19.v20190610"
val json4sVersion = "3.6.7"
val scalatagsVersion = "0.7.0"
val autowireVersion = "0.2.6"
val boopickleVersion = "1.3.1"
val rxVersion = "0.4.0"
val scaladgetVersion = "1.2.7"
val scalajsDomVersion = "0.9.7"
val roshttpVersion = "2.2.4"

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
    "com.lihaoyi" %%% "autowire" % autowireVersion,
    "io.suzaku" %%% "boopickle" % boopickleVersion,
    "com.lihaoyi" %%% "scalatags" % scalatagsVersion,
    "com.lihaoyi" %%% "scalarx" % rxVersion,
    "fr.iscpif.scaladget" %%% "tools" % scaladgetVersion,
    "fr.iscpif.scaladget" %%% "bootstrapnative" % scaladgetVersion,
    "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion
  )
) dependsOn (shared)

lazy val server = project.in(file("server")) settings (defaultSettings) settings (
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "autowire" % autowireVersion,
    "io.suzaku" %% "boopickle" % boopickleVersion,
    "com.lihaoyi" %% "scalatags" % scalatagsVersion,
    "org.scalatra" %% "scalatra" % scalatraVersion,
    "ch.qos.logback" % "logback-classic" % "1.1.3" % "runtime",
    "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided",
    "org.eclipse.jetty" % "jetty-webapp" % jettyVersion,
    "org.eclipse.jetty" % "jetty-server" % jettyVersion,
    "fr.hmil" %% "roshttp" % roshttpVersion,
    "org.json4s" %% "json4s-native" % json4sVersion
  )
  ) dependsOn (shared) enablePlugins (ScalatraPlugin)

lazy val application = project.in(file("application")) settings (defaultSettings) dependsOn (server) enablePlugins(JavaServerAppPackaging)

lazy val bootstrap = project.in(file("target/bootstrap")) settings (defaultSettings) settings (
  go := {

    val jsBuild = (fullOptJS in client in Compile).value.data
    //val serverTarget = (target in server in Compile).value
    val appTarget = (target in application in Compile).value


    val clientResources = (resourceDirectory in client in Compile).value
    val dependencyJS = (dependencyFile in client in Compile).value
    val depsCSS = (cssFile in client in Compile).value

    IO.copyFile(jsBuild, appTarget / "webapp/js/demo.js")
    IO.copyFile(dependencyJS, appTarget / "webapp/js/deps.js")
    IO.copyDirectory(depsCSS, appTarget / "webapp/css")
    IO.copyDirectory(clientResources, appTarget)
  }) dependsOn(client, server)
