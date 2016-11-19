package me.marianonavas.tictactoe.logic

import java.util.UUID

import akka.actor.ActorSystem
import akka.util.Timeout
import me.marianonavas.tictactoe.logic.Game.{GameId, Player}
import me.marianonavas.tictactoe.logic.repository.GameRepository
import org.mockito.Matchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.FunSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar

import scala.concurrent.Future

/**
  *
  */
class GameLogicSpec extends FunSpec with MockitoSugar {

    import scala.concurrent.duration._

    val actorSystem = ActorSystem("GameLogicSpec")
    val timeout = Timeout(2.seconds)

    val gameLogic = GameLogic(mock[GameRepository])(actorSystem, timeout)

    implicit val executionContext = actorSystem.dispatcher

    def createGame(players: Players, logic: GameLogic = gameLogic) =
        logic.createGame(players)

    describe("When creating a new game for two players") {
        it("If they are not playing yet the game is created") {
            val players = Players("p11", "p12")
            val eventualGameCreationResult = createGame(players)
            ScalaFutures.whenReady(eventualGameCreationResult) {
                result => assert(result.isInstanceOf[GameCreated])
            }
        }

        it("If they are already playing the game is not created") {
            val players = Players("p21", "p22")
            val eventualGameCreationResult: Future[GameCreationResult] = for {
                _ <- createGame(players)
                game2 <- createGame(players)
            } yield game2

            ScalaFutures.whenReady(eventualGameCreationResult) {
                result => assert(result == PlayersAlreadyInAGame(players))
            }
        }
    }

    private def createGameAndRetrieveId(players: Players, logic: GameLogic = gameLogic): Future[GameId] =
        createGame(players, logic) map {
            case GameCreated(id) => id
            case _ => fail("The game should have been successfully created")
        }

    private def makeMoveOnBrandNewGame(players: Players, playerToMove: Player, whereToMove: GridPosition): Future[MoveResult] =
        for {
            id <- createGameAndRetrieveId(players)
            move <- gameLogic.move(id, playerToMove, whereToMove)
        } yield move

    describe("When making a first move") {
        it("in an existing game for an existing user, the result should be successful") {
            val player1 = "p31"
            val players = Players(player1, "p32")
            val eventualMoveResult = makeMoveOnBrandNewGame(players, player1, GridPosition(First, First))

            ScalaFutures.whenReady(eventualMoveResult) {
                result => assert(result == SuccessfulMoveResult)
            }
        }

        it("In a not existing game, the result should be the corresponding error") {
            val player1 = "p41"
            val gameId = UUID.randomUUID.toString

            val eventualMoveResult =
                gameLogic.move(gameId, player1, GridPosition(First, First))

            ScalaFutures.whenReady(eventualMoveResult) {
                result => assert(result == MoveForGameNotInProgress)
            }
        }

        it("In an existing game for a user that is not part of that game, the result should be the corresponding error") {
            val players = Players("p51", "p52")
            val playerThatMoves = "p53"
            val eventualMoveResult = makeMoveOnBrandNewGame(players, playerThatMoves, GridPosition(First, First))

            ScalaFutures.whenReady(eventualMoveResult) {
                result => assert(result == MoveForPlayerNotInGame(playerThatMoves))
            }
        }
    }

    describe("When making a second move") {
        it("If both players have moved to valid positions we get a successful result for both move operations") {
            val player1 = "p61"
            val player2 = "p62"
            val players = Players(player1, player2)

            val eventualMoveResult = for {
                id <- createGameAndRetrieveId(players)
                _ <- gameLogic.move(id, player1, GridPosition(First, First))
                lastMove <- gameLogic.move(id, player2, GridPosition(First, Center))
            } yield lastMove

            ScalaFutures.whenReady(eventualMoveResult) {
                result => assert(result == SuccessfulMoveResult)
            }
        }

        it("If one of the players move twice we get the corresponding error result") {
            val player1 = "p71"
            val player2 = "p72"
            val players = Players(player1, player2)

            val eventualMoveResult = for {
                id <- createGameAndRetrieveId(players)
                _ <- gameLogic.move(id, player1, GridPosition(First, First))
                lastMove <- gameLogic.move(id, player1, GridPosition(First, Center))
            } yield lastMove

            ScalaFutures.whenReady(eventualMoveResult) {
                result => assert(result == InvalidMoveResult(MoveOutOfTurn))
            }
        }

        it("If one of the players move to an already occupied position we get the corresponding error result") {
            val player1 = "p81"
            val player2 = "p82"
            val players = Players(player1, player2)
            val position = GridPosition(First, First)

            val eventualMoveResult = for {
                id <- createGameAndRetrieveId(players)
                _ <- gameLogic.move(id, player1, position)
                lastMove <- gameLogic.move(id, player2, position)
            } yield lastMove

            ScalaFutures.whenReady(eventualMoveResult) {
                result => assert(result == InvalidMoveResult(PositionAlreadyTaken(position)))
            }
        }
    }

    describe("When making a third move") {
        it("If it is accomplished for a third player we get the corresponding error result") {
            val player1 = "p91"
            val player2 = "p92"
            val players = Players(player1, player2)

            val player3 = "p93"

            val eventualMoveResult = for {
                id <- createGameAndRetrieveId(players)
                _ <- gameLogic.move(id, player1, GridPosition(First, First))
                _ <- gameLogic.move(id, player2, GridPosition(First, Last))
                lastMove <- gameLogic.move(id, player3, GridPosition(Center, Center))
            } yield lastMove

            ScalaFutures.whenReady(eventualMoveResult) {
                result => assert(result == MoveForPlayerNotInGame(player3))
            }
        }

        it("If one of the players move to an already occupied position we get the corresponding error result") {
            val player1 = "p101"
            val player2 = "p102"
            val players = Players(player1, player2)

            val eventualMoveResult = for {
                id <- createGameAndRetrieveId(players)
                _ <- gameLogic.move(id, player1, GridPosition(First, First))
                _ <- gameLogic.move(id, player2, GridPosition(First, Last))
                lastMove <- gameLogic.move(id, player1, GridPosition(First, Last))
            } yield lastMove

            ScalaFutures.whenReady(eventualMoveResult) {
                result => assert(result == InvalidMoveResult(PositionAlreadyTaken(GridPosition(First, Last))))
            }
        }
    }

    private def finishedGameIdWithLogicWinningPlayer1(players: Players, gameLogic: GameLogic): Future[GameId] = {
        val player1 = players.player1
        val player2 = players.player2

        for {
            id <- createGameAndRetrieveId(players, gameLogic)
            _ <- gameLogic.move(id, player1, GridPosition(First, First))
            _ <- gameLogic.move(id, player2, GridPosition(Center, First))
            _ <- gameLogic.move(id, player1, GridPosition(Last, First))
            _ <- gameLogic.move(id, player2, GridPosition(First, Center))
            _ <- gameLogic.move(id, player1, GridPosition(Center, Center))
            _ <- gameLogic.move(id, player2, GridPosition(Last, Center))
            _ <- gameLogic.move(id, player1, GridPosition(First, Last))
        } yield id
    }

    private def finishedDrawGameIdWithLogic(players: Players, gameLogic: GameLogic): Future[GameId] = {
        val player1 = players.player1
        val player2 = players.player2

        for {
            id <- createGameAndRetrieveId(players, gameLogic)
            _ <- gameLogic.move(id, player1, GridPosition(First, First))
            _ <- gameLogic.move(id, player2, GridPosition(Center, Center))
            _ <- gameLogic.move(id, player1, GridPosition(Center, First))
            _ <- gameLogic.move(id, player2, GridPosition(Last, First))
            _ <- gameLogic.move(id, player1, GridPosition(First, Last))
            _ <- gameLogic.move(id, player2, GridPosition(First, Center))
            _ <- gameLogic.move(id, player1, GridPosition(Last, Center))
            _ <- gameLogic.move(id, player2, GridPosition(Center, Last))
            _ <- gameLogic.move(id, player1, GridPosition(Last, Last))
        } yield id
    }

    describe("In an already finished game") {
        val player1 = "p111"
        val player2 = "p112"
        val players = Players(player1, player2)

        val repository = mock[GameRepository]
        val logicWithRepository = GameLogic(repository)(actorSystem, timeout)
        when(repository.insertGame(any[Game])).thenReturn(Future successful ())
        when(repository.upsertLeaderBoard(any[Player])).thenReturn(Future successful ())

        val eventualFinishedGameId = finishedGameIdWithLogicWinningPlayer1(players, logicWithRepository)

        it("When trying to move we get the corresponding error result") {
            val eventualMoveResultOnFinishedGame: Future[MoveResult] = eventualFinishedGameId.flatMap(
                gameLogic.move(_, player2, GridPosition(Last, Last))
            )

            ScalaFutures.whenReady(eventualMoveResultOnFinishedGame) {
                result => assert(result == MoveForGameNotInProgress)
            }
        }

        it("If we try to create another game between the same two players we can") {
            val eventualNewGame: Future[GameCreationResult] = for {
                _ <- eventualFinishedGameId
                newGame <- gameLogic.createGame(players)
            } yield newGame

            ScalaFutures.whenReady(eventualNewGame) {
                result => assert(result.isInstanceOf[GameCreated])
            }
        }

        it("The game persistence logic is called") {
            val eventualGameId: Future[GameId] = finishedGameIdWithLogicWinningPlayer1(players, logicWithRepository)

            val expectedGameGridEntries = Set(
                GridStateEntry(GridPosition(First, First), player1),
                GridStateEntry(GridPosition(Center, First), player2),
                GridStateEntry(GridPosition(Last, First), player1),
                GridStateEntry(GridPosition(First, Center), player2),
                GridStateEntry(GridPosition(Center, Center), player1),
                GridStateEntry(GridPosition(Last, Center), player2),
                GridStateEntry(GridPosition(First, Last), player1)
            )

            ScalaFutures.whenReady(eventualGameId) {
                id => {
                    val expectedGame = Game(
                        id,
                        player1,
                        player2,
                        expectedGameGridEntries,
                        GameOver(player1)
                    )

                    Mockito.verify(repository).insertGame(expectedGame)
                }
            }
        }
    }

    describe("Getting the state of a game") {
        class MemoryRepository extends GameRepository {
            private var storage: Map[GameId, Game] = Map.empty

            override def insertGame(game: Game): Future[Unit] =
                Future successful {
                    storage = storage + (game.id -> game)
                }

            override def gameById(id: GameId): Future[Option[Game]] =
                Future successful storage.get(id)

            override def disconnect(): Unit = ()

            override def upsertLeaderBoard(winner: Player): Future[Unit] =
                Future successful ()

            override def findTop10LeaderBoard(): Future[List[LeaderBoard]] = ???
        }

        it("In a not finished game") {
            val repository = new MemoryRepository
            val logicWithRepository = GameLogic(repository)(actorSystem, timeout)

            val player1 = "p121"
            val player2 = "p122"
            val players = Players(player1, player2)

            val eventualGameId: Future[GameId] = for {
                id <- createGameAndRetrieveId(players, logicWithRepository)
                _ <- logicWithRepository.move(id, player1, GridPosition(First, First))
                _ <- logicWithRepository.move(id, player2, GridPosition(First, Last))
                lastMove <- logicWithRepository.move(id, player1, GridPosition(First, Last))
            } yield id

            val eventualState: Future[Option[GameState]] =
                eventualGameId.flatMap(logicWithRepository.state)

            ScalaFutures.whenReady(eventualState) {
                state => assert(state.contains(GameInProgress))
            }
        }

        it("In a finished game with a winner") {
            val repository = new MemoryRepository
            val logicWithRepository = GameLogic(repository)(actorSystem, timeout)

            val player1 = "p221"
            val player2 = "p222"
            val players = Players(player1, player2)

            val eventualGameId = finishedGameIdWithLogicWinningPlayer1(players, logicWithRepository)

            val eventualState: Future[Option[GameState]] =
                eventualGameId.flatMap(logicWithRepository.state)

            ScalaFutures.whenReady(eventualState) {
                state => assert(state.contains(GameOver(player1)))
            }
        }

        it("In a finished game with no winner (draw game)") {
            val repository = new MemoryRepository
            val logicWithRepository = GameLogic(repository)(actorSystem, timeout)

            val player1 = "p321"
            val player2 = "p322"
            val players = Players(player1, player2)

            val eventualGameId = finishedDrawGameIdWithLogic(players, logicWithRepository)

            val eventualState: Future[Option[GameState]] =
                eventualGameId.flatMap(logicWithRepository.state)

            ScalaFutures.whenReady(eventualState) {
                state => assert(state.contains(DrawGame))
            }
        }
    }
}
