package shared

object Data {
  val connectionRoute = "/connection"
  val adminRoutePrefix = "shared/AdminApi"


  type Role = String
  val admin: Role = "Admin"
  val user: Role = "User"

  type Status = String
  val running: Status = "Running"
  val off: Status = "Off"
  val error: Status = "Error"

  case class UserData(name: String, email: String, password: String, role: Role, omVersion: String, lastAccess: Long)

  case class PersonalUserData(name: String, email: String, password: String, role: Role)

  case class UserStatus(uuid: String, status: Status)

}