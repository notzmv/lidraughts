package lidraughts.swiss

// https://www.fide.com/FIDE/handbook/C04Annex2_TRF16.pdf
final class SwissTrf(
    sheetApi: SwissSheetApi,
    rankingApi: SwissRankingApi,
    baseUrl: String
) {

  private type Bits = List[(Int, String)]

  def apply(swiss: Swiss): Fu[List[String]] =
    rankingApi(swiss) flatMap { apply(swiss, _) }

  def apply(swiss: Swiss, ranking: Ranking): Fu[List[String]] =
    sheetApi.source(swiss).map { lines =>
      tournamentLines(swiss) ::: lines
        .map((playerLine(swiss, ranking) _).tupled)
        .map(formatLine)
    }

  private def tournamentLines(swiss: Swiss) =
    List(
      s"012 ${swiss.name}",
      s"022 ${baseUrl}/swiss/${swiss.id}",
      s"032 Lidraughts",
      s"042 ${dateFormatter print swiss.startsAt}",
      s"052 ${swiss.finishedAt ?? dateFormatter.print}",
      s"062 ${swiss.nbPlayers}",
      s"092 Individual: Swiss-System",
      s"102 ${baseUrl}/swiss",
      s"XXR ${swiss.settings.nbRounds}",
      s"XXC ${draughts.Color(swiss.id.value(0).toInt % 2 == 0).name}1"
    )

  private def playerLine(
    swiss: Swiss,
    ranking: Ranking
  )(p: SwissPlayer, pairings: Map[SwissRound.Number, SwissPairing], sheet: SwissSheet): Bits =
    List(
      3 -> "001",
      8 -> ranking.getOrElse(p.userId, 0).toString,
      47 -> p.userId,
      84 -> f"${sheet.points.value}%1.1f"
    ) ::: {
        swiss.allRounds.zip(sheet.outcomes).flatMap {
          case (rn, outcome) =>
            val pairing = pairings get rn
            List(
              95 -> pairing.map(_ opponentOf p.userId).flatMap(ranking.get).??(_.toString),
              97 -> pairing.map(_ colorOf p.userId).??(_.fold("w", "b")),
              99 -> {
                import SwissSheet._
                outcome match {
                  case Absent => "-"
                  case Late => "H"
                  case Bye => "F"
                  case Draw => "="
                  case Win => "1"
                  case Loss => "0"
                  case Ongoing => "Z"
                }
              }
            ).map { case (l, s) => (l + (rn.value - 1) * 10, s) }
        }
      } ::: p.absent.?? {
        List( // http://www.rrweb.org/javafo/aum/JaVaFo2_AUM.htm#_Unusual_info_extensions
          95 -> "0000",
          97 -> "",
          99 -> "-"
        ).map { case (l, s) => (l + swiss.round.value * 10, s) }
      }

  private def formatLine(bits: Bits): String =
    bits.foldLeft("") {
      case (acc, (pos, txt)) => s"""$acc${" " * (pos - txt.size - acc.size)}$txt"""
    }

  private val dateFormatter = org.joda.time.format.DateTimeFormat forStyle "M-"
}
