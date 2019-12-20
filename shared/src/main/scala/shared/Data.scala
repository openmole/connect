package shared

object Data {
  val connectionRoute = "/connection"
  val adminRoutePrefix = "shared/AdminApi"


  type Role = String
  val admin: Role = "Admin"
  val user: Role = "User"

  trait Status {
    def value: String
  }
  case class Waiting(message: String, value: String = "Waiting") extends Status
  case class Terminated(message: String, finishedAt: Long, value: String = "Terminated") extends Status
  case class Running(value: String = "Running") extends Status

  case class PodInfo(
                      name: String,
                      status: String,
                      restarts: Int,
                      createTime: Long,
                      podIP: String,
                      userEmail: Option[String]
                    )

  case class UserData(name: String, email: String, password: String, role: Role, omVersion: String, lastAccess: Long)


  trait K8ActionResult

  case class K8Success(message: String) extends K8ActionResult
  case class K8Failure(message: String, stackTrace: String) extends K8ActionResult
}