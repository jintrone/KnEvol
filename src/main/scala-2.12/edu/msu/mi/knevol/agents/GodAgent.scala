package edu.msu.mi.knevol.agents

import akka.actor._
import akka.util.Timeout
import edu.msu.mi.knevol.universe.{Wire, BasicUniverse, StateVar, Node}


import scala.concurrent.Future
import scala.util.Random
import akka.pattern.ask
import concurrent.duration._

/**
  * Created by josh on 10/21/15.
  */
class GodAgent(nodes: Array[Node],
               observableState: Int = 10,
               population: Int = 100,
               brainSize: Int = 15,
               contextSize: Int = 10,
               numInterests: Int = 1,
               diversityExponent:Double = .1,
               neighborsToCheck: Int = 10,
               notes: String = "",
               scribe: ActorRef) extends Actor with Stash {

  import context.dispatcher

  implicit val timeout = Timeout(1 minute)

  var universeDegree = nodes.map(_.neighbors.size).sum / nodes.size
  var universeSize = nodes.size

  var universe: ActorRef = context.actorOf(GodAgent.createUniverse("ActualUniverse", nodes, observableState))
  var agents: Map[String, ActorRef] = (0 until population).toArray.map(x => s"Agent$x" -> context.actorOf(GodAgent.createAgent(s"Agent$x", universe,
    universeDegree, brainSize, observableState, contextSize, numInterests, diversityExponent,neighborsToCheck)))(collection.breakOut): Map[String, ActorRef]
  var stop = 0
  var simId = -1


  override def receive: Receive = {
    case Init =>
      val returnTo = sender()
      (scribe ? InitRun(universeSize, universeDegree, observableState, population, brainSize, contextSize, numInterests, diversityExponent, neighborsToCheck, notes)).mapTo[Int] flatMap {
        x =>
          println(s"Set simulation id $x")
          simId = x
          val wire = Future.sequence(for (target: ActorRef <- agents.values.toIndexedSeq) yield {
            val sources = (agents.values.toSet - target).toArray
            target ? Wire(sources)
          })

          wire onSuccess {

            case x =>
              returnTo ! "OK"
              println("Ready!")


          }
          wire
      }

    case Go(rounds: Int) =>
      stop = rounds
      kickoff(0, sender())


    case x: AgentUpdate =>
      scribe forward AgentUpdate(simId, x.round, x.name, x.interests, x.fitness, x.fitnessChange, x.updateBy)


    case _ =>
      println("Nothing doin'")
  }


  def kickoff(round: Int, ref: ActorRef): Unit = {
    println(s"Round $round")
    if (round == stop) {
      println("Done!")
      //scribe ! Shutdown
      ref ! "Done"
    } else {
      for {
        x <- Future.sequence(for (x <- agents.values.toIndexedSeq) yield {

          x ? Learn(round)
        })
        y <- Future.sequence(for (x <- agents.values.toIndexedSeq) yield {

          x ? Update
        })
      } yield {
        kickoff(round + 1, ref)
      }
    }

  }

}

object GodAgent {


  def apply(universeSize: Int = 20,
            universeDegree: Int = 3,
            observableState: Int = 10,
            population: Int = 100,
            brainSize: Int = 15,
            contextSize: Int = 10,
            numInterests: Int = 1,
            diversityExponent: Double = 1.0d,
            neighborsToCheck: Int = 10,
            notes: String = "",
            scribe: ActorRef) = {

    val nodes = (for (i <- 0 until universeSize) yield new Node(i, universeSize, universeDegree)).toArray
    new GodAgent(nodes,
      observableState,
      population,
      brainSize,
      contextSize,
      numInterests,
      diversityExponent,
      neighborsToCheck,
      notes,
      scribe: ActorRef)
  }


  def createUniverse(name: String, nodes: Array[Node], observable: Int): Props = Props(new BasicUniverse(name, observable, nodes))

  def createAgent(name: String,
                  universe: ActorRef,
                  brainDegree: Int,
                  brainSize: Int,
                  observableSize: Int,
                  contextSize: Int,
                  numInterests: Int,
                  diversityExponent: Double,
                  neighborsToCheck:Int): Props = Props(new SimpleAgent(name, universe, brainSize, brainDegree, observableSize, contextSize, numInterests, .5, .5, .7, diversityExponent, neighborsToCheck))


}
