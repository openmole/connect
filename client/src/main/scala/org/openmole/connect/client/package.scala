package org.openmoleconnect.client

import scaladget.tools._
import com.raquo.laminar.api.L.*

object Css:
  
  lazy val columnFlex = cls := "columnFlex"
  lazy val centerColumnFlex = cls := "centerColumnFlex"
  lazy val rowFlex = cls := "rowFlex"
  lazy val centerRowFlex = cls := "centerRowFlex"
  lazy val badgeConnect = cls := "badge-connect"
  lazy val rowGap10 = cls := "rowGap10"

  lazy val connectionTabOverlay = Seq(
    display.flex,
    flexDirection.column,
    justifyContent.right, /* center items vertically, in this case */
    alignItems.center, /* center items horizontally, in this case */
    height := "300"
  )

  lazy val adminLogoStyle = Seq(
    display.flex,
    flexDirection.row,
    justifyContent.center, /* cent241,49er items horizontally, in this case */
    width := "500"
  )

  lazy val openmoleLogo = Seq(
    paddingTop := "300",
    width := "500",
  )

