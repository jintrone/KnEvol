package edu.msu.mi.knevol.agents

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import edu.msu.mi.knevol.universe.{Node, _}

import scala.collection.{BitSet, mutable, breakOut}
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
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
                  mutationProb: Double = .3,
                  approachProb: Double = .7) extends Actor {


  var brain = context.actorOf(SimpleAgent.createBrain(s"${name}_brain", brainSize, brainDegree))
  var nextBrain: Option[ActorRef] = None


  var localContext: List[Int] = (Random.shuffle(0 to observableSize).toList take contextSize).sortBy(x => x)
  var neighbors: Array[ActorRef] = Array.empty[ActorRef]
  var currentFitness: Double = 0
  var candidateFitness: Option[Double] = None
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
      Future.sequence(neighbors.map(f => (f ? WhatAreYourInterests).mapTo[Set[BitSet]])).onSuccess {
        case x =>
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
          this.neighbors = this.neighbors.sortBy { n=>
            -neighborSimilarity(n)
          }
          //println(s"Best = ${neighborSimilarity(this.neighbors(0))}, worst = ${neighborSimilarity(this.neighbors(this.neighbors.length-1))} ")

          returnTo ! "OK"

      }


    case Learn(round: Int) =>
      val returnTo = sender()

      val bf = (brain ? Probe(interests)).mapTo[Map[BitSet, ListBuffer[BitSet]]]
      val uf = (universe ? Probe(interests)).mapTo[Map[BitSet, ListBuffer[BitSet]]]
      val neighborsToExamine = selectNeighbors()
      val nf = Future.sequence(for (n <- neighborsToExamine) yield (n ? Probe(interests)).mapTo[Map[BitSet, ListBuffer[BitSet]]])
      // val nf = (neighbor ? Probe(interests)).mapTo[Map[BitSet, ListBuffer[mutable.BitSet]]]

      improvement = "None"
      for {
        brainPrediction <- bf
        neighborPrediction <- nf
        actual <- uf
      } yield {
        //my current fitness on the new probe
        currentFitness = compareSeveral(actual, brainPrediction)


        //Pick the best neighbor that beats my current fitness
        var best = -1
        neighborPrediction.indices.foldLeft(0.0) { (last: Double, current: Int) =>
          val fitness = compareSeveral(actual, neighborPrediction(current))
          if (fitness > last && fitness > currentFitness) {
            best = current
            fitness
          } else {
            last
          }
        }

        //The following gives me a new candidate brain
        if (best > -1) {
          improvement = "Neighbor"
          attemptTolearnFromNeighbor(neighborsToExamine(best))
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
              val candidate = compareSeveral(y, actual)
              if (candidate > currentFitness) {
                nextBrain = Some(x)
                candidateFitness = Some(candidate)
                context.parent ! AgentUpdate(-1,round, name, interests, candidate.toFloat, (candidate - currentFitness).toFloat, Some(improvement))
              } else {
                context.stop(x)
                candidateFitness = None
                nextBrain = None
                context.parent ! AgentUpdate(-1,round, name, interests, currentFitness.toFloat, 0f, None)

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
          currentFitness = candidateFitness.getOrElse(0.0)
          brain = x

        // println(s"$name IMPROVES: $improvement")

        case None =>
        //println(s"$name NO IMPROVEMENTS")

      }
      sender ! "OK"


    case Probe(states: Set[BitSet]) =>
      val s = sender()
      brain ? Probe(states) onComplete  {
        case Success(result) =>
          s ! result

        case Failure(result)=>
          println(s"Failure probing my own brain? $result")
      }

    case WhatAreYourInterests =>
      sender() ! interests

    case GiveMeYourBrain =>
      brain forward GiveMeYourBrain

  }

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

object SimpleAgent {

  def copyBrain(name: String, brainSize: Int, brainDegree: Int, nodes: Array[Node]): Props = Props(new BasicUniverse(name, brainSize, nodes))

  def createBrain(name: String, brainSize: Int, brainDegree: Int): Props = Props(new BasicUniverse(name, brainSize, brainDegree, brainSize))


}


