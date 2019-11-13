package org.openmoleconnect.server

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import slick.jdbc.H2Profile.api._

object DB {


  case class UUID(value: String) extends MappedTo[String]

  case class Login(value: String)

  case class Password(value: String) extends MappedTo[String]

  case class User(login: Login, password: Password, uuid: UUID = UUID(""))


  class Users(tag: Tag) extends Table[(UUID, String, Password)](tag, "USERS") {
    def uuid = column[UUID]("UUID", O.PrimaryKey)

    def login = column[String]("LOGIN")

    def password = column[Password]("PASSWORD")

    def * = (uuid, login, password)
  }

  val userTable = TableQuery[Users]


  private val users = Seq(User(Login("foo"), Password("foo"), UUID("foo-123-567-foo")), User(Login("bar"), Password("bar"), UUID("bar-123-567-bar")))

  def uuid(login: Login): Option[UUID] = users.find(_.login == login).map {
    _.uuid
  }

  def uuid(user: User): Option[UUID] = users.find(u => u.login == user.login && u.password == user.password).map {
    _.uuid
  }


  lazy val db: Database = Database.forDriver(
    driver = new org.h2.Driver,
    url = s"jdbc:h2:/${Settings.location}/db"
  )


  def runTransaction[E <: Effect](actions: DBIOAction[_, NoStream, E]*) =
    Await.result(
      db.run(
        DBIO.seq(
          actions: _*
        ).transactionally), Duration.Inf)

  def initDB() = {
    runTransaction(userTable.schema.createIfNotExists)
  }

}
