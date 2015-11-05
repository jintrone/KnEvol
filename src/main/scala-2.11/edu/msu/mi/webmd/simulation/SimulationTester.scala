package edu.msu.mi.webmd.simulation

import akka.actor.{Props, ActorSystem}

/**
 * Created by josh on 8/14/15.
 */
object SimulationTester extends App {

  val system = ActorSystem("TestSimulation")
  val forum = system.actorOf(Props(new Forum))

  forum ! Init(100)




}
