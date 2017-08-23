package edu.msu.mi.knevol.agents


import akka.actor.{Props, ActorSystem}
import akka.util.Timeout
import edu.msu.mi.knevol.universe._
import scala.concurrent
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import akka.pattern.ask
import scala.util.{Failure, Success, Random}

/**
 * Created by josh on 10/29/15.
 */
object AgentLauncher extends App {


  implicit val timeout = Timeout(2 hours)

  import scala.concurrent.ExecutionContext.Implicits.global


  val system = ActorSystem("TestUniverse")
  val scribe = system.actorOf(Props(new ScribeAgent))

  val universeSize = 20
  val universeDegree = 8

  val nodes =  (for (i <- 0 until universeSize) yield new Node(i, universeSize, universeDegree)).toArray

  var params = List(100,200,300,400,500).sortBy{x=>x}.map {size=>
    Params(20, 3, 10, 100, 15, 10, 3, (.8*size).toInt, s"high deg fixed network 4, size=$size",.5,5)
  }

    //checking k
    //Params(10, 1, 10, 10, 10, 1, 10, "Random neighbor", 10),
    //Params(10, 2, 10, 10, 10, 1, 10, "Random neighbor", 10),
    //Params(10, 3, 10, 10, 10, 1, 10, "Random neighbor", 9),
    //Params(10, 4, 10, 10, 10, 1, 10, "Random neighbor",10),
    //Params(10, 5, 10, 10, 10, 1, 10, "Random neighbor", 10),
    //Params(10, 6, 10, 10, 10, 1, 10, "Random neighbor", 10),
    //Params(10, 7, 10, 10, 10, 1, 10, "Random neighbor", 10),
    //Params(10, 8, 10, 10, 10, 1, 10, "Random neighbor", 10),
    //Params(10, 9, 10, 10, 10, 1, 10, "Random neighbor", 10),
    //Params(10, 10, 10, 10, 10, 1, 10, "Random neighbor", 10),

    //increased size, completely capable agents0
    //Params(20, 3, 20, 20, 20, 1, 10, "Random neighbor", 10),
    //Params(20, 3, 20, 20, 20, 2, 10, "Random neighbor", 1),
    //Params(20, 3, 20, 20, 20, 3, 10, "Random neighbor", 10),

    //hidden state, increasingly capable agents
    //Params(20, 3, 10, 10, 10, 3, 10, "Random neighbor", 10),
    //Params(20, 3, 10, 12, 10, 3, 10, "Random neighbor", 2),




  var fSerial = Future[Any] {
    ()
  }
  for {n <- params} for {count <- 0 until n.count} fSerial = fSerial.flatMap(accumRes => runSystem(n))

  Await.result(fSerial, 3 hours)



  def runSystem(p: Params): Future[Any] = {

    println("Letting the system spin up...")
    val god = system.actorOf(Props(
      new GodAgent(
        nodes,
        observableState = p.observableState,
      population = p.population,
        brainSize = p.brainSize,
        contextSize = p.contextSize,
        numInterests = p.numInterests,
        socialDegree = p.socialDegree,
        similarityWeight=p.similarity.toFloat,
        notes = p.notes,
        scribe=scribe)))

    Thread.sleep(5000)

    val result =(god ? Init) flatMap {
       x=>
         god ? Go(200)
    }

    result.onComplete {
      case Success(x) =>
        system.stop(god)

      case Failure(x) =>
        println(s"Something screwed up: $x")
        system.stop(god)
    }
    result
  }


}



case class Params(universeSize: Int = 20,
                  universeDegree: Int = 3,
                  observableState: Int = 10,
                 population: Int = 100,
                  brainSize: Int = 15,
                  contextSize: Int = 10,
                  numInterests: Int = 1,
                  socialDegree: Int = 10,
                  notes: String = "",
                 similarity:Double=.1,
                  count: Int = 10)
