package org.openmoleconnect.server

object DB {
  type Login = String
  type Password = String

  case class User(login: Login, password: Login)

  private val users = Seq(User("foo", "foo"), User("bar", "bar"))

  def exists(login: Login) = users.exists(_.login == login)

  def exists(user: User) = users.exists(_ == user)


}
