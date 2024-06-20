package org.openmole.connect.server

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
  val versionPattern = "[0-9]*\\.[0-9]*"
  val snapshotPattern = versionPattern + "-SNAPSHOT"

  def wellFormedVersion(v: String) =
    v.matches(versionPattern) || v.matches(snapshotPattern)
  
  def availableVersions(withSnapshot: Boolean = true, history: Option[Int], lastMajors: Boolean): Seq[String] =
    val tags = tool.dockerHubTags("openmole", "openmole")
    val snapshot: Seq[String] = if withSnapshot then tags.find(_.endsWith("SNAPSHOT")).toSeq else Seq()

    val wellFormed = tags.filter(_.matches(versionPattern))

    val majors: Seq[String] =
      val ms = wellFormed.flatMap(_.split('.').headOption).distinct
      val majors =
        history match
          case Some(h) => ms.take(h)
          case None => ms

      majors.flatMap: m =>
        if lastMajors
        then wellFormed.find(_.startsWith(m))
        else wellFormed.filter(_.startsWith(m))

    snapshot ++ majors
