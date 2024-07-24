package org.openmole.connect.server

import cats.effect.*
import endpoints4s.http4s.server
import org.http4s.HttpRoutes
import org.openmole.connect.server.Authentication.UserCache
import org.openmole.connect.server.K8sService.KubeCache
import org.openmole.connect.server.OpenMOLE.DockerHubCache
import org.openmole.connect.server.db.*
import org.openmole.connect.shared.*


class APIImpl()(using DB.Salt, KubeCache, UserCache, DockerHubCache, K8sService, Email.Sender):
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

class APIRoutes(impl: APIImpl) extends server.Endpoints[IO]
  with API
  with server.JsonEntitiesFromCodecs:

  val routes: HttpRoutes[IO] = HttpRoutes.of:
    routesFromEndpoints(
      institutions.implementedBy(_ => impl.institutions),
      signup.implementedBy(impl.signup),
      askResetPassword.implementedBy(impl.askResetPassword),
      resetPassword.implementedBy(impl.resetPassword)
    )
