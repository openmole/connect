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

  lazy val openmoleLogo: ModifierSeq = Seq(
    paddingTop := 300,
    width := 600,
  )

}
