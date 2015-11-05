package edu.msu.mi.knevol.universe

import java.io.FileWriter

import akka.actor.{Props, ActorSystem}
import akka.util.Timeout

import concurrent.duration._
import akka.pattern._

import scala.collection.{BitSet, mutable}
import scala.concurrent.{Future, Await}
import scala.language.postfixOps
import scala.util.Random
import scalax.collection.GraphEdge.DiEdge
import scalax.collection.Graph
import scalax.collection.io.dot._
import implicits._


/**
 * Created by josh on 10/15/15.
 */
object UniverseTest extends App {

  val system = ActorSystem("TestUniverse")
  val nodes = 10
  val connections = 9
  Random.setSeed(1000l)


  implicit val timeout = Timeout(30 seconds)

  import scala.concurrent.ExecutionContext.Implicits.global


  //val b1 = createRandomBitset(10)
  //val b2 = createRandomBitset(10)


  //println(Await.result(universe ? Probe(Set(b1,b2)), 5 seconds))
  //println(Await.result(universe ? Probe(b), 5 seconds))

  val root = DotRootGraph(directed = true, id = Some(s"$nodes Nodes, $connections Degree"))



  def edgeTransformer(innerEdge: Graph[BitSet, DiEdge]#EdgeT): Option[(DotGraph, DotEdgeStmt)] = {
    val edge = innerEdge.edge
    Some((root, DotEdgeStmt(NodeId(convert(edge.from.value.asInstanceOf[BitSet])), NodeId(convert(edge.to.value.asInstanceOf[BitSet])), Nil)))

  }

  for (degree<-1 to 10) {
    val universe = system.actorOf(Props(new BasicUniverse("TestUniverse",nodes, degree, 10)))
    Await.result(Future.sequence(for (i: Int <- 0 until Math.pow(2, 10).toInt) yield universe ? Probe(Set(convert(i)))), 20 seconds)
    val g = Await.result((universe ? GiveMeYourWorld).mapTo[Graph[BitSet, DiEdge]], 20 seconds)
    println(s"Found ${g.nodes count (_.outDegree > 1)} nodes with outdegree > 1")
    writeToFile(s"StateGraphK$degree.dot", s"${g.toDot(root, edgeTransformer)}")
    system.stop(universe)
  }


  def using[A <: {def close(): Unit}, B](param: A)(f: A => B): B =
    try { f(param) } finally { param.close() }

  def printToFile(f: java.io.File)(op: java.io.PrintWriter => Unit) {
    val p = new java.io.PrintWriter(f)
    try { op(p) } finally { p.close() }
  }

  def writeToFile(fileName:String, data:String) =
    using (new FileWriter(fileName)) {
      fileWriter => fileWriter.write(data)
    }


}
