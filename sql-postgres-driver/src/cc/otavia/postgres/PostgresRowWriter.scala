package cc.otavia.postgres

import cc.otavia.buffer.Buffer
import cc.otavia.postgres.impl.{PreparedStatement, RowValueCodec}
import cc.otavia.sql.RowWriter

import java.math.{BigInteger, BigDecimal as JBigDecimal}
import java.time.{Duration as JDuration, *}
import java.util.{Currency, Locale, UUID}
import scala.concurrent.duration.Duration

protected class PostgresRowWriter extends RowWriter {

    private var ps: PreparedStatement = _
    private var buffer: Buffer        = _

    def setPreparedStatement(ps: PreparedStatement): Unit = this.ps = ps

    def setBuffer(buffer: Buffer): Unit = this.buffer = buffer

    override def writeRowStart(): Unit = {}

    override def writeRowEnd(): Unit = {}

    override def writeNull(index: Int): Unit = buffer.writeInt(-1)

    override def writeChar(value: Char, index: Int): Unit = ???

    override def writeString(value: String, index: Int): Unit = ???

    override def writeBoolean(value: Boolean, index: Int): Unit = ???

    override def writeByte(value: Byte, index: Int): Unit = ???

    override def writeShort(value: Short, index: Int): Unit = ???

    override def writeInt(value: Int, index: Int): Unit = {
        val datatype = ps.paramDesc(index)
        if (datatype.supportsBinary) {
            val pos = buffer.writerOffset
            buffer.writeInt(4)
            buffer.writeInt(value)
        } else ???
    }

    override def writeLong(value: Long, index: Int): Unit = ???

    override def writeFloat(value: Float, index: Int): Unit = ???

    override def writeDouble(value: Double, index: Int): Unit = ???

    override def writeBigInt(value: BigInt, index: Int): Unit = ???

    override def writeBigDecimal(value: BigDecimal, index: Int): Unit = ???

    override def writeBigInteger(value: BigInteger, index: Int): Unit = ???

    override def writeJBigDecimal(value: java.math.BigDecimal, index: Int): Unit = ???

    override def writeInstant(value: Instant, index: Int): Unit = ???

    override def writeLocalDate(value: LocalDate, index: Int): Unit = ???

    override def writeLocalDateTime(value: LocalDateTime, index: Int): Unit = ???

    override def writeLocalTime(value: LocalTime, index: Int): Unit = ???

    override def writeMonthDay(value: MonthDay, index: Int): Unit = ???

    override def writeOffsetDateTime(value: OffsetDateTime, index: Int): Unit = ???

    override def writeOffsetTime(value: OffsetTime, index: Int): Unit = ???

    override def writePeriod(value: Period, index: Int): Unit = ???

    override def writeYear(value: Year, index: Int): Unit = ???

    override def writeYearMonth(value: YearMonth, index: Int): Unit = ???

    override def writeZoneId(value: ZoneId, index: Int): Unit = ???

    override def writeZoneOffset(value: ZoneOffset, index: Int): Unit = ???

    override def writeZonedDateTime(value: ZonedDateTime, index: Int): Unit = ???

    override def writeJDuration(value: JDuration, index: Int): Unit = ???

    override def writeDuration(value: Duration, index: Int): Unit = ???

    override def writeCurrency(value: Currency, index: Int): Unit = ???

    override def writeLocale(value: Locale, index: Int): Unit = ???

    override def writeUUID(value: UUID, index: Int): Unit = ???

}
