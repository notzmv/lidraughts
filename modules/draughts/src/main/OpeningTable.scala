package draughts

case class OpeningTable(key: String, name: String, url: String, positions: List[StartingPosition]) {

  lazy val shuffled = new scala.util.Random(475592).shuffle(positions).toIndexedSeq

  def randomOpening: (Int, StartingPosition) = {
    val index = scala.util.Random.nextInt(shuffled.size)
    index -> shuffled(index)
  }
}

object OpeningTable {

  import StartingPosition.Category

  val allTables = List(tableIDF)

  def byKey = key2table.get _
  private val key2table: Map[String, OpeningTable] = allTables.map { p =>
    p.key -> p
  }(scala.collection.breakOut)

}
