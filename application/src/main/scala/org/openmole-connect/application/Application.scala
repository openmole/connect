package org.openmoleconnect.application

import org.openmoleconnect.server._

object Application extends App {

  sealed trait LaunchMode

  object ServerMode extends LaunchMode

  object HelpMode extends LaunchMode

  case class Config(
                     tokenSecret: String = "",
                     launchMode: LaunchMode = ServerMode
                   )

  def usage =
    """OpenMOLE-connect application options:
      |[--secret secret] specify the keycloak secret
    """

  def parse(args: List[String], c: Config = Config()): Config = {
    if (args.isEmpty) c
    else {
      args match {
        case "--secret" :: tail ⇒ parse(tail.tail, c.copy(tokenSecret = tail.head))
        case "--help" :: tail => c.copy(launchMode = HelpMode)
        case _ => c.copy(launchMode = HelpMode)
      }
    }
  }

  val config = parse(args.toList, Config())

  config.launchMode match {
    case HelpMode ⇒ println(usage)
    case ServerMode =>
      val server = new ConnectServer(secret = config.tokenSecret)
      server.start()
  }

}
