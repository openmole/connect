package org.openmole.connect.server

import org.openmole.connect.server.Email.Sender
import org.openmole.connect.server.KubeService.KubeCache
import org.openmole.connect.server.db.DB
import org.openmole.connect.server.ConnectServer.Config
import java.time.*
import java.util.concurrent.{Executors, ThreadFactory, TimeUnit}

/*
 * Copyright (C) 2024 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


object ScheduledTask:
  def schedule(shutdown: Option[ConnectServer.Config.Shutdown])(using KubeService, Sender, KubeCache, Config.Connect) =
    shutdown.foreach: s =>
      val hour = s.checkAt.getOrElse(6)
      tool.log(s"Schedule automatic shutdown check at ${hour}, shutdown after ${s.days} days")
      val check = () => checkShutdown(s.days, s.remind.getOrElse(Seq(1, 3, 7)).toSet)
      scheduleTask(hour, check)

  def checkShutdown(days: Int, remind: Set[Int])(using KubeService, Sender, KubeCache, Config.Connect) =
    tool.log(s"Checking instances for automatic shutdown")

    val timeNow = System.currentTimeMillis()
    for
      u <- DB.users
    do
      val onSince = TimeUnit.MILLISECONDS.toDays(timeNow - u.lastAccess).toInt
      val shutdownIn = (days - onSince) + 1

      if shutdownIn <= 0
      then
        tool.log(s"Automatic shutdown of user instance due to inactivity for ${u}")
        util.Try:
          KubeService.stopOpenMOLEPod(u.uuid)
      else tool.log(s"Keep instance user ${u} instance, shutdown in in $shutdownIn")

      if remind.contains(days - onSince) && KubeService.podExists(u.uuid)
      then
        tool.log(s"Send inactivity mail to ${u}")
        Email.sendInactive(u, onSince, days - onSince)


  def scheduleTask(hour: Int, f: () => Unit) =
    val now = ZonedDateTime.now()

    val nextRun =
      val next = now.withHour(hour).withMinute(0).withSecond(0)
      if now.compareTo(next) > 0 then next.plusDays(1) else next

    val duration = Duration.between(now, nextRun)
    val initialDelay = duration.getSeconds

    val scheduler = Executors.newScheduledThreadPool(0, Thread.ofVirtual().factory())
    val runnable: Runnable = () => f()
    scheduler.scheduleAtFixedRate(runnable, initialDelay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS)


