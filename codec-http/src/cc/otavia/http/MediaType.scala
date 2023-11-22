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

package cc.otavia.http

import java.nio.charset.{Charset, StandardCharsets}
import scala.language.unsafeNulls

/** An <a href="http://tools.ietf.org/html/rfc2045">RFC 2045</a> Media Type, appropriate to describe the content type of
 *  an HTTP request or response body.
 */
enum MediaType(val tp: String, val subType: String, val parameter: Option[String] = None, val extension: String = "") {

    val fullType: Array[Byte] = s"$tp/$subType".getBytes(StandardCharsets.US_ASCII)

    val fullName: Array[Byte] = s"$tp/$subType${parameter.getOrElse("")} ".getBytes(StandardCharsets.US_ASCII)

    case AUDIO_AAC        extends MediaType("audio", "aac", extension = ".aac")
    case APP_X_ABIWORD    extends MediaType("application", "x-abiword", extension = ".abw")
    case APP_X_FREEARC    extends MediaType("application", "x-freearc", extension = ".arc")
    case VIDEO_AVI        extends MediaType("video", "x-msvideo", extension = ".avi")
    case APP_AZW          extends MediaType("application", "vnd.amazon.ebook", extension = ".azw")
    case APP_OCTET_STREAM extends MediaType("application", "octet-stream", extension = ".bin")
    case IMAGE_BMP        extends MediaType("image", "bmp", extension = ".bmp")
    case APP_BZ           extends MediaType("application", "x-bzip", extension = ".bz")
    case APP_BZ2          extends MediaType("application", "x-bzip2", extension = ".bz2")
    case APP_CSH          extends MediaType("application", "x-csh", extension = ".csh")
    case TEXT_CSS         extends MediaType("text", "css", extension = ".css")
    case TEXT_CSV         extends MediaType("text", "csv", extension = ".csv")
    case APP_DOC          extends MediaType("application", "msword", extension = ".doc")
    case APP_DOCX
        extends MediaType(
          "application",
          "vnd.openxmlformats-officedocument.wordprocessingml.document",
          extension = ".docx"
        )
    case APP_EOT        extends MediaType("application", "vnd.ms-fontobject", extension = ".eot")
    case APP_EPUB       extends MediaType("application", "epub+zip", extension = ".epub")
    case IMAGE_GIF      extends MediaType("image", "gif", extension = ".gif")
    case TEXT_HTM       extends MediaType("text", "html", extension = ".htm")
    case TEXT_HTML      extends MediaType("text", "html", extension = ".html")
    case TEXT_HTML_UTF8 extends MediaType("text", "html", Some(";charset=utf-8"), extension = ".html")
    case IMAGE_ICO      extends MediaType("image", "vnd.microsoft.icon", extension = ".ico")
    case TEXT_ICS       extends MediaType("text", "calendar", extension = ".ics")
    case APP_JAR        extends MediaType("application", "java-archive", extension = ".jar")
    case IMAGE_JPG      extends MediaType("image", "jpeg", extension = ".jpg")
    case IMAGE_JPEG     extends MediaType("image", "jpeg", extension = ".jpeg")
    case TEXT_JS        extends MediaType("text", "javascript", extension = ".js")
    case APP_JSON       extends MediaType("application", "json", extension = ".json")
    case APP_JSONLD     extends MediaType("application", "ld+json", extension = ".jsonld")
    case AUDIO_MID      extends MediaType("audio", "midi", extension = ".mid")
    case AUDIO_MIDI     extends MediaType("audio", "x-midi", extension = ".midi")
    case TEXT_MJS       extends MediaType("text", "javascript", extension = ".mjs")
    case AUDIO_MP3      extends MediaType("audio", "mpeg", extension = ".mp3")
    case VIDEO_MPEG     extends MediaType("video", "mpeg", extension = ".mpeg")
    case VIDEO_MP4      extends MediaType("video", "mpeg4", extension = ".mp4")
    case VIDEO_M4A      extends MediaType("audio", "x-m4a", extension = ".m4a")
    case APP_MPKG       extends MediaType("application", "vnd.apple.installer+xml", extension = ".mpkg")
    case APP_ODP        extends MediaType("application", "vnd.oasis.opendocument.presentation", extension = ".odp")
    case APP_ODS        extends MediaType("application", "vnd.oasis.opendocument.spreadsheet", extension = ".ods")
    case APP_ODT        extends MediaType("application", "vnd.oasis.opendocument.text", extension = ".odt")
    case AUDIO_OGA      extends MediaType("audio", "ogg", extension = ".oga")
    case VIDEO_OGV      extends MediaType("video", "ogg", extension = "ogv")
    case APP_OGX        extends MediaType("application", "ogg", extension = ".ogx")
    case FONT_OTF       extends MediaType("font", "otf", extension = ".otf")
    case IMAGE_PNG      extends MediaType("image", "png", extension = ".png")
    case APP_PDF        extends MediaType("application", "pdf", extension = ".pdf")
    case APP_PPT        extends MediaType("application", "vnd.ms-powerpoint", extension = ".ppt")
    case APP_PPTX
        extends MediaType(
          "application",
          "vnd.openxmlformats-officedocument.presentationml.presentation",
          extension = ".pptx"
        )
    case APP_RAR         extends MediaType("application", "x-rar-compressed", extension = ".rar")
    case APP_RTF         extends MediaType("application", "rtf", extension = ".rtf")
    case APP_SH          extends MediaType("application", "x-sh", extension = ".sh")
    case IMAGE_SVG       extends MediaType("image", "svg+xml", extension = ".svg")
    case APP_SWF         extends MediaType("application", "x-shockwave-flash", extension = ".swf")
    case APP_TAR         extends MediaType("application", "x-tar", extension = ".tar")
    case IMAGE_TIF       extends MediaType("image", "tiff", extension = ".tif")
    case IMAGE_TIFF      extends MediaType("image", "tiff", extension = ".tiff")
    case FONT_TTF        extends MediaType("font", "ttf", extension = ".ttf")
    case TEXT_PLAIN      extends MediaType("text", "plain", extension = ".txt")
    case TEXT_PLAIN_UTF8 extends MediaType("text", "plain", Some(";charset=utf-8"), extension = ".txt")
    case APP_VSD         extends MediaType("application", "vnd.visio", extension = ".vsd")
    case AUDIO_WAV       extends MediaType("audio", "wav", extension = ".wav")
    case AUDIO_WEBA      extends MediaType("audio", "webm", extension = ".weba")
    case AUDIO_WEBM      extends MediaType("audio", "webm", extension = ".webm")
    case FONT_WOFF       extends MediaType("font", "woff", extension = ".woff")
    case FONT_WOFF2      extends MediaType("font", "woff2", extension = ".woff2")
    case APP_XHTML       extends MediaType("application", "xhtml+xml", extension = ".xhtml")
    case APP_XHTML_UTF8  extends MediaType("application", "xhtml+xml", Some(";charset=utf-8"), extension = ".xhtml")
    case APP_XLS         extends MediaType("application", "vnd.ms-excel", extension = ".xls")
    case APP_XLSX
        extends MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet", extension = ".xlsx")

    case TEXT_XML      extends MediaType("text", "xml", extension = ".xml")
    case TEXT_XML_UTF8 extends MediaType("text", "xml", Some(";charset=utf-8"), extension = ".xml")

    case APP_XML      extends MediaType("application", "xml", extension = ".xml")
    case APP_XML_UTF8 extends MediaType("application", "xml", Some(";charset=utf-8"), extension = ".xml")

    case APP_XUL extends MediaType("application", "vnd.mozilla.xul+xml", extension = ".xul")
    case APP_ZIP extends MediaType("application", "zip", extension = ".zip")
    case APP_7z  extends MediaType("application", "x-7z-compressed", extension = ".7z")

    case APP_X_WWW_FORM_URLENCODED extends MediaType("application", "x-www-form-urlencoded")

    case MULTIPART_FORM_DATA extends MediaType("multipart", "form-data")

    case ANY extends MediaType("*", "*")

}
