package org.openmoleconnect.client

import java.nio.ByteBuffer

import org.scalajs.dom

import scala.scalajs.js.annotation.JSExportTopLevel
import boopickle.Default._
import shared.AdminApi
import autowire._
import rx._
import scaladget.bootstrapnative.Table.StaticSubRow
import scaladget.bootstrapnative._
import scalatags.JsDom.styles
import shared.Data._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}
import scaladget.bootstrapnative.bsn._
import scaladget.tools.{ModifierSeq, _}
import scalatags.JsDom.all._
import scala.scalajs.js.timers._

import Utils._

import scala.scalajs.js.Date

object AdminPanel {

  @JSExportTopLevel("admin")
  def admin() = {

    implicit def userDataSeqToRows(userData: Seq[UserData]): Seq[ExpandableRow] = userData.map { u =>
      buildExpandable(u.name, u.email, u.password, u.role, u.omVersion, u.lastAccess, podInfos.now.filter{_.userEmail == u.email}.headOption)
    }


    lazy val rowFlex = Seq(styles.display.flex, flexDirection.row, justifyContent.spaceAround, alignItems.center)
    lazy val columnFlex = Seq(styles.display.flex, flexDirection.column, styles.justifyContent.center)


    lazy val rows: Var[Seq[ExpandableRow]] = Var(Seq())
    lazy val podInfos: Var[Seq[PodInfo]] = Var(Seq())


    def save(expandableRow: ExpandableRow, userData: UserData): Unit = {
      if (userData.name.isEmpty)
        rows.update(rows.now.filterNot(_ == expandableRow))
      else {
        upsert(userData)
      }
    }

    def upsert(userData: UserData) =
      Post[AdminApi].upserted(userData).call().foreach {
        rows() = _
      }

    def delete(userData: UserData) =
      Post[AdminApi].delete(userData).call().foreach {
        rows() = _
      }

    def updateRows =
      Post[AdminApi].users().call().foreach { us =>
        rows() = us
      }

    def updatePodInfos =
      Post[AdminApi].podInfos().call().foreach {pi=>
        podInfos() = pi
        updateRows
      }

    def updatePodInfoTimer:Unit = {
        setTimeout(5000) {
          updatePodInfos
          updatePodInfoTimer
        }
      }


    def closeAll(except: ExpandableRow) = rows.now.filterNot {
      _ == except
    }.foreach {
      _.subRow.trigger() = false
    }


    def buildExpandable(userName: String = "",
                        userEmail: String = "",
                        userPassword: String = "",
                        userRole: Role = "",
                        userOMVersion: String = "",
                        userLastAccess: Long = 0L,
                        podInfo: Option[PodInfo] = None,
                        expanded: Boolean = false): ExpandableRow = {
      val aVar = Var(expanded)

      lazy val aSubRow: StaticSubRow = StaticSubRow({
        div(height := 300, rowFlex)(
          groupCell.build(margin := 25),
          div(userLastAccess.toStringDate, fontSize := "12px", minWidth := 150),
          label(label_primary, userOMVersion),
          span(columnFlex, alignItems.flexEnd, justifyContent.flexEnd)(
            button(btn_danger, "Delete", onclick := { () =>
              val userData = UserData(userName, userEmail, userPassword, userRole, userOMVersion, userLastAccess)
              delete(userData)
            }, margin := 10)
          )
        )
      }, aVar)

      def statusStyle(s: String) =
        if (s == "Running") label_success
        else if (s == "Waiting") label_warning
        else label_danger

      lazy val expandableRow: ExpandableRow = ExpandableRow(EditableRow(Seq(
        TriggerCell(a(userName, onclick := { () =>
          closeAll(expandableRow)
          aVar() = !aVar.now
        })),
        LabelCell(podInfo.map {
          _.status
        }.getOrElse("Unknown"), Seq(), optionStyle = _ => podInfo.map { pi => statusStyle(pi.status) }.getOrElse(label_danger)),
      )), aSubRow)


      lazy val groupCell: GroupCell = UserPanel.editableData(userName, userEmail, userPassword, userRole, podInfo, userOMVersion, userLastAccess, expanded, (uData: UserData) => save(expandableRow, uData))

      expandableRow
    }


    updateRows
    updatePodInfos
    updatePodInfoTimer

    val addUserButton = button(btn_primary, "Add", onclick := { () =>
      val row = buildExpandable(userRole = user, userOMVersion = "LATEST", expanded = true)
      rows.update(rows.now :+ row)
    })

    val headerStyle: ModifierSeq = Seq(
      height := 40.85
    )

    val editablePanel = div(maxWidth := 1000, margin := "40px auto")(
      img(src := "img/logo.png", css.adminLogoStyle),
      Utils.logoutItem(styles.display.flex, flexDirection.row, justifyContent.flexEnd),
      div(styles.display.flex, flexDirection.row, justifyContent.flexStart, marginLeft := 50, marginBottom := 20, marginTop := 80)(
        addUserButton(styles.display.flex, flexDirection.row, styles.justifyContent.flexEnd)
      ),
      Rx {
        div(styles.display.flex, flexDirection.row, styles.justifyContent.center)(
          EdiTable(Seq("Name", "Status"), rows()).render(width := "90%")
        )
      }
    )

    dom.document.body.appendChild(editablePanel.render)
  }

}


object Post extends autowire.Client[ByteBuffer, Pickler, Pickler] {

  override def doCall(req: Request): Future[ByteBuffer] = {
    dom.ext.Ajax.post(
      url = req.path.mkString("/"),
      data = Pickle.intoBytes(req.args),
      responseType = "arraybuffer",
      headers = Map("Content-Type" -> "application/octet-stream")
    ).map(r => TypedArrayBuffer.wrap(r.response.asInstanceOf[ArrayBuffer]))
  }

  override def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)

  override def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)

}

//}