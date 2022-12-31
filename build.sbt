/*
 * Copyright 2022 yankun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.2.1"

lazy val root = (project in file("."))
  .aggregate(core, adbc, http, redis, examples)
  .settings(
    name := "otavia"
  )

lazy val core = (project in file("core"))
  .settings(
    libraryDependencies += "io.netty" % "netty5-buffer" % "5.0.0.Alpha5",
    libraryDependencies += "org.log4s" %% "log4s" % "1.10.0"
  )

lazy val http = (project in file("http"))
  .dependsOn(core)

lazy val adbc = (project in file("adbc"))
  .dependsOn(core)

lazy val redis = (project in file("redis-client"))
  .dependsOn(core)

lazy val mio = (project in file("mio"))
    .dependsOn(core)

lazy val web = (project in file("web"))
  .dependsOn(core, http, adbc, redis)

lazy val examples = (project in file("examples"))
  .dependsOn(web)
