package org.openmoleconnect.application

import org.openmoleconnect.server.DB.UUID
import org.openmoleconnect.server._


@main def server =
  val s = ConnectServer("test", true).start()
  Thread.sleep(Long.MaxValue)

//object Application extends App {
//
//  sealed trait LaunchMode
//
//  object ServerMode extends LaunchMode
//
//  object HelpMode extends LaunchMode
//
//  case class Config(
//    tokenSecret: String = "",
//    kubeOff: Boolean = false,
//    launchMode: LaunchMode = ServerMode)
//
//  def usage =
//    """OpenMOLE-connect application options:
//      |[--secret secret] the token generation secret
//      |[-kubeOff ] do not request kubernetes (limited usage).
//    """
//
//  def parse(args: List[String], c: Config = Config()): Config = {
//    if (args.isEmpty) c
//    else {
//      args match {
//        case "--secret" :: tail ⇒ parse(tail.tail, c.copy(tokenSecret = tail.head))
//        case "-kubeOff" :: tail ⇒ parse(List(), c.copy(kubeOff = true))
//        case "--help" :: tail => c.copy(launchMode = HelpMode)
//        case _ => c.copy(launchMode = HelpMode)
//      }
//    }
//  }
//
//  val config = parse(args.toList, Config())
//
//  config.launchMode match {
//    case HelpMode ⇒ println(usage)
//    case ServerMode =>
//      if (!Settings.location.exists) Settings.location.mkdirs()
//      DB.initDB
//      val server = new ConnectServer(secret = config.tokenSecret, config.kubeOff)
//      server.start()
//  }
//
////  DB.addUser(
////    "Moo",
////    DB.Email("moo@moo.com"),
////    DB.Password("moo"),
////    Utils.openmoleversion.stable,
////    900009870L,
////    DB.simpleUser,
////    UUID("foo-123-567-foo")
////  )
//
//}
