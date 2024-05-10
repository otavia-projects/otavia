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

package cc.otavia.handler.ssl

import java.io.*
import java.nio.charset.StandardCharsets
import java.security.cert.CertificateException
import java.security.{KeyException, KeyStore}
import java.util.Base64
import java.util.regex.Pattern
import scala.collection.mutable.ArrayBuffer
import scala.language.unsafeNulls

/** Reads a PEM file and converts it into a list of DERs so that they are imported into a [[KeyStore]] easily. */
object PemReader {

    private val CERT_HEADER = Pattern.compile("-+BEGIN\\s[^-\\r\\n]*CERTIFICATE[^-\\r\\n]*-+(?:\\s|\\r|\\n)+")
    private val CERT_FOOTER = Pattern.compile("-+END\\s[^-\\r\\n]*CERTIFICATE[^-\\r\\n]*-+(?:\\s|\\r|\\n)*")
    private val KEY_HEADER  = Pattern.compile("-+BEGIN\\s[^-\\r\\n]*PRIVATE\\s+KEY[^-\\r\\n]*-+(?:\\s|\\r|\\n)+")
    private val KEY_FOOTER  = Pattern.compile("-+END\\s[^-\\r\\n]*PRIVATE\\s+KEY[^-\\r\\n]*-+(?:\\s|\\r|\\n)*")
    private val BODY        = Pattern.compile("[a-z0-9+/=][a-z0-9+/=\\r\\n]*", Pattern.CASE_INSENSITIVE)

    @throws[CertificateException]
    def readCertificates(file: File): Array[Array[Byte]] = {
        try {
            val in = new FileInputStream(file)
            try {
                readCertificates(in)
            } finally safeClose(in)
        } catch {
            case e: FileNotFoundException =>
                throw new CertificateException("could not find certificate file: " + file)
        }
    }

    @throws[CertificateException]
    def readCertificates(in: InputStream): Array[Array[Byte]] = {
        val content = readContent(in)
        val certs   = new ArrayBuffer[Array[Byte]]()
        val matcher = CERT_HEADER.matcher(content)
        var start   = 0
        var break   = false
        while {
            if (!matcher.find(start)) break = true
            matcher.usePattern(BODY)
            if (!matcher.find()) break = true
            val base64 = matcher.group(0)
            matcher.usePattern(CERT_FOOTER)
            if (!matcher.find()) break = true

            if (!break) certs.addOne(Base64.getMimeDecoder.decode(base64))
            start = matcher.end()
            matcher.usePattern(CERT_HEADER)
            !break
        } do ()

        if (certs.isEmpty) throw new CertificateException("found no certificates in input stream")

        certs.toArray
    }

    @throws[KeyException]
    def readPrivateKey(file: File): Array[Byte] = try {
        val in = new FileInputStream(file)
        try readPrivateKey(in)
        finally safeClose(in)
    } catch {
        case e: FileNotFoundException => throw new KeyException("could not find key file: " + file)
    }

    @throws[KeyException]
    def readPrivateKey(in: InputStream): Array[Byte] = {
        val content = readContent(in)
        val matcher = KEY_HEADER.matcher(content)
        if (!matcher.find()) throw keyNotFoundException()
        matcher.usePattern(BODY)
        if (!matcher.find()) throw keyNotFoundException()

        val base64 = matcher.group(0)

        matcher.usePattern(KEY_FOOTER)
        if (!matcher.find()) throw keyNotFoundException()
        Base64.getMimeDecoder.decode(base64)
    }

    private def keyNotFoundException(): KeyException = new KeyException(
      "could not find a PKCS #8 private key in input stream" +
          " (see https://netty.io/wiki/sslcontextbuilder-and-private-key.html for more information)"
    )

    private def readContent(in: InputStream): String = {
        val out = new ByteArrayOutputStream()
        try {
            val buf = new Array[Byte](8192)
            var ret = 0
            while {
                ret = in.read(buf)
                if (ret > 0) out.write(buf, 0, ret)
                ret > 0
            } do ()
        } finally {
            safeClose(in)
        }
        out.toString(StandardCharsets.US_ASCII)
    }

    private def safeClose(in: InputStream): Unit = try in.close()
    catch {
        case _: IOException =>
    }

}
