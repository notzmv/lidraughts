package draughts
package format

class MoveGenPerfTest extends DraughtsTest {

  args(skipAll = false)

  val captureFen = "W:WK23:B28,17,10,20,29,39,40,38,31,8,9"
  val extremeCaptureMan = "W:W23:B7,8,9,10,17,18,19,20,27,28,29,30,37,38,39,40"
  val extremeCaptureKing = "W:WK23:B8,9,10,11,17,18,19,20,27,28,29,30,31,38,39,42,44"
  val extremeFrisianKing = "W:WK50:B3,7,10,12,13,14,17,20,21,23,25,30,32,36,38,39,41,43,K47"
  val extremeFrisianKing2 = "W:WK5:BK2,K4,K7,K8,K9,K10,K11,K13,K15,K16,K18,K19,K20,K21,K22,K24,K27,K29,K30,K31,K32,K33,K35,K36,K38,K40,K41,K42,K43,K44,K47,K49"
  val extremeFrisianMan = "W:W50:B18,22,23,24,27,29,32,33,34,38,40,42,43,44,K49"
  val extremeFrisianMan2 = "W:W50:B7,9,11,12,13,14,16,18,20,21,22,23,24,27,29,31,32,33,34,36,38,40,41,42,43,44,K47,K49"

  "generate frisian moves" should {
    "once" in {
      val nb = 50
      val iterations = 100
      def runOne = Forsyth.<<@(draughts.variant.Frisian, extremeFrisianKing2).get.validMoves
      def run(its: Int) { for (i ← 1 to its) runOne }
      println("warming up")
      if (nb * iterations > 1) {
        run(nb * 4)
      }
      println("running tests")
      val durations = for (i ← 1 to iterations) yield {
        val start = System.currentTimeMillis
        run(nb)
        val duration = System.currentTimeMillis - start
        println(s"$nb positions in $duration ms")
        duration
      }
      val nbPositions = iterations * nb
      val moveNanos = (1000000 * durations.sum) / nbPositions
      println(s"Average = $moveNanos nanoseconds per position")
      println(s"          ${1000000000 / moveNanos} positions per second")
      true === true
    }
  }

  /*"generate moves" should {
    "many times" in {
      val nb = 250
      val iterations = 30
      def runOne = (Forsyth << captureFen).get.validMoves
      def run(its: Int) { for (i ← 1 to its) runOne }
      runOne.size must_== 1
      runOne.head._2.size must_== 1
      runOne.head._2.head.capture.get.size must_== 10
      if (nb * iterations > 1) {
        println("warming up")
        run(nb * 20)
      }
      println("running tests")
      val durations = for (i ← 1 to iterations) yield {
        val start = System.currentTimeMillis
        run(nb)
        val duration = System.currentTimeMillis - start
        println(s"$nb positions in $duration ms")
        duration
      }
      val nbPositions = iterations * nb
      val moveNanos = (1000000 * durations.sum) / nbPositions
      println(s"Average = $moveNanos nanoseconds per position")
      println(s"          ${1000000000 / moveNanos} positions per second")
      true === true
    }
  }*/

}