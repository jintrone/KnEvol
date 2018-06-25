package edu.msu.mi.knevol.universe


import java.util.NoSuchElementException

import akka.actor.{ActorRef, Props, Actor}
import akka.pattern.ask
import akka.util.Timeout

import edu.msu.mi.knevol.agents.GiveMeYourBrain
import edu.msu.mi.knevol.universe._


import scala.collection.immutable.Iterable
import scala.collection.mutable.ListBuffer
import scala.collection.{BitSet, mutable}
import scala.concurrent.Future
import concurrent.duration._
import scala.util.{Failure, Success}
import scalax.collection.GraphEdge.DiEdge
import scala.collection.breakOut

import scalax.collection.mutable.Graph
import scalax.collection.GraphPredef._
import scalax.collection.GraphEdge._

/**
  * Created by josh on 8/23/15.
  */
class BasicUniverse(name: String, observable: Int, val nodes: Array[Node]) extends Actor {

  import context.dispatcher

  implicit val timeout = Timeout(20 seconds)


  var processing = mutable.Map[BitSet, ProcessHolder]()
  var baseState: BitSet = createRandomBitset(nodes.length) -- (0 until observable)

  var attractors: Graph[BitSet, DiEdge] = scalax.collection.mutable.Graph[BitSet, DiEdge]()


  def this(name: String, size: Int = 20, degree: Int = 3, observable: Int = 20) {
    this(name, observable, (for (i <- 0 until size) yield new Node(i, size, degree)).toArray)

  }


  def safeUpdateGraph(g: Graph[BitSet, DiEdge]): Unit = {
    //somehow, this does not seem to happen immediately.  This is very odd
    attractors.synchronized {
      attractors ++= g
    }

  }

  def safeCopyGraph(): Graph[BitSet, DiEdge] = {

    var result: Graph[BitSet, DiEdge] = Graph.empty
    attractors.synchronized {
      result = Graph[BitSet, DiEdge]() ++ attractors
    }

    result

  }

  //def launchProbe(b:BitSet):

  def probe(p: ProcessHolder): Future[ListBuffer[BitSet]] = {
    Future.sequence(p.processing.map(_ ? Update).toList) flatMap {
      x =>
        val next: BitSet = (for ((b: Boolean, count) <- x.zipWithIndex if b) yield count) (collection.breakOut)
        if (p.graph.contains(next)) {
          p.graph += p.last ~> next

          safeUpdateGraph(p.graph)
          processing -= p.probe
          p.processing.foreach(context.stop)
          if (!attractors.contains(p.probe)) {
            println(s"Attractors missing ${p.probe}")
          }
          Future(composeStateSequence(p.probe, p.graph))
          //add next to graph
        } else {
          p.graph += p.last ~> next
          p.last = next
          Future.sequence(p.processing.map(_ ? Advance).toList) flatMap { x =>
            probe(p)
          }
        }
    }
  }


  override def receive: Receive = {
    case Probe(probe: Set[BitSet]) =>
      //println(s"Recevied a probe ${probe} in brain ${name}")
      val returnTo = sender()
      val probeAsList = probe.toList

      Future.sequence(probeAsList.map { s: BitSet =>
        val actualProbe = s ++ baseState
        if (attractors.contains(actualProbe)) {
          Future(
            try {
              composeStateSequence(actualProbe, attractors)
            } catch {

              case x: NoSuchElementException =>
                println(s"${attractors.nodes}")
                throw x

              case y: Throwable =>
                println("no clue")
                throw y

            }
          )
        } else if (processing.contains(s)) {
          processing(actualProbe).calculation.get
        } else {
          val p = ProcessHolder(actualProbe, this, safeCopyGraph(), nodes.map { n: Node => context.actorOf(BasicUniverse.createActor(n, nodes.length)) }.to[ListBuffer])
          processing(actualProbe) = p
          val f: Future[ListBuffer[BitSet]] = Future.sequence(for ((a: ActorRef, count: Int) <- p.processing.zipWithIndex) yield a ? Wire(nodes(count).neighbors.map(p.processing(_)))) flatMap (x =>
            Future.sequence(for ((actor, count) <- p.processing.zipWithIndex) yield actor ? SetState(p.probe(count))) flatMap (x =>
              this.probe(p)
              ))
          p.calculation = Some(f)
          p.calculation.get
        }
      }) onComplete {
        case Success(results) =>
          //println(s"Successfully completed probe ${probe} in brain ${name}")
          returnTo ! ((probeAsList zip results) (breakOut): Map[BitSet, ListBuffer[BitSet]])

        case Failure(x) =>
          println(s"Something bad happened $x")
          x.printStackTrace()
      }

    case GiveMeYourBrain =>
      sender ! nodes.map {
        new Node(_, nodes.length)
      }

    case GiveMeYourWorld =>
      sender ! attractors

    case _ => println("Got something else")
  }


  def composeStateSequence(probe: BitSet, graph: Graph[BitSet, DiEdge] = attractors, onlyObservable: Boolean = true): ListBuffer[BitSet] = {
    var b = ListBuffer[BitSet]()

    if (!(graph contains probe)) {
      println(s"PROBLEM: $probe not found; base state is $baseState")
    }
    (graph get probe).pathUntil { n =>
      if (b.contains(n)) true
      else {
        b += n
        false
      }
    }

    if (onlyObservable) {
      b.map { x =>
        x filter (_ < observable)
      }
    } else {
      b
    }

  }


  def dump(): String = {
     ""
  }

  //println(s"BaseState $baseState")


}


object BasicUniverse {

  def createActor(node: Node, universeSize: Int): Props = Props(new StateVar(new Node(node, universeSize)))


}

case class ProcessHolder(probe: BitSet,
                         universe: BasicUniverse,
                         graph: Graph[BitSet, DiEdge] = Graph[BitSet, DiEdge](),
                         processing: mutable.ListBuffer[ActorRef] = mutable.ListBuffer[ActorRef](),
                         receivers: mutable.ListBuffer[ActorRef] = mutable.ListBuffer[ActorRef]()) {


  var calculation: Option[Future[ListBuffer[BitSet]]] = None
  var last: BitSet = probe
  graph += probe

}
