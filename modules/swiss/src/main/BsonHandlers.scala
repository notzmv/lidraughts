package lidraughts.swiss

import draughts.Clock.{ Config => ClockConfig }
import draughts.variant.Variant
import lidraughts.db.BSON
import lidraughts.db.dsl._
import reactivemongo.bson._

private object BsonHandlers {

  private[swiss] implicit val statusBSONHandler: BSONHandler[BSONInteger, Status] = new BSONHandler[BSONInteger, Status] {
    def read(bsonInt: BSONInteger): Status = Status(bsonInt.value) err s"No such status: ${bsonInt.value}"
    def write(x: Status) = BSONInteger(x.id)
  }

  implicit val clockHandler = new BSONHandler[BSONDocument, ClockConfig] {
    def read(doc: BSONDocument) = ClockConfig(
      doc.getAs[Int]("limit").get,
      doc.getAs[Int]("increment").get
    )

    def write(config: ClockConfig) = BSONDocument(
      "limit" -> config.limitSeconds,
      "increment" -> config.incrementSeconds
    )
  }
  implicit val variantHandler = new BSONHandler[BSONInteger, Variant] {
    def read(b: BSONInteger): Variant = Variant.orDefault(b.value)
    def write(x: Variant) = BSONInteger(x.id)
  }

  implicit val swissPointsHandler = intAnyValHandler[Swiss.Points](_.double, Swiss.Points.apply)

  implicit val playerNumberHandler = intAnyValHandler[SwissPlayer.Number](_.value, SwissPlayer.Number.apply)
  implicit val playerIdHandler = new BSONHandler[BSONString, SwissPlayer.Id] {
    def read(b: BSONString): SwissPlayer.Id = (b.value split ':' match {
      case Array(swissId, number) =>
        parseIntOption(number) map { n =>
          SwissPlayer.Id(Swiss.Id(swissId), SwissPlayer.Number(n))
        }
      case _ => None
    }) err s"Invalid player ID $b"
    def write(id: SwissPlayer.Id) = BSONString(s"${id.swissId}:${id.number}")
  }

  implicit val playerHandler = new BSON[SwissPlayer] {
    def reads(r: BSON.Reader) = SwissPlayer(
      id = r.get[SwissPlayer.Id]("_id"),
      userId = r str "uid",
      rating = r int "r",
      provisional = r boolD "pr",
      points = r.get[Swiss.Points]("p")
    )
    def writes(w: BSON.Writer, o: SwissPlayer) = $doc(
      "_id" -> o.id,
      "uid" -> o.userId,
      "r" -> o.rating,
      "pr" -> w.boolO(o.provisional),
      "p" -> o.points
    )
  }

  implicit val pairingHandler = new BSON[SwissPairing] {
    def reads(r: BSON.Reader) = {
      val white = r.get[SwissPlayer.Number]("w")
      val black = r.get[SwissPlayer.Number]("b")
      SwissPairing(
        gameId = r str "g",
        white = white,
        black = black,
        winner = r boolO "w" map {
          case true => white
          case _ => black
        }
      )
    }
    def writes(w: BSON.Writer, o: SwissPairing) = $doc(
      "g" -> o.gameId,
      "w" -> o.white,
      "b" -> o.black,
      "w" -> o.winner.map(o.white ==)
    )
  }

  implicit val roundNumberHandler = intAnyValHandler[SwissRound.Number](_.value, SwissRound.Number.apply)
  implicit val roundIdHandler = new BSONHandler[BSONString, SwissRound.Id] {
    def read(b: BSONString): SwissRound.Id = (b.value split ':' match {
      case Array(swissId, number) =>
        parseIntOption(number) map { n =>
          SwissRound.Id(Swiss.Id(swissId), SwissRound.Number(n))
        }
      case _ => None
    }) err s"Invalid round ID $b"
    def write(id: SwissRound.Id) = BSONString(id.toString)
  }

  implicit val roundHandler = new BSON[SwissRound] {
    def reads(r: BSON.Reader) =
      SwissRound(
        id = r.get[SwissRound.Id]("_id"),
        pairings = r.get[List[SwissPairing]]("p")
      // byes = r.get[List[SwissPlayer.Number]]("b")
      )
    def writes(w: BSON.Writer, o: SwissRound) = $doc(
      "id" -> o.id,
      "p" -> o.pairings
    // "b"  -> o.byes
    )
  }

  implicit val swissIdHandler = stringAnyValHandler[Swiss.Id](_.value, Swiss.Id.apply)
  implicit val swissHandler = Macros.handler[Swiss]
}
