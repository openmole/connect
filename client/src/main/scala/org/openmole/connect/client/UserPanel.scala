package org.openmole.connect.client

import com.raquo.airstream.core
import com.raquo.laminar.api.L.*
import org.openmole.connect
import org.openmole.connect.client.ConnectUtils.*
import org.openmole.connect.client.UIUtils.DetailedInfo
import org.openmole.connect.shared.Data
import org.scalajs.dom
import scaladget.bootstrapnative.*
import scaladget.bootstrapnative.bsn.*
import org.openmole.connect.shared.Data.*

import scala.scalajs.js.annotation.JSExportTopLevel
import scala.concurrent.ExecutionContext.Implicits.global

object UserPanel {

  lazy val rowFlex = Seq(display.flex, flexDirection.row, justifyContent.spaceAround)
  lazy val columnFlex = Seq(display.flex, flexDirection.column, justifyContent.center, alignItems.flexStart)

  lazy val roles = Seq(Data.user, connect.shared.Data.admin)
  lazy val roleFilter = (r: Role) => r == admin

  @JSExportTopLevel("user")
  def user(): Unit =
    def getUser = UserAPIClient.user(()).future

    lazy val userPanel = div(
      child <-- Signal.fromFuture(getUser).map:
        case Some(u) =>
          div(child <-- Signal.fromFuture(AdminAPIClient.usedSpace(u.uuid).future).map: v =>
            val podInfo: Var[Option[PodInfo]] = Var(None)

            div(maxWidth := "1000", margin := "40px auto",
              ConnectUtils.logoutItem.amend(display.flex, flexDirection.row, justifyContent.flexEnd),
              UIUtils.userInfoBlock(DetailedInfo(u.role, u.omVersion, v.flatten.map(_.toInt), u.storage, u.memory, u.cpu, u.openMOLEMemory)),
              EventStream.periodic(5000).toObservable -->
                Observer: _ =>
                  UserAPIClient.instance(()).future.foreach(podInfo.set),
              child <--
                podInfo.signal.map:
                  case None =>
                    button(btn_primary,
                      "Launch OpenMOLE",
                      onClick --> { _ =>
                        UserAPIClient.launch(()).future.foreach(podInfo.set)
                      }
                    )
                  case Some(podInfo) =>
                    podInfo.status match
                      case Some(t: PodInfo.Status.Terminated) =>
                        s"OpenMOLE is stopping since ${t.finishedAt}: ${t.message}"
                      case Some(t: PodInfo.Status.Waiting) =>
                        s"OpenMOLE is waiting ${t.message}"
                      case _ =>
                        podInfo.podIP match
                          case Some(_) =>
                            div(
                              a("Go to OpenMOLE", href := s"/${Data.openMOLERoute}/"),
                              button(btn_danger,
                                "Stop OpenMOLE",
                                onClick --> { _ => UserAPIClient.stop(()).future }
                              )
                            )
                          case None => "OpenMOLE is launching"

            )
          )
        case None => div()
    )

    lazy val appContainer = dom.document.querySelector("#appContainer")
    render(appContainer, UIUtils.mainPanel(userPanel))

}
