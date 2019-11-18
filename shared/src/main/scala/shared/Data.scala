package shared

object Data {
  val connectionRoute = "/connection"
  val adminRoutePrefix = "shared/AdminApi"

  case class UserData(email: String, password: String, role: String, uuid: String)

}