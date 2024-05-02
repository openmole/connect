package org.openmole.connect.client

import com.raquo.airstream.core
import com.raquo.laminar.api.L.*
import org.openmole.connect
import org.openmole.connect.client.ConnectUtils.*
import org.openmole.connect.client.ConnectUtils.OpenMOLEPodStatus
import org.openmole.connect.client.UIUtils.DetailedInfo
import org.openmole.connect.shared.Data
import org.scalajs.dom
import scaladget.bootstrapnative.*
import scaladget.bootstrapnative.bsn.*
import org.openmole.connect.shared.Data.*
import org.openmoleconnect.client.Css

import scala.scalajs.js.annotation.JSExportTopLevel
import scala.concurrent.ExecutionContext.Implicits.global

object UserPanel {

  lazy val roles = Seq(Data.user, connect.shared.Data.admin)
  lazy val roleFilter = (r: Role) => r == admin

  @JSExportTopLevel("user")
  def user(): Unit =
    def getUser = UserAPIClient.user(()).future

    lazy val userPanel = div(
      child <-- Signal.fromFuture(getUser).map:
        case Some(u) =>
          div(child <-- Signal.fromFuture(AdminAPIClient.usedSpace(u.uuid).future).map: v =>
            div(maxWidth := "1000", margin := "40px auto",
              ConnectUtils.logoutItem.amend(Css.rowFlex, justifyContent.flexEnd),
              UIUtils.userInfoBlock(DetailedInfo(u.role, u.omVersion, v.flatten.map(_.toInt), u.storage, u.memory, u.cpu, u.openMOLEMemory)),
              UIUtils.openmoleBoard(u.uuid)
            )
          )
        case _=> UIUtils.waiter
    )
    lazy val appContainer = dom.document.querySelector("#appContainer")
    render(appContainer, UIUtils.mainPanel(userPanel))

}
