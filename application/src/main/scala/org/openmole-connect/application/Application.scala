package org.openmoleconnect.application

import org.openmoleconnect.server.*
import java.io.File

import scopt.*

@main def application(args: String*) =

  case class Config(
    salt: String = "",
    secret: String = "",
    kubeOff: Boolean = false)

  val builder = OParser.builder[Config]
  val parser =
    import builder._
    OParser.sequence(
      programName("openmole-connect"),
      head("openmole-connect", "1.0"),
      opt[String]("salt").action((x, c) => c.copy(salt = x)).required().text("salt value"),
      opt[String]("secret").action((x, c) => c.copy(secret = x)).required().text("secret value"),
      opt[Unit]("kube-off").action((x, c) => c.copy(kubeOff = true)).text("disable kube"),
    )

  OParser.parse(parser, args, Config()) match
    case Some(config) =>
      if (!Settings.location.exists) Settings.location.mkdirs()
      DB.initDB(config.salt)
      val server = new ConnectServer(config.salt, config.secret, config.kubeOff)
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


