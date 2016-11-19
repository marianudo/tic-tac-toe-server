
package me.marianonavas.tictactoe

import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.mashape.unirest.http.Unirest
import io.dropwizard.testing.junit.DropwizardAppRule
import org.junit.{ClassRule, Test}
import org.scalatest.Matchers
import org.scalatest.junit.JUnitSuite


object NoughtsTest {
	@ClassRule def rule = new DropwizardAppRule[NoughtsConfiguration](classOf[NoughtsApplication], "test.yml")
}

class NoughtsTest extends JUnitSuite with Matchers {

  val baseUrl = "http://localhost:8080/game"

  val objectMapper = new ObjectMapper()
  objectMapper.registerModule(DefaultScalaModule)

  def initGame(player1Id: String, player2Id: String): String = {
    val response = Unirest.post(baseUrl)
      .queryString("player1Id", player1Id)
      .queryString("player2Id", player2Id)
      .asString()

    if(response.getStatus != Status.OK.getStatusCode) {
      throw new RuntimeException(s"${response.getStatus} when creating game: ${response.getBody}")
    }

    objectMapper.readValue(response.getBody, classOf[GameCreatedResponse]).gameId
  }

  def runMoveAndGetStatusCode(gameId: String, move: MoveRequest): (Int, String) = {
    val response = Unirest.put(s"$baseUrl/$gameId")
        .header("Content-Type", "application/json")
        .body(objectMapper.writeValueAsString(move))
        .asString()

    (response.getStatus, response.getBody)
  }

  def runMoves(gameId: String, moves: Seq[MoveRequest]) = {
    moves.foreach(move => {
      val (responseStatus, body) = runMoveAndGetStatusCode(gameId, move)

      if(responseStatus != Status.ACCEPTED.getStatusCode) {
        throw new RuntimeException(s"$responseStatus when making move: $body")
      }
    })
  }

  def getState(gameId: String): GameStateResponse = {
    val response = Unirest.get(s"$baseUrl/$gameId").asString()

    if(response.getStatus != Status.OK.getStatusCode) {
      throw new RuntimeException(s"${response.getStatus} when getting state: ${response.getBody}")
    }

    objectMapper.readValue(response.getBody, classOf[GameStateResponse])
  }

  def parseConflictResponse(json: String): String =
    objectMapper.readValue(json, classOf[ConflictResponse]).cause

  def assertConflictResponse(tuple: (Int, String), expectedErrorMsg: String) = {
    assertConflictStatus(tuple._1)
    assert(parseConflictResponse(tuple._2) == expectedErrorMsg)
  }

	@Test
	def testPlayer1Win() {
      val gameId = initGame("1", "2")
      runMoves(gameId, Seq(
        MoveRequest("1", 0, 0),
        MoveRequest("2", 1, 0),
        MoveRequest("1", 0, 1),
        MoveRequest("2", 1, 1),
        MoveRequest("1", 0, 2)
      ))

      assert(getState(gameId) == GameStateResponse("1", true))
	}

  @Test
  def testPlayer2Win() = {
    val gameId = initGame("me", "you")

    runMoves(gameId, Seq(
      MoveRequest("you", 0, 0),
      MoveRequest("me", 1, 0),
      MoveRequest("you", 0, 1),
      MoveRequest("me", 1, 1),
      MoveRequest("you", 0, 2)
    ))

    assert(getState(gameId) == GameStateResponse("you", true))
  }

  @Test
  def testDrawGame() = {
    val gameId = initGame("John", "Doe")

    runMoves(gameId, Seq(
      MoveRequest("John", 0, 0),
      MoveRequest("Doe", 1, 0),
      MoveRequest("John", 2, 0),
      MoveRequest("Doe", 1, 1),
      MoveRequest("John", 1, 2),
      MoveRequest("Doe", 0, 2),
      MoveRequest("John", 2, 1),
      MoveRequest("Doe", 2, 2),
      MoveRequest("John", 0, 1)
    ))

    assert(getState(gameId) == GameStateResponse(null, true))
  }

  def assertConflictStatus(status: Int) =
    assert(status == Response.Status.CONFLICT.getStatusCode)

  @Test
  def testMoveOutOfTurn() = {
    val gameId = initGame("He", "She")

    runMoveAndGetStatusCode(gameId, MoveRequest("He", 0, 0))
    runMoveAndGetStatusCode(gameId, MoveRequest("She", 0, 1))
    runMoveAndGetStatusCode(gameId, MoveRequest("He", 1, 1))

    val responseTuple = runMoveAndGetStatusCode(gameId, MoveRequest("He", 1, 2))

    assertConflictResponse(responseTuple, "This wasn't this player turn")
  }

  @Test
  def testMoveToOccupiedPosition() = {
    val gameId = initGame("uncle", "aunt")

    runMoveAndGetStatusCode(gameId, MoveRequest("uncle", 0, 0))
    runMoveAndGetStatusCode(gameId, MoveRequest("aunt", 0, 1))
    val responseTuple = runMoveAndGetStatusCode(gameId, MoveRequest("uncle", 0, 1))

    assertConflictResponse(responseTuple, "Position (0, 1) already taken")
  }

  @Test
  def testIncludeAThirdPlayerInTheMiddleOfAGame() = {
    val gameId = initGame("mum", "dad")

    runMoveAndGetStatusCode(gameId, MoveRequest("mum", 0, 0))
    runMoveAndGetStatusCode(gameId, MoveRequest("dad", 0, 1))
    runMoveAndGetStatusCode(gameId, MoveRequest("mum", 1, 1))
    val responseTuple = runMoveAndGetStatusCode(gameId, MoveRequest("grandma", 1, 2))

    assertConflictResponse(responseTuple, "Player grandma is not part of this game")
  }

  @Test
  def testTryAMoveOutOfTheGameGrid() = {
    val gameId = initGame("nephew", "niece")

    runMoveAndGetStatusCode(gameId, MoveRequest("nephew", 0, 0))
    val resposeTuple = runMoveAndGetStatusCode(gameId, MoveRequest("niece", 0, 3))

    assertConflictResponse(resposeTuple, "Coordinate 3 out of range (0, 1, 2)")
  }

  @Test
  def testTryAMoveOnAFinishedGame() = {
    val gameId = initGame("grandma", "grandpa")

    runMoves(gameId, Seq(
      MoveRequest("grandma", 0, 0),
      MoveRequest("grandpa", 1, 0),
      MoveRequest("grandma", 0, 1),
      MoveRequest("grandpa", 1, 1),
      MoveRequest("grandma", 0, 2)
    ))

    val responseTuple = runMoveAndGetStatusCode(gameId, MoveRequest("grandpa", 2, 2))

    assertConflictResponse(responseTuple, s"Game with id $gameId not created yet or already finished")
  }

  @Test
  def testGetStateOfAGameInProgress() = {
    val gameId = initGame("socrates", "aristoteles")

    runMoves(gameId, Seq(
      MoveRequest("socrates", 0, 0),
      MoveRequest("aristoteles", 1, 0),
      MoveRequest("socrates", 0, 1)
    ))

    assert(getState(gameId) == GameStateResponse(null, false))
  }

  @Test
  def testMoveOnANotCreatedGame() = {
    val responseTuple = runMoveAndGetStatusCode("notCreatedGame", MoveRequest("player", 0, 0))

    assertConflictResponse(responseTuple, "Game with id notCreatedGame not created yet or already finished")
  }
}