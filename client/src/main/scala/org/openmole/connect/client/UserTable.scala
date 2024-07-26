package org.openmole.connect.client

import scaladget.bootstrapnative.Table._
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.*

class UserTable(headers: Seq[HtmlElement], userRows: Signal[Seq[Row]]):
  val selected: Var[Option[RowID]] = Var(None)
  val expanded: Var[Seq[RowID]] = Var(Seq())

  def updateExpanded(rowID: RowID) =
    expanded.update: e =>
      if e.contains(rowID)
      then e.filterNot(_ == rowID)
      else e.appended(rowID)

  def rowRender(rowID: RowID, initialRow: Row, rowStream: Signal[Row]): HtmlElement =
    initialRow match
      case br: BasicRow =>
        tr(
          backgroundColor <-- selected.signal.map: s =>
            if initialRow.rowID.contains(s) then "#dae5f2" else ""
          ,
          onClick --> (_ => selected.set(Some(initialRow.rowID))),
          children <-- rowStream.map(r => r.tds)
        )
      case er: ExpandedRow =>
        tr(
          child <-- rowStream.map(rs => rs.tds.head)
        )


  val render =
    table( cls := "table",
      tr(headers.map(v => th(centerCell, v))),
      tbody(
        children <-- userRows.split(_.rowID)(rowRender)
      )
    )
