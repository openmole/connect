package org.openmole.connect.server

import cats.effect.*
import org.http4s.HttpRoutes
import org.openmole.connect.server.Authentication.UserCache
import org.openmole.connect.server.KubeService.KubeCache
import org.openmole.connect.server.OpenMOLE.DockerHubCache
import org.openmole.connect.server.db.*
import org.openmole.connect.shared.*

import scala.concurrent.ExecutionContext


class APIImpl()(using DB.Salt, KubeCache, UserCache, DockerHubCache, KubeService, Email.Sender, ExecutionContext):
  def institutions = DB.institutions
  def signup(firstName: String, name: String, email: String, password: String, institution: String, url: String) =
    tool.log(s"signing up $name")
    DB.addRegisteringUser(DB.RegisterUser(name, firstName, email, DB.salted(password), institution)) match
      case Some((inDB, secret)) =>
        def serverURL = url.reverse.dropWhile(_ != '/').reverse
        Email.sendValidationLink(serverURL, inDB, secret)
        None
      case None => Some("A user with this email is already registered")


  def askResetPassword(email: String, url: String) =
    def serverURL = url.reverse.dropWhile(_ != '/').reverse

    DB.userFromEmail(email) match
      case Some(user) =>
        val secret = DB.addResetPassword(user)
        tool.log(serverURL)
        Email.sendResetPasswordLink(serverURL, user, secret)
        None
      case None => Some("Unknown Email")

  def resetPassword(password: String, uuid: String, secret: String) =
    if DB.resetPassword(uuid, secret, password)
    then None
    else Some("Invalid secret")


import sttp.tapir.server.interpreter.*
import sttp.tapir.server.http4s.*

class TapirAPIRoutes(impl: APIImpl):
  import TapirAPI.*
  import sttp.capabilities.fs2.Fs2Streams
  import sttp.tapir.server.ServerEndpoint

  val routes: HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(
      List[ServerEndpoint[Fs2Streams[IO], IO]](
        institutions.serverLogicSuccessPure(_ => impl.institutions),
        signup.serverLogicSuccessPure(impl.signup),
        askResetPassword.serverLogicSuccessPure(impl.askResetPassword),
        resetPassword.serverLogicSuccessPure(impl.resetPassword)
      )
    )