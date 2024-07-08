package org.openmole.connect.application

import org.openmole.connect.server.*

import java.io.File
import scopt.*

@main def application(args: String*) =

  case class Config(
    configFile: Option[File] = None)

  val builder = OParser.builder[Config]
  val parser =
    import builder._
    OParser.sequence(
      programName("openmole-connect"),
      head("openmole-connect", "1.0"),
      //opt[String]("openmole-test").action((x, c) => c.copy(openmoleTest = Some(x))).required(),
      opt[File]("config-file").action((x, c) => c.copy(configFile = Some(x))).required(),

    )

  OParser.parse(parser, args, Config()) match
    case Some(config) =>
      if (!Settings.location.exists) Settings.location.mkdirs()
      val configuration = ConnectServer.read(config.configFile.get)
      val server = ConnectServer(configuration)
      server.start()
    case _ =>
      // arguments are bad, error message will have been displayed






//  DB.addUser(
//    "Moo",
//    DB.Email("moo@moo.com"),
//    DB.Password("moo"),
//    Utils.openmoleversion.stable,
//    900009870L,
//    DB.simpleUser,
//    UUID("foo-123-567-foo")
//  )


