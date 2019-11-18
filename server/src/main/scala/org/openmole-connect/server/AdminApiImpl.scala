package org.openmoleconnect.server

object AdminApiImpl extends shared.AdminApi {

  def users() = {
    println("in users server")
    val u = DBQueries.users
    println("users " + u)
    u
  }
}


//object AdminRequest {
//  private val requestPrefix = "shared/AdminApi"
//
//  private val requests = Seq(
//    s"$requestPrefix/users"
//  )
//
//  def isAdminRequest(path: String) = requests.contains(path)
//}