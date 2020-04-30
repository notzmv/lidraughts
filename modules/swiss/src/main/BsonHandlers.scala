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
  implicit val swissScoreHandler = intAnyValHandler[Swiss.Score](_.double, Swiss.Score.apply)
  implicit val playerNumberHandler = intAnyValHandler[SwissPlayer.Number](_.value, SwissPlayer.Number.apply)
  implicit val roundNumberHandler = intAnyValHandler[SwissRound.Number](_.value, SwissRound.Number.apply)
  implicit val swissIdHandler = stringAnyValHandler[Swiss.Id](_.value, Swiss.Id.apply)
  implicit val playerIdHandler = stringAnyValHandler[SwissPlayer.Id](_.value, SwissPlayer.Id.apply)

  implicit val playerHandler = new BSON[SwissPlayer] {
    def reads(r: BSON.Reader) = SwissPlayer(
      _id = r.get[SwissPlayer.Id]("_id"),
      swissId = r.get[Swiss.Id]("s"),
      number = r.get[SwissPlayer.Number]("n"),
      userId = r str "u",
      rating = r int "r",
      provisional = r boolD "pr",
      points = r.get[Swiss.Points]("p"),
      score = r.get[Swiss.Score]("c")
    )
    def writes(w: BSON.Writer, o: SwissPlayer) = $doc(
      "_id" -> o._id,
      "s" -> o.swissId,
      "n" -> o.number,
      "u" -> o.userId,
      "r" -> o.rating,
      "pr" -> w.boolO(o.provisional),
      "p" -> o.points,
      "c" -> o.score
    )
  }

  implicit val pairingHandler = new BSON[SwissPairing] {
    def reads(r: BSON.Reader) =
      r.get[List[SwissPlayer.Number]]("u") match {
        case List(white, black) =>
          SwissPairing(
            _id = r str "_id",
            swissId = r.get[Swiss.Id]("s"),
            round = r.get[SwissRound.Number]("r"),
            white = white,
            black = black,
            winner = r boolO "w" map {
              case true => white
              case _ => black
            }
          )
        case _ => sys error "Invalid swiss pairing users"
      }
    def writes(w: BSON.Writer, o: SwissPairing) = $doc(
      "_id" -> o._id,
      "s" -> o.swissId,
      "r" -> o.round,
      "g" -> o.gameId,
      "u" -> o.players,
      "w" -> o.winner.map(o.white ==)
    )
  }

  implicit val swissHandler = Macros.handler[Swiss]
}
