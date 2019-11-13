package org.openmoleconnect.server

import java.util

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import slick.jdbc.H2Profile.api._

object DB {


  case class UUID(value: String) extends MappedTo[String]

  case class Login(value: String) extends MappedTo[String]

  case class Password(value: String) extends MappedTo[String]

  case class User(login: Login, password: Password, email: String, uuid: UUID = UUID(""))


  class Users(tag: Tag) extends Table[(UUID, Login, Password, String)](tag, "USERS") {
    def uuid = column[UUID]("UUID", O.PrimaryKey)

    def login = column[Login]("LOGIN")

    def password = column[Password]("PASSWORD")

    def email = column[String]("EMAIL")

    def * = (uuid, login, password, email)
  }

  val userTable = TableQuery[Users]

  val db: Database = Database.forDriver(
    driver = new org.h2.Driver,
    url = s"jdbc:h2:/${Settings.location}/db"
  )

  def users =
    Await.result(
      db.run(userTable.result).map { x =>
        x.map {
          case (uuid, login, password, email) => User(login, password, email, uuid)
        }
      }, Duration.Inf
    )

  //Seq(User(Login("foo"), Password("foo"), UUID("foo-123-567-foo")), User(Login("bar"), Password("bar"), UUID("bar-123-567-bar")))


  def uuid(login: Login): Option[UUID] = users.find(_.login == login).map {
    _.uuid
  }

  def uuid(login: Login, password: Password): Option[UUID] = users.find(u => u.login == login && u.password == password).map {
    _.uuid
  }

  def runTransaction[E <: Effect](actions: DBIOAction[_, NoStream, E]*) =
    Await.result(
      db.run(
        DBIO.seq(
          actions: _*
        ).transactionally), Duration.Inf)

  def initDB = {
    runTransaction(userTable.schema.createIfNotExists)
  }

  def addUser(login: Login, password: Password, email: String) =
    if (!users.contains{u: User=> u.email == email}) {
      runTransaction(
        userTable += (UUID(util.UUID.randomUUID().toString), login, password, email)
      )
    }
}
