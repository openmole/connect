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
import ConnectUtils._


object AdminPanel {

  @JSExportTopLevel("admin")
  def admin() = {

    implicit def userDataSeqToRows(userData: Seq[UserData]): Seq[EmailRow] = userData.map { u =>

      buildExpandable(u.name, u.email, u.password, u.role, u.omVersion, u.lastAccess, podInfos.now.filter {
        _.userEmail == Some(u.email)
      }.headOption, open.now.map {
        _ == u.email
      }.getOrElse(false))
    }


    lazy val rowFlex = Seq(styles.display.flex, flexDirection.row, justifyContent.spaceAround, alignItems.center)
    lazy val columnFlex = Seq(styles.display.flex, flexDirection.column, styles.justifyContent.center)

    case class EmailRow(email: String, expandableRow: ExpandableRow)

    lazy val rows: Var[Seq[EmailRow]] = Var(Seq())
    lazy val open: Var[Option[String]] = Var(None)
    lazy val podInfos: Var[Seq[PodInfo]] = Var(Seq())


    def save(expandableRow: ExpandableRow, userData: UserData): Unit = {
      //  if (userData.name.isEmpty)
      //   rows.update(rows.now.filterNot(_.expandableRow == expandableRow))
      //  else {
      upsert(userData)
      //   }
    }

    def upsert(userData: UserData) =
      Post[AdminApi].upserted(userData).call().foreach {
        rows() = _
      }

    def deleteUser(userData: UserData) =
      Post[AdminApi].delete(userData).call().foreach {
        rows() = _
      }

    def stopOpenMOLE(userData: UserData) =
      Post[AdminApi].stopOpenMOLE(userData).call().foreach {
        rows() = _
      }

    def updateRows = {
      Post[AdminApi].users().call().foreach { us =>
        rows() = us
      }
    }

    def updatePodInfos =
      Post[AdminApi].podInfos().call().foreach { pi =>
        println("PI " + pi)
        podInfos() = pi
        updateRows
      }

    def isEditing(email: String): Boolean = rows.now.filter { er =>
      er.expandableRow.editableRow.cells.exists {
        _.editMode.now
      }
    }.map {
      _.email
    }.headOption == Some(email)

    //    def updatePodInfoTimer: Unit = {
    //      setTimeout(10000) {
    //        updatePodInfos
    //        updatePodInfoTimer
    //      }
    //    }


    def closeAll(except: ExpandableRow) = rows.now.filterNot {
      _.expandableRow == except
    }.foreach {
      _.expandableRow.subRow.trigger() = false
    }


    def buildExpandable(userName: String = "",
                        userEmail: String = "",
                        userPassword: String = "",
                        userRole: Role = "",
                        userOMVersion: String = "",
                        userLastAccess: Long = 0L,
                        podInfo: Option[PodInfo] = None,
                        expanded: Boolean = false,
                        edited: Option[Boolean] = None): EmailRow = {
      val aVar = Var(expanded)
      val editing = edited.getOrElse(isEditing(userEmail))

      lazy val aSubRow: StaticSubRow = StaticSubRow({
        div(height := 350, rowFlex)(
          groupCell.build(margin := 25),
          div(userLastAccess.toStringDate, fontSize := "12px", minWidth := 150),
          label(label_primary, userOMVersion),
          span(columnFlex, alignItems.flexEnd, justifyContent.flexEnd)(
            button(btn_danger, "Delete user (and data)", onclick := { () =>
              val userData = UserData(userName, userEmail, userPassword, userRole, userOMVersion, userLastAccess)
              deleteUser(userData)
            }, margin := 10),
            button(btn_danger, "Stop OpenMOLE", onclick := { () =>
              val userData = UserData(userName, userEmail, userPassword, userRole, userOMVersion, userLastAccess)
              stopOpenMOLE(userData)
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
          open() = {
            if (aVar.now) Some(userEmail)
            else None
          }
        })),
        LabelCell(podInfo.map {
          _.status
        }.getOrElse("Unknown"), Seq(), optionStyle = _ => podInfo.map { pi => statusStyle(pi.status) }.getOrElse(label_danger)),
      )), aSubRow)


      lazy val groupCell: GroupCell = UserPanel.editableData(userName, userEmail, userPassword, userRole, podInfo, userOMVersion, userLastAccess, editableEmail = true, editableRole = true, expanded, editing, (uData: UserData) => save(expandableRow, uData))

      EmailRow(userEmail, expandableRow)
    }


    updateRows
    updatePodInfos
    //updatePodInfoTimer

    val addUserButton = button(btn_primary, "Add", onclick := { () =>
      val row = buildExpandable(userRole = user, userOMVersion = "LATEST", expanded = true, edited = Some(true))
      rows.update(rows.now :+ row)
    })

    val refreshButton = button(btn_default, "Refresh", onclick := { () =>
      updatePodInfos
    })

    val headerStyle: ModifierSeq = Seq(
      height := 40.85
    )

    val editablePanel = div(maxWidth := 1000, margin := "40px auto")(
      img(src := "img/logo.png", Css.adminLogoStyle),
      ConnectUtils.logoutItem(styles.display.flex, flexDirection.row, justifyContent.flexEnd),
      div(styles.display.flex, flexDirection.row, justifyContent.flexStart, marginLeft := 50, marginBottom := 20, marginTop := 80)(
        addUserButton(styles.display.flex, flexDirection.row, styles.justifyContent.flexEnd),
        refreshButton(styles.display.flex, flexDirection.row, styles.justifyContent.flexEnd)
      ),
      Rx {
        div(styles.display.flex, flexDirection.row, styles.justifyContent.center)(
          EdiTable(Seq("Name", "Status"), rows().map {
            _.expandableRow
          }).render(width := "90%")
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