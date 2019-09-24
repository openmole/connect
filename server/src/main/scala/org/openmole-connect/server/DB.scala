package org.openmoleconnect.server

object DB {

  case class UUID(value: String)
  case class Login(value: String)
  case class Password(value: String)


  case class User(login: Login, password: Password, uuid: UUID = UUID(""))

  private val users = Seq(User(Login("foo"), Password("foo"), UUID("foo-123-567-foo")), User(Login("bar"), Password("bar"), UUID("bar-123-567-bar")))

  def uuid(login: Login): Option[UUID] = users.find(_.login == login).map{_.uuid}

  def uuid(user: User): Option[UUID] = users.find(u=> u.login == user.login && u.password == user.password).map{_.uuid}
}
