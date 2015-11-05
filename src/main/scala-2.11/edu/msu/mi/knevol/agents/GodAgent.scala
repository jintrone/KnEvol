package edu.msu.mi.knevol.agents

import akka.actor.{Stash, Actor, ActorRef, Props}
import akka.util.Timeout
import edu.msu.mi.knevol.universe.{Wire, BasicUniverse, StateVar, Node}


import scala.concurrent.Future
import scala.util.Random
import akka.pattern.ask
import concurrent.duration._

/**
 * Created by josh on 10/21/15.
 */
class GodAgent(
                universeSize: Int = 20,
                universeDegree: Int = 3,
                observableState: Int = 10,
                population: Int = 100,
                brainSize: Int = 15,
                contextSize: Int = 10,
                numInterests: Int =1,
                socialDegree: Int = 10,
                notes:String = "",
                scribe:ActorRef) extends Actor with Stash {

  import context.dispatcher

  implicit val timeout = Timeout(1 minute)


  var universe: ActorRef = context.actorOf(GodAgent.createUniverse("ActualUniverse",universeSize, universeDegree, observableState))
  var agents: Array[ActorRef] = (0 until population).toArray.map(x=>context.actorOf(GodAgent.createAgent(s"Agent$x",universe, universeDegree, brainSize,  observableState, contextSize,numInterests)))
  var stop = 0
  var simId = -1



  override def receive: Receive = {
    case Init =>
      val returnTo = sender()
      (scribe ? InitRun(universeSize,universeDegree,observableState,population,brainSize,contextSize,numInterests,socialDegree,notes)).mapTo[Int] flatMap {
        x =>
          println(s"Set simulation id $x")
          simId = x
          val wire = Future.sequence(for (target: ActorRef <- agents.toIndexedSeq) yield {
            val sources = agents.toSet - target
            target ? Wire(Random.shuffle(sources).toList take socialDegree)
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
      kickoff(0,sender())


    case x:AgentUpdate =>
      scribe forward AgentUpdate(simId,x.round,x.name,x.interests,x.fitness,x.fitnessChange,x.updateBy)


    case _ =>
      println("Nothing doin'")
  }



  def kickoff(round: Int,ref:ActorRef): Unit = {
    println(s"Round $round")
    if (round == stop) {
      println("Done!")
      //scribe ! Shutdown
      ref ! "Done"
    } else {
      for {
        x <- Future.sequence(for (x <- agents.toIndexedSeq) yield {

          x ? Learn(round)
        })
        y <- Future.sequence(for (x <- agents.toIndexedSeq) yield {

          x ? Update
        })
      } yield {
        kickoff(round + 1,ref)
      }
    }

  }

}

object GodAgent {

  def createUniverse(name:String, universeSize: Int, degree: Int, observable: Int): Props = Props(new BasicUniverse(name,universeSize, degree, observable))

  def createAgent(name:String,
                  universe: ActorRef,
                  brainDegree: Int,
                  brainSize: Int,
                  observableSize: Int,
                  contextSize: Int,
                   numInterests:Int): Props = Props(new SimpleAgent(name, universe, brainSize, brainDegree, observableSize, contextSize,numInterests))


}
