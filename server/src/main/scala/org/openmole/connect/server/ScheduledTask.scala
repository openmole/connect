package org.openmole.connect.server

import org.openmole.connect.server.Email.Sender
import org.openmole.connect.server.K8sService.KubeCache
import org.openmole.connect.server.db.DB

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
  def schedule(shutdown: Option[ConnectServer.Config.Shutdown])(using K8sService, Sender, KubeCache) =
    shutdown.foreach: s =>
      val check = () => checkShutdown(s.days, s.remind.getOrElse(Seq(1, 3, 7)).toSet)
      scheduleTask(s.checkAt.getOrElse(6), check)


  def checkShutdown(days: Int, remind: Set[Int])(using K8sService, Sender, KubeCache) =
    val timeNow = System.currentTimeMillis()
    for
      u <- DB.users
    do
      val onSince = TimeUnit.MILLISECONDS.toDays(timeNow - u.lastAccess).toInt

      if (days - onSince) + 1 <= 0
      then
        tool.log(s"Automatic shutdown of user instance due to inactivity for ${u}")
        K8sService.stopOpenMOLEPod(u.uuid)

      if remind.contains(days - onSince) && K8sService.podExists(u.uuid)
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


