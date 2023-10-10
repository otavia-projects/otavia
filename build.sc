/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
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

import $ivy.`com.lihaoyi::mill-contrib-buildinfo:`
import $ivy.`com.lihaoyi::mill-contrib-jmh:`
import $ivy.`com.lihaoyi::mill-contrib-scoverage:`
import mill._
import mill.api.Result
import mill.contrib.jmh.JmhModule
import mill.scalalib._
import mill.scalalib.publish._
import os.Path

object ProjectInfo {

    def description: String     = "A super fast IO & Actor programming model!"
    def organization: String    = "cc.otavia"
    def organizationUrl: String = "https://github.com/otavia-projects"
    def projectUrl: String      = "https://github.com/otavia-projects/otavia"
    def github                  = VersionControl.github("otavia-projects", "otavia")
    def repository              = github.browsableRepository.get
    def licenses                = Seq(License.`Apache-2.0`)
    def author                  = Seq("Yan Kun <yan_kun_1992@foxmail.com>")
    def version                 = "0.3.1-SNAPSHOT"
    def scalaVersion            = "3.3.0"
    def scoverageVersion        = "1.4.0"
    def buildTool               = "mill"
    def buildToolVersion        = main.BuildInfo.millVersion

    def testDep = ivy"org.scalatest::scalatest:3.2.15"

    def jsoniter      = ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core:2.24.1"
    def jsoniterMacro = ivy"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.24.1"
    def booPickle     = ivy"io.suzaku::boopickle:1.4.0"
    def netty5        = ivy"io.netty:netty5-codec:5.0.0.Alpha5"
    def xml           = ivy"org.scala-lang.modules::scala-xml:2.1.0"
    def proto         = ivy"io.github.zero-deps::proto:2.1.2"
    def shapeless     = ivy"org.typelevel::shapeless3-deriving:3.3.0"
    def jedis         = ivy"redis.clients:jedis:4.4.3"
    def scram         = ivy"com.ongres.scram:client:2.1"
    def magnolia      = ivy"com.softwaremill.magnolia1_3::magnolia:1.3.3"

}

trait OtaviaModule extends ScalaModule with PublishModule {

    override def scalaVersion = ProjectInfo.scalaVersion

    override def publishVersion: T[String] = ProjectInfo.version

    override def pomSettings: T[PomSettings] = PomSettings(
      description = ProjectInfo.description,
      organization = ProjectInfo.organization,
      url = ProjectInfo.repository,
      licenses = ProjectInfo.licenses,
      versionControl = ProjectInfo.github,
      developers = Seq(
        Developer(
          "yankun1992",
          "Yan Kun",
          "https://github.com/yankun1992",
          Some("otavia-projects"),
          Some("https://github.com/otavia-projects")
        )
      )
    )

    override def scalacOptions = T { scala.Seq("-Yexplicit-nulls") }

}

trait BufferModule extends OtaviaModule {

    trait BufferTests extends ScalaTests with TestModule.ScalaTest {
        override def ivyDeps = Agg(ProjectInfo.testDep)
    }
}

trait JvmBufferModule extends BufferModule with ScalaModule {

    object test extends BufferTests

}

object buffer extends JvmBufferModule {

    //    object native extends NativeBufferModule
    //
    //    trait NativeBufferModule extends BufferModule with ScalaNativeModule {
    //
    //        def scalaNativeVersion = "0.4.14"
    //
    //        object test extends BufferTests
    //
    //    }

    object bench extends ScalaModule with JmhModule {

        override def scalaVersion = ProjectInfo.scalaVersion

        def jmhCoreVersion = "1.37"

        override def moduleDeps = Seq(buffer)

    }

}

object core extends OtaviaModule /* with BuildInfo */ {

    override def ivyDeps = Agg(ProjectInfo.netty5)

    // override def buildInfoMembers = Seq(
    //   BuildInfo.Value("scalaVersion", scalaVersion()),
    //   BuildInfo.Value("publishVersion", publishVersion()),
    //   BuildInfo.Value("name", "otavia"),
    //   BuildInfo.Value("description", ProjectInfo.description),
    //   BuildInfo.Value("organization", ProjectInfo.organization),
    //   BuildInfo.Value("organizationUrl", ProjectInfo.organizationUrl),
    //   BuildInfo.Value("github", ProjectInfo.repository),
    //   BuildInfo.Value(
    //     "licenses", {
    //         if (ProjectInfo.licenses.length == 1) ProjectInfo.licenses.head.name
    //         else ProjectInfo.licenses.map(_.name).mkString("[", ", ", "]")
    //     }
    //   ),
    //   BuildInfo.Value(
    //     "author", {
    //         if (ProjectInfo.author.length == 1) ProjectInfo.author.head else ProjectInfo.author.mkString("[", ", ", "]")
    //     }
    //   ),
    //   BuildInfo.Value("copyright", "2022")
    // )

    // override def buildInfoPackageName: String = "cc.otavia"

    override def artifactName = "core"

    override def moduleDeps = Seq(buffer)

    object test extends ScalaTests with TestModule.ScalaTest {

        override def ivyDeps = Agg(ProjectInfo.testDep)

    }

}

object testkit extends OtaviaModule {

    override def moduleDeps = Seq(core)

}

object handler extends OtaviaModule {

    override def moduleDeps: Seq[PublishModule] = scala.Seq(core, codec)

    override def artifactName = "handler"

    object test extends ScalaTests with TestModule.ScalaTest {

        override def ivyDeps = Agg(ProjectInfo.testDep)

    }

}

object codec extends OtaviaModule {

    override def moduleDeps: Seq[PublishModule] = scala.Seq(core)

    override def artifactName = "codec"

    object test extends ScalaTests with TestModule.ScalaTest {

        override def ivyDeps = Agg(ProjectInfo.testDep, ProjectInfo.netty5)

    }

}

object `codec-http` extends OtaviaModule {

    override def artifactName = "codec-http"

    override def moduleDeps: Seq[PublishModule] = scala.Seq(codec, serde, `serde-json`)

    object test extends ScalaTests with TestModule.ScalaTest {

        override def ivyDeps = Agg(ProjectInfo.testDep)

    }

}

object adbc extends OtaviaModule {

    override def artifactName = "adbc"

    override def moduleDeps: Seq[PublishModule] = scala.Seq(core, codec, serde)

}

object `codec-redis` extends OtaviaModule {

    override def artifactName: T[String] = "codec-redis"

    override def moduleDeps: Seq[PublishModule] = scala.Seq(core, codec, serde)

    object test extends ScalaTests with TestModule.ScalaTest {

        override def ivyDeps = Agg(ProjectInfo.testDep, ProjectInfo.jedis)

    }

}

object `codec-socks` extends OtaviaModule {

    override def artifactName: T[String] = "codec-socks"

    override def moduleDeps: Seq[PublishModule] = scala.Seq(core, codec)

}

object `codec-haproxy` extends OtaviaModule {

    override def artifactName: T[String] = "codec-haproxy"

    override def moduleDeps: Seq[PublishModule] = scala.Seq(core, codec)

}

object `codec-mqtt` extends OtaviaModule {

    override def artifactName: T[String] = "codec-mqtt"

    override def moduleDeps: Seq[PublishModule] = scala.Seq(core, codec)

}

object `codec-memcache` extends OtaviaModule {

    override def artifactName: T[String] = "codec-memcache"

    override def moduleDeps: Seq[PublishModule] = scala.Seq(core, codec)

}

object `codec-smtp` extends OtaviaModule {

    override def artifactName: T[String] = "codec-smtp"

    override def moduleDeps: Seq[PublishModule] = scala.Seq(core, codec)

}

object `codec-kafka` extends OtaviaModule {

    override def artifactName: T[String] = "codec-kafka"

    override def moduleDeps: Seq[PublishModule] = scala.Seq(core, codec)

}

object log4a extends OtaviaModule {

    override def moduleDeps: Seq[PublishModule] = scala.Seq(core)

    override def artifactName: T[String] = "log4a"

    override def ivyDeps = Agg(ProjectInfo.xml)

    object test extends ScalaTests with TestModule.ScalaTest {

        override def ivyDeps = Agg(ProjectInfo.testDep)

    }

}

object examples extends OtaviaModule {
    override def moduleDeps: Seq[PublishModule] =
        scala.Seq(core, codec, log4a, `mysql-adbc-driver`, `postgres-adbc-driver`, `codec-redis`, `codec-http`)
}

object serde extends OtaviaModule {

    override def artifactName = "serde"

    override def moduleDeps = Seq(buffer)

}

object `serde-json` extends OtaviaModule {

    override def artifactName = "serde-json"

    override def moduleDeps = Seq(serde)

    object test extends ScalaTests with TestModule.ScalaTest {

        override def ivyDeps = Agg(ProjectInfo.testDep, ProjectInfo.jsoniter)

        override def compileIvyDeps = Agg(ProjectInfo.jsoniterMacro)

    }

}

object `serde-proto` extends OtaviaModule {

    override def artifactName = "serde-proto"

    override def moduleDeps = Seq(serde)

    object test extends ScalaTests with TestModule.ScalaTest {

        override def ivyDeps = Agg(ProjectInfo.testDep, ProjectInfo.proto)

    }

}

object `mysql-adbc-driver` extends OtaviaModule {

    override def artifactName = "mysql-adbc-driver"

    override def moduleDeps = Seq(adbc)

    object test extends ScalaTests with TestModule.ScalaTest {

        override def ivyDeps = Agg(ProjectInfo.testDep)

    }

}

object `postgres-adbc-driver` extends OtaviaModule {

    override def artifactName = "postgres-adbc-driver"

    override def moduleDeps = Seq(adbc)

    override def ivyDeps = Agg(ProjectInfo.scram)

    object test extends ScalaTests with TestModule.ScalaTest {

        override def ivyDeps = Agg(ProjectInfo.testDep)

    }

}

trait SiteModule extends ScalaModule {

    private val RE = "(.*)<a href=\"(.*?)\" class=\"logo-container\">(.*)".r

    def projectName: String

    def projectVersion: String

    def projectUrl: String

    def docSource = T.source(millSourcePath)

    override def scalaDocOptions: T[Seq[String]] = T {
        mandatoryScalacOptions() ++ scalacOptions()
    }

    private def replace(child: Path): Unit = {
        val content: String = os.read(child)
        val end             = content.indexOf("\" class=\"logo-container\">")
        if (end != -1) {
            var start = end - 1
            while (content(start) != '\"') start -= 1

            os.remove(child)
            os.write(child, content.substring(0, start))
            os.write.append(child, content.substring(start, end) + "home.html")
            os.write.append(child, content.substring(end, content.length))
        }
    }

    def site = T {

        val compileCp = Seq(
          "-classpath",
          compileClasspath().iterator
              .filter(_.path.ext != "pom")
              .map(_.path)
              .mkString(java.io.File.pathSeparator)
        )

        val docs = T.dest / "docs"
        for {
            child <- os.walk(docSource().path) if os.isFile(child)
        } {
            val fileName = child.toNIO.toString.toLowerCase.trim
            if (fileName.endsWith(".md")) {
                val content = os.read(child)
                val newCtx  = content.replaceAll("([(\"]).*/_assets/images/", "$1images/")
                os.write(docs / child.subRelativeTo(docSource().path), newCtx, createFolders = true)
            } else os.copy.over(child, docs / child.subRelativeTo(docSource().path), createFolders = true)
        }

        val files = Lib.findSourceFiles(T.traverse(moduleDeps)(_.docSources)().flatten, Seq("tasty"))

        val javadocDir = T.dest / "_site"
        os.makeDir.all(javadocDir)

        // format: off
        val options = Seq(
            "-d", javadocDir.toNIO.toString,
            "-siteroot", docs.toNIO.toString,
            "-project-version", projectVersion,
            "-project", projectName,
            // "-project-logo", "docs/_assets/images/logo.drawio.svg",
            "-project-footer", "Copyright (c) 2022, Yan Kun/Otavia Project",
            "-source-links:docs=github://otavia-projects/otavia/main#docs",
            s"-social-links:github::$projectUrl"
        ) ++ scalaDocPluginClasspath().map(pluginPathRef => s"-Xplugin:${pluginPathRef.path}")
        // format: on

        if (
          zincWorker()
              .worker()
              .docJar(
                scalaVersion(),
                scalaOrganization(),
                scalaDocClasspath(),
                scalacPluginClasspath(),
                options ++ compileCp ++ scalaDocOptions() ++ files.map(_.toString)
              )
        ) {
            replace(javadocDir / "index.html")
            for (child <- os.walk(javadocDir / "cc") if child.toNIO.toString.endsWith(".html")) replace(child)
            for (child <- os.walk(javadocDir / "docs") if child.toNIO.toString.endsWith(".html")) replace(child)

            os.copy.over(docs / "home.html", javadocDir / "home.html")
            // os.remove.all.apply(docs)
            Result.Success(PathRef(javadocDir))
        } else {
            os.remove.all.apply(docs)
            Result.Failure("doc generation failed")
        }
    }

}

object docs extends SiteModule {

    override def scalaVersion: T[String] = ProjectInfo.scalaVersion

    override def moduleDeps: Seq[PublishModule] = scala.Seq(buffer, core, serde, handler, codec, testkit)

    override def projectName: String = "otavia"

    override def projectVersion: String = ProjectInfo.version

    override def projectUrl: String = ProjectInfo.projectUrl

}
