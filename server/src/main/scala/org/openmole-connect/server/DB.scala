package org.openmoleconnect.server

object DB {
  type Login = String
  type UUID = String
  type Password = String

  case class User(login: Login, password: Login, uuid: UUID = "")

  private val users = Seq(User("foo", "foo", "foo-123-567-foo"), User("bar", "bar", "bar-123-567-bar"))

  def uuid(login: Login): Option[UUID] = users.find(_.login == login).map{_.uuid}

  def uuid(user: User): Option[UUID] = users.find(u=> u.login == user.login && u.password == user.password).map{_.uuid}
}
