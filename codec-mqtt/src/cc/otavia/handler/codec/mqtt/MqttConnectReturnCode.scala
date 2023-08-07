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

package cc.otavia.handler.codec.mqtt

enum MqttConnectReturnCode(val byteValue: Byte) {

    case CONNECTION_ACCEPTED extends MqttConnectReturnCode(0x00)
    // MQTT 3 codes
    case CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION extends MqttConnectReturnCode(0x01)
    case CONNECTION_REFUSED_IDENTIFIER_REJECTED           extends MqttConnectReturnCode(0x02)
    case CONNECTION_REFUSED_SERVER_UNAVAILABLE            extends MqttConnectReturnCode(0x03)
    case CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD     extends MqttConnectReturnCode(0x04)
    case CONNECTION_REFUSED_NOT_AUTHORIZED                extends MqttConnectReturnCode(0x05)
    // MQTT 5 codes
    case CONNECTION_REFUSED_UNSPECIFIED_ERROR            extends MqttConnectReturnCode(0x80.toByte)
    case CONNECTION_REFUSED_MALFORMED_PACKET             extends MqttConnectReturnCode(0x81.toByte)
    case CONNECTION_REFUSED_PROTOCOL_ERROR               extends MqttConnectReturnCode(0x82.toByte)
    case CONNECTION_REFUSED_IMPLEMENTATION_SPECIFIC      extends MqttConnectReturnCode(0x83.toByte)
    case CONNECTION_REFUSED_UNSUPPORTED_PROTOCOL_VERSION extends MqttConnectReturnCode(0x84.toByte)
    case CONNECTION_REFUSED_CLIENT_IDENTIFIER_NOT_VALID  extends MqttConnectReturnCode(0x85.toByte)
    case CONNECTION_REFUSED_BAD_USERNAME_OR_PASSWORD     extends MqttConnectReturnCode(0x86.toByte)
    case CONNECTION_REFUSED_NOT_AUTHORIZED_5             extends MqttConnectReturnCode(0x87.toByte)
    case CONNECTION_REFUSED_SERVER_UNAVAILABLE_5         extends MqttConnectReturnCode(0x88.toByte)
    case CONNECTION_REFUSED_SERVER_BUSY                  extends MqttConnectReturnCode(0x89.toByte)
    case CONNECTION_REFUSED_BANNED                       extends MqttConnectReturnCode(0x8a.toByte)
    case CONNECTION_REFUSED_BAD_AUTHENTICATION_METHOD    extends MqttConnectReturnCode(0x8c.toByte)
    case CONNECTION_REFUSED_TOPIC_NAME_INVALID           extends MqttConnectReturnCode(0x90.toByte)
    case CONNECTION_REFUSED_PACKET_TOO_LARGE             extends MqttConnectReturnCode(0x95.toByte)
    case CONNECTION_REFUSED_QUOTA_EXCEEDED               extends MqttConnectReturnCode(0x97.toByte)
    case CONNECTION_REFUSED_PAYLOAD_FORMAT_INVALID       extends MqttConnectReturnCode(0x99.toByte)
    case CONNECTION_REFUSED_RETAIN_NOT_SUPPORTED         extends MqttConnectReturnCode(0x9a.toByte)
    case CONNECTION_REFUSED_QOS_NOT_SUPPORTED            extends MqttConnectReturnCode(0x98.toByte)
    case CONNECTION_REFUSED_USE_ANOTHER_SERVER           extends MqttConnectReturnCode(0x9c.toByte)
    case CONNECTION_REFUSED_SERVER_MOVED                 extends MqttConnectReturnCode(0x9d.toByte)
    case CONNECTION_REFUSED_CONNECTION_RATE_EXCEEDED     extends MqttConnectReturnCode(0x9f.toByte)

}

object MqttConnectReturnCode {

    private val VALUES: Array[MqttConnectReturnCode] = {
        val arr = new Array[MqttConnectReturnCode](160)
        values.foreach { code =>
            val unsignedByte = code.byteValue & 0xff
            // Suppress a warning about out of bounds access since the enum contains only correct values// Suppress a warning about out of bounds access since the enum contains only correct values
            arr(unsignedByte) = code // lgtm [java/index-out-of-bounds]
        }
        arr
    }

    def valueOf(b: Byte): MqttConnectReturnCode = {
        val unsignedByte = b & 0xff
        val mqttConnectReturnCode: MqttConnectReturnCode =
            try {
                VALUES(unsignedByte)
            } catch {
                case _: Throwable =>
                    throw new IllegalArgumentException("unknown connect return code: " + unsignedByte)
            }
        mqttConnectReturnCode
    }

}
