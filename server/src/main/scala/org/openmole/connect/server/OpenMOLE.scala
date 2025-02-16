package org.openmole.connect.server

import com.google.common.base.{Supplier, Suppliers}
import com.google.common.cache.CacheBuilder
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.openmole.connect.server.tool.cache

import java.util.concurrent.TimeUnit

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

object OpenMOLE:
  val stablePattern = "[0-9]*\\.[0-9]*"
  val snapshotPattern = stablePattern + "-SNAPSHOT"
  val rcPattern = stablePattern + "-RC[0-9]*"
  val latest = "latest"

  def wellFormedVersion(v: String) =
    v.matches(stablePattern) || v.matches(snapshotPattern) || v.matches(rcPattern)

  def availableVersions(withSnapshot: Boolean = true, history: Option[Int] = None, min: Option[Int] = None, lastMajors: Boolean = false, latest: Boolean = true)(using DockerHubCache): Seq[String] =
    val tags = summon[DockerHubCache].get()

    val stableFormed = tags.filter(v => v.matches(stablePattern))
    val rcFormed = tags.filter(v => v.matches(rcPattern))
    val snapshotFormed = tags.filter(v => v.matches(snapshotPattern))

    def allMajors =
      tags.flatMap: t =>
        util.Try(t.split('.').headOption.map(_.toInt)).toOption.flatten
      .distinct

    val majors =
      val h =
        history match
          case Some(h) => allMajors.take(h)
          case None => allMajors

      min match
        case Some(m) => h.filter(_ >= m)
        case None => h

    val minorVersionMap =
      majors.map: m =>
        m -> stableFormed.filter(_.startsWith(s"$m."))
      .toMap

    val rcVersionMap =
      majors.map: m =>
        m -> rcFormed.filter(_.startsWith(s"$m."))
      .toMap

    val snapshotVersionMap =
      majors.map: m =>
        m -> snapshotFormed.filter(_.startsWith(s"$m."))
      .toMap

    majors.flatMap: maj =>
      if minorVersionMap(maj).nonEmpty
      then
        if lastMajors
        then minorVersionMap(maj).headOption.toSeq
        else minorVersionMap(maj)
      else
        if rcVersionMap(maj).nonEmpty
        then if lastMajors then rcVersionMap(maj).headOption.toSeq else rcVersionMap(maj)
        else if withSnapshot then snapshotVersionMap(maj) else Seq()
    ++ (if latest then Seq(OpenMOLE.latest) else Seq())


  object DockerHubCache:
    def apply(): DockerHubCache =
      Suppliers.memoizeWithExpiration(() => dockerHubTags("openmole", "openmole"), 2, TimeUnit.MINUTES)

  opaque type DockerHubCache = Supplier[Seq[String]]

  def dockerHubTags(group: String, image: String, pageSize: Int = 100): Seq[String] =
    import org.json4s.*
    import org.json4s.jackson.JsonMethods.*

    val httpClient = tool.buildHttpClient()
    try
      val httpGet = new HttpGet(s"https://hub.docker.com/v2/namespaces/$group/repositories/$image/tags?page_size=$pageSize")
      val response = httpClient.execute(httpGet)
      try
        val json = parse(response.getEntity.getContent)
        (json \\ "name").children.map(_.values.toString)
      finally response.close()
    finally httpClient.close()

