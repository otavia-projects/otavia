package app.model

import cc.otavia.json.JsonSerde
import cc.otavia.sql.{Row, RowCodec}

/** The model for the "fortune" database table. */
case class Fortune(id: Int, message: String) extends Row derives RowCodec, JsonSerde

case class Message(message: String) derives JsonSerde

/** The model for the "world" database table. */
case class World(id: Int, randomNumber: Int) extends Row derives RowCodec, JsonSerde