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

package cc.otavia.json

import cc.otavia.buffer.Buffer
import cc.otavia.serde.annotation.{ignore, rename, stringfield}
import org.scalatest.funsuite.AnyFunSuite

import java.nio.charset.StandardCharsets
import java.time.{Instant, LocalDate, LocalDateTime, YearMonth}
import scala.collection.mutable
import scala.collection.immutable
import scala.language.unsafeNulls

class JsonSerdeSuite extends AnyFunSuite {

    private def roundtrip[A](value: A, serde: JsonSerde[A]): A = {
        val buffer = Buffer.wrap(new Array[Byte](8192)).clean()
        serde.serialize(value, buffer)
        buffer.readerOffset(0)
        serde.deserialize(buffer)
    }

    private def serializeToJson[A](value: A, serde: JsonSerde[A]): String = {
        val buffer = Buffer.wrap(new Array[Byte](8192)).clean()
        serde.serialize(value, buffer)
        buffer.getCharSequence(0, buffer.writerOffset, StandardCharsets.UTF_8).toString
    }

    private def deserializeFromJson[A](json: String, serde: JsonSerde[A]): A = {
        val buffer = Buffer.wrap(new Array[Byte](8192)).clean()
        buffer.writeBytes(json.getBytes(StandardCharsets.UTF_8))
        buffer.readerOffset(0)
        serde.deserialize(buffer)
    }

    // ==================== Basic Types ====================

    test("Byte roundtrip") {
        val serde = JsonSerde.derived[Byte]
        assert(roundtrip(0.toByte, serde) == 0.toByte)
        assert(roundtrip(127.toByte, serde) == 127.toByte)
        assert(roundtrip(-128.toByte, serde) == -128.toByte)
    }

    test("Short roundtrip") {
        val serde = JsonSerde.derived[Short]
        assert(roundtrip(0.toShort, serde) == 0.toShort)
        assert(roundtrip(12345.toShort, serde) == 12345.toShort)
        assert(roundtrip(-999.toShort, serde) == -999.toShort)
    }

    test("Int roundtrip") {
        val serde = JsonSerde.derived[Int]
        assert(roundtrip(0, serde) == 0)
        assert(roundtrip(42, serde) == 42)
        assert(roundtrip(-100, serde) == -100)
        assert(roundtrip(Int.MaxValue, serde) == Int.MaxValue)
        assert(roundtrip(Int.MinValue, serde) == Int.MinValue)
    }

    test("Long roundtrip") {
        val serde = JsonSerde.derived[Long]
        assert(roundtrip(0L, serde) == 0L)
        assert(roundtrip(123456789L, serde) == 123456789L)
        assert(roundtrip(-987654321L, serde) == -987654321L)
        assert(roundtrip(Long.MaxValue, serde) == Long.MaxValue)
    }

    test("Float roundtrip") {
        val serde = JsonSerde.derived[Float]
        assert(roundtrip(0.0f, serde) == 0.0f)
        assert(roundtrip(3.14f, serde) == 3.14f)
        assert(roundtrip(-1.5f, serde) == -1.5f)
    }

    test("Double roundtrip") {
        val serde = JsonSerde.derived[Double]
        assert(roundtrip(0.0, serde) == 0.0)
        assert(roundtrip(3.14159, serde) == 3.14159)
        assert(roundtrip(-99.99, serde) == -99.99)
    }

    test("Boolean roundtrip") {
        val serde = JsonSerde.derived[Boolean]
        assert(roundtrip(true, serde) == true)
        assert(roundtrip(false, serde) == false)
    }

    test("String roundtrip") {
        val serde = summon[JsonSerde[String]]
        assert(roundtrip("hello", serde) == "hello")
        assert(roundtrip("", serde) == "")
        assert(roundtrip("hello world", serde) == "hello world")
    }

    test("String with escape sequences roundtrip") {
        val serde = summon[JsonSerde[String]]
        assert(roundtrip("hello\nworld", serde) == "hello\nworld")
        assert(roundtrip("tab\there", serde) == "tab\there")
        assert(roundtrip("quote\"inside", serde) == "quote\"inside")
        assert(roundtrip("back\\slash", serde) == "back\\slash")
        assert(roundtrip("中文", serde) == "中文")
        assert(roundtrip("emoji: \uD83D\uDE00", serde) == "emoji: \uD83D\uDE00")
    }

    // ==================== Option ====================

    test("Option[Int] roundtrip - Some") {
        val serde = JsonSerde.derived[Option[Int]]
        assert(roundtrip(Some(42), serde) == Some(42))
    }

    test("Option[Int] roundtrip - None") {
        val serde = JsonSerde.derived[Option[Int]]
        assert(roundtrip(None, serde) == None)
    }

    test("Option[String] roundtrip") {
        val serde = JsonSerde.derived[Option[String]]
        assert(roundtrip(Some("hello"), serde) == Some("hello"))
        assert(roundtrip(None, serde) == None)
    }

    // ==================== Tuple ====================

    test("Tuple2 roundtrip") {
        val serde = JsonSerde.derived[(String, Int)]
        val result = roundtrip(("hello", 42), serde)
        assert(result._1 == "hello")
        assert(result._2 == 42)
    }

    test("Tuple4 roundtrip") {
        val serde = JsonSerde.derived[(String, Int, Long, Option[Long])]
        val result = roundtrip(("hello", 91, 56789L, Some(90L)), serde)
        assert(result._1 == "hello")
        assert(result._2 == 91)
        assert(result._3 == 56789L)
        assert(result._4 == Some(90L))
    }

    // ==================== Collections ====================

    test("Array[Int] roundtrip") {
        val serde = JsonSerde.derived[Array[Int]]
        val result = roundtrip(Array(1, 2, 3, 4, 5), serde)
        assert(result.sameElements(Array(1, 2, 3, 4, 5)))
    }

    test("Array[Int] empty roundtrip") {
        val serde = JsonSerde.derived[Array[Int]]
        val result = roundtrip(Array.empty[Int], serde)
        assert(result.isEmpty)
    }

    test("Array[String] roundtrip") {
        val serde = JsonSerde.derived[Array[String]]
        val result = roundtrip(Array("a", "b", "c"), serde)
        assert(result.sameElements(Array("a", "b", "c")))
    }

    test("List[Int] roundtrip") {
        val serde = JsonSerde.derived[List[Int]]
        assert(roundtrip(List(1, 2, 3), serde) == List(1, 2, 3))
    }

    test("List[Int] empty roundtrip") {
        val serde = JsonSerde.derived[List[Int]]
        assert(roundtrip(List.empty[Int], serde) == List.empty[Int])
    }

    test("List[String] roundtrip") {
        val serde = JsonSerde.derived[List[String]]
        assert(roundtrip(List("hello", "world"), serde) == List("hello", "world"))
    }

    test("IndexedSeq[Int] roundtrip") {
        val serde = JsonSerde.derived[IndexedSeq[Int]]
        assert(roundtrip(IndexedSeq(10, 20, 30), serde) == IndexedSeq(10, 20, 30))
    }

    test("Seq[Int] roundtrip") {
        val serde = JsonSerde.derived[Seq[Int]]
        assert(roundtrip(Seq(1, 2, 3), serde) == Seq(1, 2, 3))
    }

    test("mutable.ArraySeq[Int] roundtrip") {
        val serde = JsonSerde.derived[mutable.ArraySeq[Int]]
        val result = roundtrip(mutable.ArraySeq(1, 2, 3), serde)
        assert(result == mutable.ArraySeq(1, 2, 3))
    }

    test("immutable.ArraySeq[Int] roundtrip") {
        val serde = JsonSerde.derived[immutable.ArraySeq[Int]]
        val result = roundtrip(immutable.ArraySeq(1, 2, 3), serde)
        assert(result == immutable.ArraySeq(1, 2, 3))
    }

    test("IArray[Int] roundtrip") {
        val serde = JsonSerde.derived[IArray[Int]]
        val result = roundtrip(IArray(1, 2, 3), serde)
        assert(result.sameElements(IArray(1, 2, 3)))
    }

    // ==================== Map ====================

    test("Map[String, Int] roundtrip") {
        val serde = JsonSerde.derived[Map[String, Int]]
        val result = roundtrip(Map("a" -> 1, "b" -> 2), serde)
        assert(result == Map("a" -> 1, "b" -> 2))
    }

    test("Map[String, Int] empty roundtrip") {
        val serde = JsonSerde.derived[Map[String, Int]]
        assert(roundtrip(Map.empty[String, Int], serde) == Map.empty[String, Int])
    }

    test("Map[String, String] roundtrip") {
        val serde = JsonSerde.derived[Map[String, String]]
        val result = roundtrip(Map("key1" -> "val1", "key2" -> "val2"), serde)
        assert(result == Map("key1" -> "val1", "key2" -> "val2"))
    }

    // ==================== Collection serialization format ====================

    test("List serialization produces JSON array") {
        val serde = JsonSerde.derived[List[Int]]
        val json  = serializeToJson(List(1, 2, 3), serde)
        assert(json.startsWith("["))
        assert(json.endsWith("]"))
        assert(!json.startsWith("{"))
    }

    test("IndexedSeq serialization produces JSON array") {
        val serde = JsonSerde.derived[IndexedSeq[Int]]
        val json  = serializeToJson(IndexedSeq(1, 2), serde)
        assert(json.startsWith("["))
        assert(json.endsWith("]"))
    }

    test("Map serialization produces JSON object") {
        val serde = JsonSerde.derived[Map[String, Int]]
        val json  = serializeToJson(Map("a" -> 1), serde)
        assert(json.startsWith("{"))
        assert(json.endsWith("}"))
    }

    // ==================== Case Class ====================

    test("simple case class roundtrip") {
        case class Point(x: Int, y: Int) derives JsonSerde
        val serde = summon[JsonSerde[Point]]
        val result = roundtrip(Point(10, 20), serde)
        assert(result == Point(10, 20))
    }

    test("case class with String field roundtrip") {
        case class User(id: Int, name: String) derives JsonSerde
        val serde = summon[JsonSerde[User]]
        val result = roundtrip(User(1, "Tom"), serde)
        assert(result == User(1, "Tom"))
    }

    test("case class with Option field roundtrip - Some") {
        case class Item(id: Int, name: String, description: Option[String]) derives JsonSerde
        val serde = summon[JsonSerde[Item]]
        val result = roundtrip(Item(1, "widget", Some("a useful tool")), serde)
        assert(result == Item(1, "widget", Some("a useful tool")))
    }

    test("case class with Option field roundtrip - None") {
        case class Item(id: Int, name: String, description: Option[String]) derives JsonSerde
        val serde = summon[JsonSerde[Item]]
        val result = roundtrip(Item(1, "widget", None), serde)
        assert(result.id == 1)
        assert(result.name == "widget")
        assert(result.description == None)
    }

    test("case class with List field roundtrip") {
        case class Order(id: Int, items: List[String]) derives JsonSerde
        val serde = summon[JsonSerde[Order]]
        val result = roundtrip(Order(100, List("apple", "banana")), serde)
        assert(result == Order(100, List("apple", "banana")))
    }

    test("case class with nested case class roundtrip") {
        case class Address(city: String, zip: String) derives JsonSerde
        case class Person(name: String, address: Address) derives JsonSerde
        val serde = summon[JsonSerde[Person]]
        val result = roundtrip(Person("Alice", Address("Beijing", "100000")), serde)
        assert(result == Person("Alice", Address("Beijing", "100000")))
    }

    test("case class serialization format") {
        case class User(id: Int, name: String) derives JsonSerde
        val serde = summon[JsonSerde[User]]
        val json = serializeToJson(User(1, "Tom"), serde)
        assert(json.startsWith("{"))
        assert(json.endsWith("}"))
        assert(json.contains("\"id\""))
        assert(json.contains("\"name\""))
        assert(json.contains("1"))
        assert(json.contains("\"Tom\""))
    }

    // ==================== Annotations ====================

    test("case class with @rename annotation") {
        case class Renamed(@rename("full_name") name: String, age: Int) derives JsonSerde
        val serde = summon[JsonSerde[Renamed]]
        val json = serializeToJson(Renamed("Alice", 30), serde)
        assert(json.contains("\"full_name\""))
        assert(!json.contains("\"name\""))
        assert(json.contains("\"Alice\""))

        val result = roundtrip(Renamed("Alice", 30), serde)
        assert(result == Renamed("Alice", 30))
    }

    test("case class with @stringfield annotation") {
        case class Stringified(@stringfield id: Int, name: String) derives JsonSerde
        val serde = summon[JsonSerde[Stringified]]
        val json = serializeToJson(Stringified(42, "test"), serde)
        // id should be serialized as a string (quoted)
        assert(json.contains("\"42\"") || json.contains("\"id\":\"42\"") || json.contains("\"id\": \"42\""))

        val result = roundtrip(Stringified(42, "test"), serde)
        assert(result == Stringified(42, "test"))
    }

    test("case class with @ignore annotation") {
        case class WithIgnore(id: Int, @ignore secret: String) derives JsonSerde
        val serde = summon[JsonSerde[WithIgnore]]
        val json = serializeToJson(WithIgnore(1, "password"), serde)
        assert(!json.contains("secret"))
        assert(!json.contains("password"))
        assert(json.contains("\"id\""))
    }

    // ==================== Deserialize from JSON string ====================

    test("deserialize case class from JSON string") {
        case class User(id: Int, name: String) derives JsonSerde
        val serde = summon[JsonSerde[User]]
        val result = deserializeFromJson("{\"id\":1,\"name\":\"Tom\"}", serde)
        assert(result == User(1, "Tom"))
    }

    test("deserialize case class with missing Optional field") {
        case class Item(id: Int, name: String, desc: Option[String]) derives JsonSerde
        val serde = summon[JsonSerde[Item]]
        // desc field missing in JSON - should default to null (not None, due to genDefaultValue)
        val result = deserializeFromJson("{\"id\":1,\"name\":\"widget\"}", serde)
        assert(result.id == 1)
        assert(result.name == "widget")
    }

    test("deserialize case class with unknown field - should skip") {
        case class Simple(x: Int) derives JsonSerde
        val serde = summon[JsonSerde[Simple]]
        val result = deserializeFromJson("{\"x\":42,\"unknown\":\"skip me\"}", serde)
        assert(result == Simple(42))
    }

    test("deserialize List from JSON string") {
        val serde = JsonSerde.derived[List[Int]]
        val result = deserializeFromJson("[1,2,3]", serde)
        assert(result == List(1, 2, 3))
    }

    test("deserialize Array from JSON string") {
        val serde = JsonSerde.derived[Array[String]]
        val result = deserializeFromJson("[\"a\",\"b\",\"c\"]", serde)
        assert(result.sameElements(Array("a", "b", "c")))
    }

    test("deserialize Map from JSON string") {
        val serde = JsonSerde.derived[Map[String, Int]]
        val result = deserializeFromJson("{\"a\":1,\"b\":2}", serde)
        assert(result == Map("a" -> 1, "b" -> 2))
    }

    // ==================== Complex nested types ====================

    test("case class with Array of case class") {
        case class Item(name: String, price: Double) derives JsonSerde
        case class Order(id: Int, items: Array[Item]) derives JsonSerde
        val serde = summon[JsonSerde[Order]]
        val order = Order(1, Array(Item("apple", 1.5), Item("banana", 2.0)))
        val result = roundtrip(order, serde)
        assert(result.id == 1)
        assert(result.items.length == 2)
        assert(result.items(0).name == "apple")
        assert(result.items(1).name == "banana")
    }

    test("case class with Map") {
        case class Config(name: String, props: Map[String, String]) derives JsonSerde
        val serde = summon[JsonSerde[Config]]
        val result = roundtrip(Config("app", Map("key1" -> "val1", "key2" -> "val2")), serde)
        assert(result == Config("app", Map("key1" -> "val1", "key2" -> "val2")))
    }

    test("case class with Option[List]") {
        case class Container(id: Int, items: Option[List[Int]]) derives JsonSerde
        val serde = summon[JsonSerde[Container]]

        val r1 = roundtrip(Container(1, Some(List(1, 2, 3))), serde)
        assert(r1.id == 1)
        assert(r1.items == Some(List(1, 2, 3)))

        val r2 = roundtrip(Container(2, None), serde)
        assert(r2.id == 2)
        assert(r2.items == None)
    }

    // ==================== Time types ====================

    test("LocalDate roundtrip") {
        val serde = summon[JsonSerde[LocalDate]]
        val date  = LocalDate.of(2024, 7, 1)
        assert(roundtrip(date, serde) == date)
    }

    test("Instant roundtrip") {
        val serde = summon[JsonSerde[Instant]]
        val inst  = Instant.parse("2024-07-01T12:30:45Z")
        assert(roundtrip(inst, serde) == inst)
    }

    test("YearMonth roundtrip") {
        val serde = summon[JsonSerde[YearMonth]]
        val ym    = YearMonth.of(2024, 7)
        assert(roundtrip(ym, serde) == ym)
    }

    test("LocalDateTime roundtrip") {
        val serde = summon[JsonSerde[LocalDateTime]]
        val ldt   = LocalDateTime.of(2024, 7, 1, 12, 30, 45)
        assert(roundtrip(ldt, serde) == ldt)
    }

    // ==================== BigDecimal / BigInteger ====================

    test("BigInt roundtrip") {
        val serde = summon[JsonSerde[BigInt]]
        assert(roundtrip(BigInt(12345), serde) == BigInt(12345))
        assert(roundtrip(BigInt("99999999999999999999"), serde) == BigInt("99999999999999999999"))
    }

    test("BigInteger roundtrip") {
        val serde = summon[JsonSerde[java.math.BigInteger]]
        val bi    = new java.math.BigInteger("123456789012345678901234567890")
        assert(roundtrip(bi, serde) == bi)
    }

    // ==================== Char ====================

    test("Char roundtrip") {
        val serde = JsonSerde.derived[Char]
        assert(roundtrip('A', serde) == 'A')
        assert(roundtrip('z', serde) == 'z')
        assert(roundtrip('0', serde) == '0')
    }

    test("Char special characters roundtrip") {
        val serde = JsonSerde.derived[Char]
        assert(roundtrip('\n', serde) == '\n')
        assert(roundtrip('\t', serde) == '\t')
        assert(roundtrip('\"', serde) == '\"')
        assert(roundtrip('\\', serde) == '\\')
    }

    test("Char unicode roundtrip") {
        val serde = JsonSerde.derived[Char]
        assert(roundtrip('好', serde) == '好')
    }

    // ==================== Value Class (AnyVal) ====================

    test("value class roundtrip") {
        val serde = summon[JsonSerde[Wrapper]]
        assert(roundtrip(Wrapper(42), serde) == Wrapper(42))
        assert(roundtrip(Wrapper(0), serde) == Wrapper(0))
        assert(roundtrip(Wrapper(-1), serde) == Wrapper(-1))
    }

    test("value class with Long underlying") {
        val serde = summon[JsonSerde[Timestamp]]
        assert(roundtrip(Timestamp(123456789L), serde) == Timestamp(123456789L))
        assert(roundtrip(Timestamp(Long.MaxValue), serde) == Timestamp(Long.MaxValue))
    }

    test("value class in case class field") {
        val serde = summon[JsonSerde[Distance]]
        val result = roundtrip(Distance("road", Meter(42.5)), serde)
        assert(result.name == "road")
        assert(result.length.value == 42.5)
    }

    // ==================== BigDecimal / JBigDecimal via JsonSerde ====================

    test("BigDecimal roundtrip via JsonSerde") {
        val serde = summon[JsonSerde[BigDecimal]]
        assert(roundtrip(BigDecimal("123.456"), serde) == BigDecimal("123.456"))
        assert(roundtrip(BigDecimal("-0.001"), serde) == BigDecimal("-0.001"))
        assert(roundtrip(BigDecimal("99999999999999999999.9999999999"), serde) == BigDecimal("99999999999999999999.9999999999"))
    }

    test("JBigDecimal roundtrip via JsonSerde") {
        val serde = summon[JsonSerde[java.math.BigDecimal]]
        val bd = new java.math.BigDecimal("3.141592653589793")
        val result = roundtrip(bd, serde)
        assert(result.compareTo(bd) == 0)
    }

    test("case class with BigDecimal field") {
        case class Product(name: String, price: BigDecimal) derives JsonSerde
        val serde = summon[JsonSerde[Product]]
        val result = roundtrip(Product("widget", BigDecimal("19.99")), serde)
        assert(result.name == "widget")
        assert(result.price == BigDecimal("19.99"))
    }

    test("case class with BigInteger field") {
        case class BigId(name: String, id: java.math.BigInteger) derives JsonSerde
        val serde = summon[JsonSerde[BigId]]
        val bi = new java.math.BigInteger("999999999999999999999999999")
        val result = roundtrip(BigId("huge", bi), serde)
        assert(result.name == "huge")
        assert(result.id == bi)
    }

    // ==================== Tuple3 ====================

    test("Tuple3 roundtrip") {
        val serde = JsonSerde.derived[(Int, String, Boolean)]
        val result = roundtrip((1, "hello", true), serde)
        assert(result._1 == 1)
        assert(result._2 == "hello")
        assert(result._3 == true)
    }

    test("Tuple1 roundtrip") {
        val serde = JsonSerde.derived[Tuple1[Int]]
        val result = roundtrip(Tuple1(42), serde)
        assert(result._1 == 42)
    }

    // ==================== Nested Collections ====================

    test("List[List[Int]] roundtrip") {
        val serde = JsonSerde.derived[List[List[Int]]]
        val result = roundtrip(List(List(1, 2), List(3, 4, 5), List.empty), serde)
        assert(result == List(List(1, 2), List(3, 4, 5), List.empty))
    }

    test("Map[String, List[Int]] roundtrip") {
        val serde = JsonSerde.derived[Map[String, List[Int]]]
        val result = roundtrip(Map("a" -> List(1, 2), "b" -> List.empty), serde)
        assert(result == Map("a" -> List(1, 2), "b" -> List.empty))
    }

    test("Option[case class] roundtrip") {
        case class Inner(x: Int) derives JsonSerde
        val serde = JsonSerde.derived[Option[Inner]]
        assert(roundtrip(Some(Inner(42)), serde) == Some(Inner(42)))
        assert(roundtrip(None, serde) == None)
    }

    // ==================== Case class with all primitive types ====================

    test("case class with all primitive types roundtrip") {
        case class AllTypes(
            b: Byte,
            s: Short,
            i: Int,
            l: Long,
            f: Float,
            d: Double,
            bool: Boolean,
            c: Char,
            str: String
        ) derives JsonSerde
        val serde = summon[JsonSerde[AllTypes]]
        val value = AllTypes(1.toByte, 2.toShort, 3, 4L, 5.0f, 6.0, true, 'X', "hello")
        val result = roundtrip(value, serde)
        assert(result.b == 1.toByte)
        assert(result.s == 2.toShort)
        assert(result.i == 3)
        assert(result.l == 4L)
        assert(result.f == 5.0f)
        assert(result.d == 6.0)
        assert(result.bool == true)
        assert(result.c == 'X')
        assert(result.str == "hello")
    }

    // ==================== Empty case class ====================

    test("empty case class roundtrip") {
        case class Empty() derives JsonSerde
        val serde = summon[JsonSerde[Empty]]
        val result = roundtrip(Empty(), serde)
        assert(result == Empty())
    }

    test("empty case class serialization format") {
        case class Empty2() derives JsonSerde
        val serde = summon[JsonSerde[Empty2]]
        val json = serializeToJson(Empty2(), serde)
        assert(json == "{}")
    }

    // ==================== Case class with @ignore in middle ====================

    test("case class with @ignore field in middle position") {
        case class Mixed(id: Int, @ignore internal: String, name: String) derives JsonSerde
        val serde = summon[JsonSerde[Mixed]]
        val json = serializeToJson(Mixed(1, "secret", "Tom"), serde)
        assert(!json.contains("internal"))
        assert(!json.contains("secret"))
        assert(json.contains("\"id\""))
        assert(json.contains("\"name\""))

        val result = roundtrip(Mixed(1, "ignored", "Tom"), serde)
        assert(result.id == 1)
        assert(result.name == "Tom")
    }

    // ==================== Multiple @rename ====================

    test("case class with multiple @rename annotations") {
        case class MultiRename(
            @rename("first_name") firstName: String,
            @rename("last_name") lastName: String,
            age: Int
        ) derives JsonSerde
        val serde = summon[JsonSerde[MultiRename]]
        val json = serializeToJson(MultiRename("Alice", "Smith", 30), serde)
        assert(json.contains("\"first_name\""))
        assert(json.contains("\"last_name\""))
        assert(!json.contains("\"firstName\""))
        assert(!json.contains("\"lastName\""))

        val result = roundtrip(MultiRename("Alice", "Smith", 30), serde)
        assert(result.firstName == "Alice")
        assert(result.lastName == "Smith")
        assert(result.age == 30)
    }

    // ==================== Deserialize with whitespace ====================

    test("deserialize case class with spaces between entries") {
        case class WsItem(id: Int, name: String) derives JsonSerde
        val serde = summon[JsonSerde[WsItem]]
        val result = deserializeFromJson("{\"id\": 1 , \"name\": \"Tom\" }", serde)
        assert(result == WsItem(1, "Tom"))
    }

    test("deserialize case class with liberal whitespace") {
        case class WsData(x: Int, y: String, z: Boolean) derives JsonSerde
        val serde = summon[JsonSerde[WsData]]
        val result = deserializeFromJson("{ \"x\" : 42 , \"y\" : \"hello\" , \"z\" : true }", serde)
        assert(result == WsData(42, "hello", true))
    }

    // ==================== Deserialize with field reordering ====================

    test("deserialize case class with fields in different order") {
        case class Pair(x: Int, y: Int) derives JsonSerde
        val serde = summon[JsonSerde[Pair]]
        val result = deserializeFromJson("{\"y\":20,\"x\":10}", serde)
        assert(result == Pair(10, 20))
    }

    // ==================== Null handling ====================

    test("deserialize null as Option None") {
        val serde = JsonSerde.derived[Option[String]]
        val result = deserializeFromJson("null", serde)
        assert(result == None)
    }

    // ==================== Long boundary values via JsonSerde ====================

    test("Long boundary values via JsonSerde") {
        val serde = JsonSerde.derived[Long]
        assert(roundtrip(Long.MaxValue, serde) == Long.MaxValue)
        assert(roundtrip(Long.MinValue, serde) == Long.MinValue)
    }

    // ==================== Case class with Tuple field ====================

    test("case class with Tuple2 field") {
        case class Range(bounds: (Int, Int)) derives JsonSerde
        val serde = summon[JsonSerde[Range]]
        val result = roundtrip(Range((0, 100)), serde)
        assert(result.bounds._1 == 0)
        assert(result.bounds._2 == 100)
    }

    // ==================== Deserialize empty collections from JSON ====================

    test("deserialize empty List from JSON string") {
        val serde = JsonSerde.derived[List[Int]]
        val result = deserializeFromJson("[]", serde)
        assert(result == List.empty[Int])
    }

    test("deserialize empty Map from JSON string") {
        val serde = JsonSerde.derived[Map[String, Int]]
        val result = deserializeFromJson("{}", serde)
        assert(result == Map.empty[String, Int])
    }

    // ==================== Array of complex types ====================

    test("Array[Double] roundtrip") {
        val serde = JsonSerde.derived[Array[Double]]
        val result = roundtrip(Array(1.1, 2.2, 3.3), serde)
        assert(result.sameElements(Array(1.1, 2.2, 3.3)))
    }

    test("Array[Long] roundtrip") {
        val serde = JsonSerde.derived[Array[Long]]
        val result = roundtrip(Array(Long.MaxValue, 0L, Long.MinValue), serde)
        assert(result.sameElements(Array(Long.MaxValue, 0L, Long.MinValue)))
    }

    // ==================== Single-element collections ====================

    test("List single element roundtrip") {
        val serde = JsonSerde.derived[List[Int]]
        val result = deserializeFromJson("[42]", serde)
        assert(result == List(42))
    }

    test("Array single element roundtrip") {
        val serde = JsonSerde.derived[Array[Int]]
        val result = deserializeFromJson("[99]", serde)
        assert(result.sameElements(Array(99)))
    }

    test("IndexedSeq single element roundtrip") {
        val serde = JsonSerde.derived[IndexedSeq[String]]
        val result = deserializeFromJson("[\"only\"]", serde)
        assert(result == IndexedSeq("only"))
    }

    test("Seq single element roundtrip") {
        val serde = JsonSerde.derived[Seq[Double]]
        val result = deserializeFromJson("[1.5]", serde)
        assert(result == Seq(1.5))
    }

    test("mutable.ArraySeq single element") {
        val serde = JsonSerde.derived[mutable.ArraySeq[Int]]
        val result = deserializeFromJson("[7]", serde)
        assert(result == mutable.ArraySeq(7))
    }

    test("immutable.ArraySeq single element") {
        val serde = JsonSerde.derived[immutable.ArraySeq[Int]]
        val result = deserializeFromJson("[7]", serde)
        assert(result == immutable.ArraySeq(7))
    }

    test("IArray single element") {
        val serde = JsonSerde.derived[IArray[Int]]
        val result = deserializeFromJson("[7]", serde)
        assert(result.sameElements(IArray(7)))
    }

    test("Map single entry roundtrip") {
        val serde = JsonSerde.derived[Map[String, Int]]
        val result = deserializeFromJson("{\"k\":1}", serde)
        assert(result == Map("k" -> 1))
    }

    // ==================== @stringfield on various types ====================

    test("@stringfield on Long") {
        case class StrLong(@stringfield id: Long, name: String) derives JsonSerde
        val serde = summon[JsonSerde[StrLong]]
        val json = serializeToJson(StrLong(123456789L, "test"), serde)
        assert(json.contains("\"123456789\""))
        val result = roundtrip(StrLong(123456789L, "test"), serde)
        assert(result == StrLong(123456789L, "test"))
    }

    test("@stringfield on Boolean") {
        case class StrBool(@stringfield active: Boolean, name: String) derives JsonSerde
        val serde = summon[JsonSerde[StrBool]]
        val json = serializeToJson(StrBool(true, "test"), serde)
        assert(json.contains("\"true\""))
        val result = roundtrip(StrBool(true, "test"), serde)
        assert(result == StrBool(true, "test"))
    }

    test("@stringfield on Double") {
        case class StrDouble(@stringfield score: Double, name: String) derives JsonSerde
        val serde = summon[JsonSerde[StrDouble]]
        val json = serializeToJson(StrDouble(3.14, "test"), serde)
        assert(json.contains("\"3.14\""))
        val result = roundtrip(StrDouble(3.14, "test"), serde)
        assert(result.score == 3.14)
    }

    test("@stringfield on Short") {
        case class StrShort(@stringfield code: Short, name: String) derives JsonSerde
        val serde = summon[JsonSerde[StrShort]]
        val json = serializeToJson(StrShort(100.toShort, "test"), serde)
        assert(json.contains("\"100\""))
        val result = roundtrip(StrShort(100.toShort, "test"), serde)
        assert(result == StrShort(100.toShort, "test"))
    }

    test("@stringfield on Byte") {
        case class StrByte(@stringfield flags: Byte, name: String) derives JsonSerde
        val serde = summon[JsonSerde[StrByte]]
        val json = serializeToJson(StrByte(5.toByte, "test"), serde)
        assert(json.contains("\"5\""))
        val result = roundtrip(StrByte(5.toByte, "test"), serde)
        assert(result == StrByte(5.toByte, "test"))
    }

    test("@stringfield on Float") {
        case class StrFloat(@stringfield rate: Float, name: String) derives JsonSerde
        val serde = summon[JsonSerde[StrFloat]]
        val json = serializeToJson(StrFloat(1.5f, "test"), serde)
        assert(json.contains("\"1.5\""))
        val result = roundtrip(StrFloat(1.5f, "test"), serde)
        assert(result.rate == 1.5f)
    }

    // ==================== Null handling for non-Option fields ====================

    test("deserialize null for String field") {
        case class WithStr(id: Int, name: String) derives JsonSerde
        val serde = summon[JsonSerde[WithStr]]
        val result = deserializeFromJson("{\"id\":1,\"name\":null}", serde)
        assert(result.id == 1)
        assert(result.name == null)
    }

    test("deserialize null for Int field defaults to 0") {
        case class WithInt(id: Int, name: String) derives JsonSerde
        val serde = summon[JsonSerde[WithInt]]
        val result = deserializeFromJson("{\"id\":null,\"name\":\"test\"}", serde)
        assert(result.id == 0)
        assert(result.name == "test")
    }

    test("deserialize null for Long field defaults to 0") {
        case class WithLong(id: Long) derives JsonSerde
        val serde = summon[JsonSerde[WithLong]]
        val result = deserializeFromJson("{\"id\":null}", serde)
        assert(result.id == 0L)
    }

    test("deserialize null for Boolean field defaults to false") {
        case class WithBool(active: Boolean) derives JsonSerde
        val serde = summon[JsonSerde[WithBool]]
        val result = deserializeFromJson("{\"active\":null}", serde)
        assert(result.active == false)
    }

    test("deserialize null for Option field is None") {
        case class WithOpt(id: Int, name: Option[String]) derives JsonSerde
        val serde = summon[JsonSerde[WithOpt]]
        val result = deserializeFromJson("{\"id\":1,\"name\":null}", serde)
        assert(result.id == 1)
        assert(result.name == None)
    }

    // ==================== Duplicate keys ====================

    test("deserialize with duplicate keys - last wins") {
        case class DupTest(x: Int, y: String) derives JsonSerde
        val serde = summon[JsonSerde[DupTest]]
        val result = deserializeFromJson("{\"x\":1,\"x\":2,\"y\":\"a\",\"y\":\"b\"}", serde)
        assert(result.x == 2)
        assert(result.y == "b")
    }

    // ==================== Missing required fields ====================

    test("deserialize missing Int field defaults to 0") {
        case class ReqInt(x: Int, name: String) derives JsonSerde
        val serde = summon[JsonSerde[ReqInt]]
        val result = deserializeFromJson("{\"name\":\"test\"}", serde)
        assert(result.x == 0)
        assert(result.name == "test")
    }

    test("deserialize missing Boolean field defaults to false") {
        case class ReqBool(active: Boolean, name: String) derives JsonSerde
        val serde = summon[JsonSerde[ReqBool]]
        val result = deserializeFromJson("{\"name\":\"test\"}", serde)
        assert(result.active == false)
        assert(result.name == "test")
    }

    test("deserialize missing Double field defaults to 0.0") {
        case class ReqDouble(score: Double, name: String) derives JsonSerde
        val serde = summon[JsonSerde[ReqDouble]]
        val result = deserializeFromJson("{\"name\":\"test\"}", serde)
        assert(result.score == 0.0)
        assert(result.name == "test")
    }

    test("deserialize missing String field defaults to null") {
        case class ReqStr(x: Int, name: String) derives JsonSerde
        val serde = summon[JsonSerde[ReqStr]]
        val result = deserializeFromJson("{\"x\":42}", serde)
        assert(result.x == 42)
        assert(result.name == null)
    }

    test("deserialize missing Long field defaults to 0") {
        case class ReqLong(id: Long, name: String) derives JsonSerde
        val serde = summon[JsonSerde[ReqLong]]
        val result = deserializeFromJson("{\"name\":\"test\"}", serde)
        assert(result.id == 0L)
        assert(result.name == "test")
    }

    // ==================== Boundary values ====================

    test("Byte boundary values") {
        val serde = JsonSerde.derived[Byte]
        assert(roundtrip(Byte.MaxValue, serde) == Byte.MaxValue)
        assert(roundtrip(Byte.MinValue, serde) == Byte.MinValue)
    }

    test("Short boundary values") {
        val serde = JsonSerde.derived[Short]
        assert(roundtrip(Short.MaxValue, serde) == Short.MaxValue)
        assert(roundtrip(Short.MinValue, serde) == Short.MinValue)
    }

    test("Float boundary values") {
        val serde = JsonSerde.derived[Float]
        assert(roundtrip(Float.MaxValue, serde) == Float.MaxValue)
        assert(roundtrip(Float.MinValue, serde) == Float.MinValue)
    }

    test("Double boundary values") {
        val serde = JsonSerde.derived[Double]
        assert(roundtrip(Double.MaxValue, serde) == Double.MaxValue)
        assert(roundtrip(Double.MinValue, serde) == Double.MinValue)
    }

    test("negative zero Float roundtrip") {
        val serde = JsonSerde.derived[Float]
        assert(roundtrip(-0.0f, serde) == -0.0f)
    }

    test("negative zero Double roundtrip") {
        val serde = JsonSerde.derived[Double]
        assert(roundtrip(-0.0, serde) == -0.0)
    }

    // ==================== String escape sequences at serde level ====================

    test("deserialize string with \\b escape") {
        val serde = summon[JsonSerde[String]]
        val result = deserializeFromJson("\"hello\\bworld\"", serde)
        assert(result == "hello\bworld")
    }

    test("deserialize string with \\f escape") {
        val serde = summon[JsonSerde[String]]
        val result = deserializeFromJson("\"page\\fbreak\"", serde)
        assert(result == "page\fbreak")
    }

    test("deserialize string with \\r escape") {
        val serde = summon[JsonSerde[String]]
        val result = deserializeFromJson("\"line1\\rline2\"", serde)
        assert(result == "line1\rline2")
    }

    test("deserialize string with \\/ escape") {
        val serde = summon[JsonSerde[String]]
        val result = deserializeFromJson("\"path\\/to\\/file\"", serde)
        assert(result == "path/to/file")
    }

    test("roundtrip string with all escape sequences") {
        val serde = summon[JsonSerde[String]]
        val str = "\b\f\r\n\t\"\\"
        assert(roundtrip(str, serde) == str)
    }

    // ==================== Value class wrapping String and Boolean ====================

    test("String value class roundtrip") {
        val serde = summon[JsonSerde[Tag]]
        assert(roundtrip(Tag("hello"), serde) == Tag("hello"))
        assert(roundtrip(Tag(""), serde) == Tag(""))
    }

    test("Boolean value class roundtrip") {
        val serde = summon[JsonSerde[Flag]]
        assert(roundtrip(Flag(true), serde) == Flag(true))
        assert(roundtrip(Flag(false), serde) == Flag(false))
    }

    test("String value class in case class") {
        case class Labeled(label: Tag, value: Int) derives JsonSerde
        val serde = summon[JsonSerde[Labeled]]
        val result = roundtrip(Labeled(Tag("important"), 42), serde)
        assert(result.label == Tag("important"))
        assert(result.value == 42)
    }

    // ==================== List[Option[Int]] with null elements ====================

    test("List[Option[Int]] with null elements") {
        val serde = JsonSerde.derived[List[Option[Int]]]
        val result = deserializeFromJson("[1,null,3]", serde)
        assert(result == List(Some(1), None, Some(3)))
    }

    test("List[Option[String]] with null elements") {
        val serde = JsonSerde.derived[List[Option[String]]]
        val result = deserializeFromJson("[\"a\",null,\"b\"]", serde)
        assert(result == List(Some("a"), None, Some("b")))
    }

    test("List[Option[Int]] roundtrip all Some") {
        val serde = JsonSerde.derived[List[Option[Int]]]
        assert(roundtrip(List(Some(1), Some(2), Some(3)), serde) == List(Some(1), Some(2), Some(3)))
    }

    test("List[Option[Int]] roundtrip mixed") {
        val serde = JsonSerde.derived[List[Option[Int]]]
        assert(roundtrip(List(Some(1), None, Some(3)), serde) == List(Some(1), None, Some(3)))
    }

    // ==================== Empty collections deserialization ====================

    test("deserialize empty IArray from JSON") {
        val serde = JsonSerde.derived[IArray[Int]]
        val result = deserializeFromJson("[]", serde)
        assert(result.isEmpty)
    }

    test("deserialize empty mutable.ArraySeq from JSON") {
        val serde = JsonSerde.derived[mutable.ArraySeq[Int]]
        val result = deserializeFromJson("[]", serde)
        assert(result.isEmpty)
    }

    test("deserialize empty immutable.ArraySeq from JSON") {
        val serde = JsonSerde.derived[immutable.ArraySeq[String]]
        val result = deserializeFromJson("[]", serde)
        assert(result.isEmpty)
    }

    test("deserialize IArray from JSON") {
        val serde = JsonSerde.derived[IArray[String]]
        val result = deserializeFromJson("[\"a\",\"b\"]", serde)
        assert(result.sameElements(IArray("a", "b")))
    }

    test("deserialize mutable.ArraySeq from JSON") {
        val serde = JsonSerde.derived[mutable.ArraySeq[Int]]
        val result = deserializeFromJson("[10,20,30]", serde)
        assert(result == mutable.ArraySeq(10, 20, 30))
    }

    test("deserialize immutable.ArraySeq from JSON") {
        val serde = JsonSerde.derived[immutable.ArraySeq[Int]]
        val result = deserializeFromJson("[10,20,30]", serde)
        assert(result == immutable.ArraySeq(10, 20, 30))
    }

    // ==================== IndexedSeq with >32 elements ====================

    test("IndexedSeq with >32 elements roundtrip") {
        val serde = JsonSerde.derived[IndexedSeq[Int]]
        val big = (1 to 50).toIndexedSeq
        assert(roundtrip(big, serde) == big)
    }

    // ==================== Malformed JSON error paths ====================

    test("deserialize case class missing closing brace throws") {
        case class SimpleErr(x: Int) derives JsonSerde
        val serde = summon[JsonSerde[SimpleErr]]
        intercept[Throwable] {
            deserializeFromJson("{\"x\":42", serde)
        }
    }

    test("deserialize List missing closing bracket throws") {
        val serde = JsonSerde.derived[List[Int]]
        intercept[Throwable] {
            deserializeFromJson("[1,2,3", serde)
        }
    }

    test("deserialize case class missing colon throws") {
        case class ColonTest(x: Int) derives JsonSerde
        val serde = summon[JsonSerde[ColonTest]]
        intercept[Throwable] {
            deserializeFromJson("{\"x\" 42}", serde)
        }
    }

    // ==================== Option[CaseClass] with nested object ====================

    test("Option[case class] deserialize Some from JSON") {
        case class Inner2(a: Int, b: String) derives JsonSerde
        val serde = JsonSerde.derived[Option[Inner2]]
        val result = deserializeFromJson("{\"a\":10,\"b\":\"hello\"}", serde)
        assert(result == Some(Inner2(10, "hello")))
    }

    test("Option[case class] deserialize null as None") {
        case class Inner3(a: Int) derives JsonSerde
        val serde = JsonSerde.derived[Option[Inner3]]
        val result = deserializeFromJson("null", serde)
        assert(result == None)
    }

    // ==================== Map with complex value types ====================

    test("Map[String, List[Int]] deserialize from JSON") {
        val serde = JsonSerde.derived[Map[String, List[Int]]]
        val result = deserializeFromJson("{\"a\":[1,2],\"b\":[]}", serde)
        assert(result == Map("a" -> List(1, 2), "b" -> List.empty))
    }

    test("Map[String, Option[Int]] roundtrip") {
        val serde = JsonSerde.derived[Map[String, Option[Int]]]
        val result = roundtrip(Map("a" -> Some(1), "b" -> None), serde)
        assert(result == Map("a" -> Some(1), "b" -> None))
    }

    // ==================== Tuple edge cases ====================

    test("Tuple5 roundtrip") {
        val serde = JsonSerde.derived[(Int, String, Boolean, Long, Double)]
        val result = roundtrip((1, "hello", true, 42L, 3.14), serde)
        assert(result._1 == 1)
        assert(result._2 == "hello")
        assert(result._3 == true)
        assert(result._4 == 42L)
        assert(result._5 == 3.14)
    }

    test("Tuple2 deserialize from JSON") {
        val serde = JsonSerde.derived[(String, Int)]
        val result = deserializeFromJson("[\"world\",99]", serde)
        assert(result._1 == "world")
        assert(result._2 == 99)
    }

    // ==================== Case class with all Option fields ====================

    test("case class with all Option fields - all None") {
        case class AllOpt(a: Option[Int], b: Option[String]) derives JsonSerde
        val serde = summon[JsonSerde[AllOpt]]
        val result = deserializeFromJson("{}", serde)
        assert(result.a == None)
        assert(result.b == None)
    }

    test("case class with all Option fields - all Some") {
        case class AllOpt2(a: Option[Int], b: Option[String]) derives JsonSerde
        val serde = summon[JsonSerde[AllOpt2]]
        val result = deserializeFromJson("{\"a\":42,\"b\":\"hello\"}", serde)
        assert(result == AllOpt2(Some(42), Some("hello")))
    }

}
