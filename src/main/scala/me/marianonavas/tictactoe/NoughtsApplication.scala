package me.marianonavas.tictactoe

import akka.actor.ActorSystem
import akka.util.Timeout
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import io.dropwizard.Application
import io.dropwizard.jersey.jackson.JacksonMessageBodyProvider
import io.dropwizard.setup.{Bootstrap, Environment}
import me.marianonavas.tictactoe.logic.GameLogic
import me.marianonavas.tictactoe.logic.repository.GameRepository

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object NoughtsApplication {
  def main(args: Array[String]) {
    new NoughtsApplication().run(args:_*)
  }
}

class NoughtsApplication extends Application[NoughtsConfiguration] {

  private implicit val actorSystem = ActorSystem("NoughtsApplication")
  private implicit val executionContext: ExecutionContext = actorSystem.dispatcher
  private implicit val akkaTimeout = Timeout(1.second)

  override val getName = "noughts"

  override def initialize(bootstrap: Bootstrap[NoughtsConfiguration]) {

  }

  override def run(configuration: NoughtsConfiguration, environment: Environment) {
    val dbHost = System.getenv("DB_HOST")

    val repository = Await.result(
      GameRepository(s"mongodb://$dbHost/"), 2.seconds
    )

    val resource = new NoughtsResource(
    GameLogic(repository)
    )

    val objectMapper = new ObjectMapper() with ScalaObjectMapper
    objectMapper.registerModule(DefaultScalaModule)

    environment.jersey().register(new JacksonMessageBodyProvider(objectMapper, environment.getValidator))
    environment.jersey().register(resource)
  }

}
