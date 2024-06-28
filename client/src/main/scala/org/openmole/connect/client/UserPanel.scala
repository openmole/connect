package org.openmole.connect.client

import com.raquo.airstream.core
import com.raquo.laminar.api.L.*
import org.openmole.connect
import org.openmole.connect.client.ConnectUtils.*
import org.openmole.connect.client.ConnectUtils.OpenMOLEPodStatus
import org.openmole.connect.client.UIUtils.DetailedInfo
import org.openmole.connect.shared.{Data, Storage}
import org.scalajs.dom
import scaladget.bootstrapnative.*
import scaladget.bootstrapnative.bsn.*
import org.openmole.connect.shared.Data.*
import org.openmoleconnect.client.Css

import scala.scalajs.js.annotation.JSExportTopLevel
import scala.concurrent.ExecutionContext.Implicits.global

object UserPanel:

  lazy val roles = Data.Role.values
  lazy val roleFilter = (r: Role) => r == Data.Role.Admin

  @JSExportTopLevel("user")
  def user(): Unit =
    def getUser = UserAPIClient.user(()).future
    def getVersions = UserAPIClient.availableVersions(()).future

    val podInfo: Var[Option[PodInfo]] = Var(None)
    val space: Var[Option[Storage]] = Var(None)

    lazy val userPanel = div(
      child <-- Signal.fromFuture(getUser).map:
        case Some(u) =>
          div(
            EventStream.periodic(5000).toObservable -->
              Observer: _ =>
                UserAPIClient.instance(()).future.foreach: i =>
                  podInfo.set(i)
                val stopped = podInfo.now().flatMap(_.status.map(PodInfo.Status.isStopped)).getOrElse(true)
                if space.now().isEmpty && !stopped
                then UserAPIClient.usedSpace(()).future.foreach(space.set),
            div(maxWidth := "1000", margin := "40px auto",
              ConnectUtils.logoutItem.amend(Css.rowFlex, justifyContent.flexEnd),
              UIUtils.userInfoBlock(u, space),
              div(Css.rowFlex, justifyContent.flexEnd, marginRight := "30", marginBottom := "20",
                child <--
                  Signal.fromFuture(getVersions).map: vs =>
                    UIUtils.versionChanger(u.omVersion, vs.getOrElse(Seq()))
              ),
              div(
                child <--
                  podInfo.signal.map(_.flatMap(_.status)).map:
                    case Some(st) => UIUtils.openmoleBoard(None, st)
                    case None => UIUtils.openmoleBoard(None, PodInfo.Status.Inactive)
              )
            )
          )
        case _ => UIUtils.waiter.amend(Css.rowFlex, justifyContent.flexEnd)
    )

    lazy val appContainer = dom.document.querySelector("#appContainer")
    render(appContainer, UIUtils.mainPanel(userPanel))

