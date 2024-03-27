package org.openmoleconnect.client

import scaladget.tools._
import com.raquo.laminar.api.L.*

object Css:
  lazy val connectionTabOverlay = Seq(
    display.flex,
    flexDirection.column,
    justifyContent.center, /* center items vertically, in this case */
    alignItems.center, /* center items horizontally, in this case */
    height := "300"
  )

  lazy val connectionFormStyle = Seq(
    display.flex,
    flexDirection.row,
    justifyContent.flexEnd, /* center items vertically, in this case */
    alignItems.center,
    marginTop := "120"
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

