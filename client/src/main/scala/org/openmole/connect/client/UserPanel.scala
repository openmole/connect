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

    def getVersions = UserAPIClient.availableVersions(()).future

    val podInfo: Var[Option[PodInfo]] = Var(None)

    lazy val userPanel = div(
      child <-- Signal.fromFuture(getUser).map:
        case Some(u) =>
          div(
            EventStream.periodic(5000).toObservable -->
              Observer: _ =>
                UserAPIClient.instance(()).future.foreach(podInfo.set),
            div(maxWidth := "1000", margin := "40px auto",
              ConnectUtils.logoutItem.amend(Css.rowFlex, justifyContent.flexEnd),
              UIUtils.userInfoBlock(u),
              div(Css.rowFlex, justifyContent.flexEnd, marginRight := "30", marginBottom := "20",
                child <--
                  Signal.fromFuture(getVersions).map: vs =>
                    UIUtils.versionChanger(u.omVersion, vs.getOrElse(Seq()))
              ),
              UIUtils.openmoleBoard(None, podInfo.now())
            )
          )
        case _ => UIUtils.waiter
    )

    lazy val appContainer = dom.document.querySelector("#appContainer")
    render(appContainer, UIUtils.mainPanel(userPanel))

}
