package lidraughts.swiss

import lidraughts.db.dsl._

final private class SwissDirector(
    playerColl: Coll,
    pairingColl: Coll,
    pairingSystem: PairingSystem
) {
  import BsonHandlers._

  def apply(swiss: Swiss): Funit =
    for {
      players <- SwissPlayer.fields { f =>
        playerColl
          .find($doc(f.swissId -> swiss.id))
          .sort($sort asc f.number)
          .list[SwissPlayer]()
      }
      prevPairings <- SwissPairing.fields { f =>
        pairingColl
          .find($doc(f.swissId -> swiss.id))
          .sort($sort asc f.round)
          .list[SwissPairing]()
      }
    } yield {
      val pairings = pairingSystem(swiss, players, prevPairings)
      println(pairings)
    }
}
