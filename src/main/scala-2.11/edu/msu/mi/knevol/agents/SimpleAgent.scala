package edu.msu.mi.knevol.agents

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import edu.msu.mi.knevol.universe.{Node, _}

import scala.collection.{BitSet, mutable, breakOut}
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Random}


/**
 * Created by josh on 10/16/15.
 */
class SimpleAgent(name: String,
                  universe: ActorRef,
                  brainSize: Int,
                  brainDegree: Int,
                  observableSize: Int,
                  contextSize: Int,
                  numInterests: Int,
                  rewireProb: Double = .2,
                  mutationProb: Double = .1,
                  approachProb: Double = .7,
                   similarityWeight:Double = .1,
                 //note that epsilon is applied wherever this agent is attempting to
                 //make a decision about the fitness of a particular brain
                   epsilon:Double = .1) extends Actor {


  var brain = context.actorOf(SimpleAgent.createBrain(s"${name}_brain", brainSize, brainDegree))
  var nextBrain: Option[ActorRef] = None


  var localContext: List[Int] = (Random.shuffle(0 to observableSize).toList take contextSize).sortBy(x => x)
  var neighbors: Array[ActorRef] = Array.empty[ActorRef]
  //var actualFitness: Double = 0
  var currentFitness= NoisyMeasure(0.0,epsilon)
  var candidateFitness: Option[NoisyMeasure] = None
  var interests = Set.empty[BitSet]
  var neighborSimilarity: Map[ActorRef, Float] = Map.empty
  while (interests.size < numInterests) {
    interests += createRandomBitset(observableSize)
  }

  import context.dispatcher

  implicit val timeout = Timeout(20 seconds)

  var improvement = "None"

  override def receive: Receive = {

    case Wire(neighbors: Iterable[ActorRef]) =>
      this.neighbors = neighbors.toArray
      val returnTo = sender()

      //This is new!
      val bf = (brain ? Probe(interests)).mapTo[Map[BitSet, ListBuffer[BitSet]]]
      val uf = (universe ? Probe(interests)).mapTo[Map[BitSet, ListBuffer[BitSet]]]
      val neighborInterests = Future.sequence(neighbors.map(f => (f ? WhatAreYourInterests).mapTo[Set[BitSet]]))

      for {
        x <- neighborInterests
        brainPrediction <- bf
        actual <- uf
      } yield {
        updateFitness(compareSeveral(actual, brainPrediction))
        //crazy terseness.
        //for each neighbor,
        //find the best possible pairing between our interests
        //and compute average Jaccard similarity

        neighborSimilarity = neighbors.zip(
          x.map { otherI =>
            val tmp = interests.flatMap { myI =>
              otherI.map(ni => ((myI, ni), (myI & ni).size.toFloat / (myI ++ ni).size.toFloat))
            }.to[ListBuffer].sortBy { b => -b._2 }

            tmp.foldLeft(ListBuffer[((BitSet,BitSet),Float)]()) { (a, b) =>
              if (a.exists { x =>
                b._1._1 == x._1._1 || b._1._2 == x._1._2
              }) a
              else {
                a += b
              }
            }.map(_._2).sum / interests.size.toFloat
          })(breakOut): Map[ActorRef, Float]
        this.neighbors = this.neighbors.sortBy { n =>
          -neighborSimilarity(n)
        }
        //println(s"Best = ${neighborSimilarity(this.neighbors(0))}, worst = ${neighborSimilarity(this.neighbors(this.neighbors.length-1))} ")

        returnTo ! "OK"
      }


    case Learn(round: Int) =>
      val returnTo = sender()

      val bf = (brain ? Probe(interests)).mapTo[Map[BitSet, ListBuffer[BitSet]]]
      val uf = (universe ? Probe(interests)).mapTo[Map[BitSet, ListBuffer[BitSet]]]
      //val neighborsToExamine = selectNeighbors()
     // val nf = Future.sequence(for (n <- neighborsToExamine) yield (n ? Probe(interests)).mapTo[Map[BitSet, ListBuffer[BitSet]]])

      val nf = Future.sequence(neighbors.toList.map(n=>(n?WhatIsYourFitness).mapTo[Double]))
      // val nf = (neighbor ? Probe(interests)).mapTo[Map[BitSet, ListBuffer[mutable.BitSet]]]


      for {
        brainPrediction <- bf
        neighborFiteness <- nf
        actual <- uf
      } yield {
        //my current fitness on the new probe
        updateFitness(compareSeveral(actual, brainPrediction))


        //Pick the best neighbor that beats my current fitness
        var best:Option[ActorRef] = None
        (neighbors zip (neighbors.map(neighborSimilarity(_)) zip neighborFiteness).map(x=>getAgentDesirability(x._1,x._2))).foldLeft(currentFitness.perceived) {
          (bestFitness:Double,candidate:(ActorRef,Double)) =>
            if (bestFitness<candidate._2) {
              best = Some(candidate._1)
              candidate._2
            }
            bestFitness
        }

        //The following gives me a new candidate brain


        if (best.isDefined) {
          improvement = "Neightbor"

          attemptTolearnFromNeighbor(best.get)
        } else {
          improvement = "Myself"
          attemptTolearnByMyself()
        }
      } onComplete {
        case Success(x) =>
          //I ask the new brain about the current probe
          (x ? Probe(interests)).mapTo[Map[BitSet, ListBuffer[BitSet]]] onComplete {

            //And check what I get
            case Success(y) =>
              val candidate = NoisyMeasure(compareSeveral(y,actual))
              if (candidate.perceived > currentFitness.perceived) {
                nextBrain = Some(x)
                candidateFitness = Some(candidate)
                context.parent ! AgentUpdate(-1, round, name, interests, candidate.actual.toFloat, (candidate.actual - currentFitness.actual).toFloat, Some(improvement))
              } else {
                context.stop(x)
                candidateFitness = None
                nextBrain = None
                context.parent ! AgentUpdate(-1, round, name, interests, currentFitness.actual.toFloat, 0f, None)

              }
              returnTo ! "OK"

            case Failure(x) =>
              println(s"Failed to probe my brain $x")

          }

        case Failure(x) =>
          println(s"Failed to get a new brain $x")
          x.printStackTrace()

      }


    case Update =>
      nextBrain match {
        case Some(x) =>
          context.stop(brain)
          //at this point, the candidate fitness is already noisy
          currentFitness = candidateFitness.getOrElse(NoisyMeasure(0.0,epsilon))
          brain = x

        // println(s"$name IMPROVES: $improvement")

        case None =>
        //println(s"$name NO IMPROVEMENTS")

      }
      sender ! "OK"


    case Probe(states: Set[BitSet]) =>
      val s = sender()
      brain ? Probe(states) onComplete {
        case Success(result) =>
          s ! result

        case Failure(result) =>
          println(s"Failure probing my own brain? $result")
      }

    case WhatIsYourFitness =>
      sender() ! currentFitness.perceived

    case WhatAreYourInterests =>
      sender() ! interests

    case GiveMeYourBrain =>
      brain forward GiveMeYourBrain

  }

  def noisifyFitness(value:Double):Double = {
    Math.max(Math.min(value + (1.0-Random.nextDouble()*2)*epsilon,1.0),0.0)
  }


  def updateFitness(calculation:Double): Unit = {

    currentFitness = NoisyMeasure(calculation,epsilon)
  }

  def getAgentDesirability(similarity:Double, fitness:Double)=similarityWeight*similarity + (1-similarityWeight)*fitness

  def attemptTolearnByMyself(): Future[ActorRef] = {
    for {
      mNodes <- (brain ? GiveMeYourBrain).mapTo[Array[Node]]
    } yield {
      mNodes.foreach(_.mutateEverything(rewireProb, mutationProb))
      context.actorOf(SimpleAgent.copyBrain(s"${name}_brain", brainSize, brainDegree, mNodes))
    }

  }

  def attemptTolearnFromNeighbor(neighbor: ActorRef): Future[ActorRef] = {
    val neighborBrain = (neighbor ? GiveMeYourBrain).mapTo[Array[Node]]
    val myBrain = (brain ? GiveMeYourBrain).mapTo[Array[Node]]
    for {
      nNodes <- neighborBrain
      mNodes <- myBrain
    } yield {
      (mNodes zip nNodes).foreach { t =>
        if (Random.nextDouble() < approachProb) {
          t._1.copy(t._2)
        }

      }
      context.actorOf(SimpleAgent.copyBrain(s"${name}_brain", brainSize, brainDegree, mNodes))

    }


  }

  def selectNeighbors(): Seq[ActorRef] = neighbors take 5

  //def selectNeighbors(): Seq[ActorRef] = neighbors


  def compareSeveral(actual: Map[BitSet, ListBuffer[BitSet]], test: Map[BitSet, ListBuffer[BitSet]]): Double = {
    actual.keys.foldLeft(0.0) { (c, s) => c +
      compare(actual(s), test(s))

    } / actual.size
  }

  def compare(actual: Seq[BitSet], prediction: Seq[BitSet]): Double = {
    val length = Math.max(actual.length, prediction.length)
    actual.zip(prediction).foldLeft(0.0) { (i, t) => i +
      localContext.foldLeft(0.0) { (x, y) => x + (if (t._1.contains(y) == t._2.contains(y)) 1.0 else 0.0) } / localContext.length
    } / length

  }

}

case class NoisyMeasure(actual:Double, epsilon:Double=0.0) {
  val perceived:Double =  Math.max(Math.min(actual + (1.0-Random.nextDouble()*2)*epsilon,1.0),0.0)
}

object SimpleAgent {

  def copyBrain(name: String, brainSize: Int, brainDegree: Int, nodes: Array[Node]): Props = Props(new BasicUniverse(name, brainSize, nodes))

  def createBrain(name: String, brainSize: Int, brainDegree: Int): Props = Props(new BasicUniverse(name, brainSize, brainDegree, brainSize))


}


