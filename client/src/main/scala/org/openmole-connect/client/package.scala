package org.openmoleconnect.client

import scalatags.JsDom.all._
import scaladget.tools._

package object css {
  lazy val connectionTabOverlay: ModifierSeq = Seq(
    display.flex,
    flexDirection.column,
    justifyContent.center, /* center items vertically, in this case */
    alignItems.center, /* center items horizontally, in this case */
    height := 300
  )

  lazy val connectionFormStyle: ModifierSeq = Seq(
    display.flex,
    flexDirection.row,
    justifyContent.flexEnd, /* center items vertically, in this case */
    alignItems.center,
    marginTop := 120
  )

  lazy val adminLogoStyle: ModifierSeq = Seq(
    display.flex,
    flexDirection.row,
    justifyContent.center, /* cent241,49er items horizontally, in this case */
    width := 500
  )

  lazy val openmoleLogo: ModifierSeq = Seq(
    paddingTop := 300,
    width := 500,
  )

}
