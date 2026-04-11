/*
 * Copyright 2022 Yan Kun <yan_kun_1992@foxmail.com>
 *
 * This file fork from netty.
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

package cc.otavia.core.channel

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.BeforeAndAfterAll

import java.io.RandomAccessFile
import java.nio.file.{Files, Path}
import scala.language.unsafeNulls

class FileRegionSuite extends AnyFunSuiteLike with BeforeAndAfterAll {

    private var testFile: Path = _

    override def beforeAll(): Unit = {
        testFile = Files.createTempFile("otavia-file-region-test", ".dat")
        val ch = Files.write(testFile, Array.fill(1024)(1.toByte))
    }

    override def afterAll(): Unit = {
        Files.deleteIfExists(testFile)
    }

    test("create default file region") {
        val fileRegion = DefaultFileRegion(testFile.toFile)

        assert(fileRegion.position == 0)
        assert(fileRegion.count > 0)
    }

    test("file region retain and release") {
        val fileRegion = DefaultFileRegion(testFile.toFile)

        fileRegion.retain
        assert(fileRegion.refCnt == 2)
        fileRegion.release
        assert(fileRegion.refCnt == 1)
        fileRegion.release
        assert(fileRegion.refCnt == 0)
        assert(!fileRegion.isOpen)
    }

    test("file region deallocate") {
        val ch     = new RandomAccessFile(testFile.toFile, "r").getChannel
        val region = DefaultFileRegion(ch)

        region.retain
        assert(region.refCnt == 2)
        region.release
        assert(region.isOpen)
        assert(region.refCnt == 1)
        region.release
        assert(region.refCnt == 0)
        assert(!region.isOpen)
    }

}
