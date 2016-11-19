package me.marianonavas.tictactoe

import javax.ws.rs.core.{MediaType, Response}
import javax.ws.rs._

import me.marianonavas.tictactoe.logic._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
  *
  */
// Requests; Have to be here to be properly deserialized by Jackson
// (doesn't work if defined as an inner case class)
private[tictactoe] case class MoveRequest(playerId: String, x: Int, y: Int)

@Path("/game")
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
private[tictactoe] class NoughtsResource(gameLogic: GameLogic) {

    private def conflictResponse(msg: String): Response =
        Response.status(Response.Status.CONFLICT)
            .entity(ConflictResponse(msg))
            .build()

    private def okResponse(entity: AnyRef): Response =
        Response.ok(entity).build()

    @POST
    def createGame(@QueryParam("player1Id") player1: String, @QueryParam("player2Id") player2: String): Response = {
        val players = Players(player1, player2)
        val eventualGameCreationResult: Future[GameCreationResult] = gameLogic.createGame(players)

        // As Dropwizard is a synchronous tool we have to wait for this Future to return an actual response to the client
        // a good improvement here would be to use Spray, Akka Http, Play, or any other fully asynchronous server library
        val gameCreationResult: GameCreationResult = Await.result(eventualGameCreationResult, 2.seconds)

        gameCreationResult match {
            case GameCreated(gameId) => okResponse(GameCreatedResponse(gameId))
            case PlayersAlreadyInAGame(Players(p1, p2)) =>
                conflictResponse("The given players are already in a game in progress")
        }
    }

    @GET
    @Path("/top10")
    def top10Scorers(): Response = {
        val scorers = Await.result(gameLogic.top10Scorers(), 2.seconds)
        okResponse(scorers)
    }

    @GET
    @Path("/{gameId}")
    def getGame(@PathParam("gameId") gameId: String): Response = {
        val eventualMaybeGameState = gameLogic.state(gameId)

        // As always, we need to wait (see the counterpart comment in the rest of the methods of this resource class)
        val maybeGameState = Await.result(eventualMaybeGameState, 2.seconds)

        maybeGameState match {
            case None => conflictResponse(s"The game $gameId has never existed")
            case Some(gameState) => gameState match {
                case GameInProgress =>
                    okResponse(GameStateResponse(null, false))

                case DrawGame =>
                    okResponse(GameStateResponse(null, true))

                case GameOver(winner) =>
                    okResponse(GameStateResponse(winner, true))

                case x =>
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ConflictResponse(s"Unexpected status for a persisted game: $x"))
                        .build()
            }
        }
    }


    @PUT
    @Path("/{gameId}")
    def makeMove(@PathParam("gameId") gameId: String, moveRequest: MoveRequest): Response = {

        case class InvalidCoordinate(index: Int)

        def indexToGridCoordinate(index: Int): Either[InvalidCoordinate, GridCoordinate] =
            if (index == 0) Right(First)
            else if (index == 1) Right(Center)
            else if (index == 2) Right(Last)
            else Left(InvalidCoordinate(index))

        def gridCoordinateToIndex(coordinate: GridCoordinate): Int =
            coordinate match {
                case First => 0
                case Center => 1
                case Last => 2
            }

        val xGridCoordinate = indexToGridCoordinate(moveRequest.x)
        val yGridCoordinate = indexToGridCoordinate(moveRequest.y)

        val gridPosition = for {
            x <- xGridCoordinate.right
            y <- yGridCoordinate.right
        } yield GridPosition(x, y)

        gridPosition match {
            case Right(position) => {
                val eventualMoveResult: Future[MoveResult] = gameLogic.move(
                    gameId,
                    moveRequest.playerId,
                    position
                )

                // As commented before in the createGame method, as Dropwizard is by default synchronous we need to wait
                // to return a response
                // This is something that could been improved in a real project, using any asynchronous framework / library
                // (See the corresponding comment on the createGame method for further info)
                val moveResult = Await.result(eventualMoveResult, 1.second)

                moveResult match {
                    case SuccessfulMoveResult => Response.status(Response.Status.ACCEPTED).build()

                    case MoveForGameNotInProgress =>
                        conflictResponse(s"Game with id $gameId not created yet or already finished")

                    case InvalidMoveResult(reason) => reason match {
                        case PositionAlreadyTaken(GridPosition(x, y)) =>
                            conflictResponse(
                                s"Position (${gridCoordinateToIndex(x)}, ${gridCoordinateToIndex(y)}) already taken"
                            )

                        case GameAlreadyFinished =>
                            conflictResponse(s"Game with id $gameId is already finished; no further moves are allowed")

                        case MoveFromAThirdPlayer(player) =>
                            conflictResponse(s"Player $player is not part of this game")

                        case MoveOutOfTurn =>
                            conflictResponse("This wasn't this player turn")
                    }

                    case MoveForPlayerNotInGame(player) =>
                        conflictResponse(s"Player $player is not part of this game")
                }
            }

            case Left(invalidCoordinate) =>
                conflictResponse(s"Coordinate ${invalidCoordinate.index} out of range (0, 1, 2)")
        }
    }
}
