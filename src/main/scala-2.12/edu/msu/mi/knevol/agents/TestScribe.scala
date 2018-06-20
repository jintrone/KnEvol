package edu.msu.mi.knevol.agents

import akka.actor.{Props, ActorSystem}

/**
 * Created by josh on 10/29/15.
 */
object TestScribe extends App {

  val system = ActorSystem("TestDbCreation")
  val scribe = system.actorOf(Props(new ScribeAgent()))

  Thread.sleep(5000)



}
