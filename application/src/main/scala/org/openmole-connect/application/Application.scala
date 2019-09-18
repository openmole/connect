package org.openmoleconnect.application

import org.openmoleconnect.server._

object Application extends App {

  sealed trait LaunchMode

  object ServerMode extends LaunchMode

  object HelpMode extends LaunchMode

  case class Config(
                     tokenSecret: String = "",
                     publicAdress: String = "",
                     port: Option[Int] = None,
                     launchMode: LaunchMode = ServerMode
                   )

  def usage =
    """OpenMOLE-connect application options:
      |[--secret secret] specify the keycloak secret
      |[--public-adress url] public entry point for openmole instances
      |[--port port] specify the port for openmole-manager application
    """

  def parse(args: List[String], c: Config = Config()): Config = {
    if (args.isEmpty) c
    else {
      args match {
        case "--secret" :: tail ⇒ parse(tail.tail, c.copy(tokenSecret = tail.head))
        case "--public-adress" :: tail ⇒ parse(tail.tail, c.copy(publicAdress = tail.head))
        case "--port" :: tail ⇒ parse(tail.tail, c.copy(port = Some(tail.head.toInt)))
        case "--help" :: tail => c.copy(launchMode = HelpMode)
        case _ => c.copy(launchMode = HelpMode)
      }
    }
  }

  val config = parse(args.toList, Config())

  config.launchMode match {
    case HelpMode ⇒
      println(usage)
    case ServerMode =>
      val server = new ConnectServer(
        port = config.port.getOrElse(8080),
        secret = config.tokenSecret,
        publicAdress = config.publicAdress)
      server.start()
  }

}
