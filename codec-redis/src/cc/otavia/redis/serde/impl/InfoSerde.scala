package cc.otavia.redis.serde.impl

import cc.otavia.buffer.Buffer
import cc.otavia.redis.cmd.Info
import cc.otavia.redis.serde.AbstractCommandSerde

object InfoSerde extends AbstractCommandSerde[Info] {

    override def deserialize(in: Buffer): Info = {
        ???
    }

    override def serialize(value: Info, out: Buffer): Unit = {
        serializeArrayHeader(1, out)
        serializeBulkString("info", out)
    }

}
