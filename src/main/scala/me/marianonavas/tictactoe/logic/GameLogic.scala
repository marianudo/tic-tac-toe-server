package me.marianonavas.tictactoe.logic

import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern
import pattern.{ask, pipe}
import akka.util.Timeout
import me.marianonavas.tictactoe.logic.Game.{GameId, Player}
import me.marianonavas.tictactoe.logic.repository.GameRepository

import scala.concurrent.Future
import scala.language.implicitConversions

/**
  *
  */
/**
  * Trait that defines the interface for the business logic
  */
private[tictactoe] trait GameLogic {
    def createGame(players: Players): Future[GameCreationResult]

    def move(gameId: GameId, player: Player, targetPosition: GridPosition): Future[MoveResult]

    def state(gameId: GameId): Future[Option[GameState]]

    def top10Scorers(): Future[List[LeaderBoard]]
}

private class ActorsBasedGameLogic
(gameRepository: GameRepository)
(implicit actorSystem: ActorSystem, akkaTimeout: Timeout) extends GameLogic {

    case class ActorBasedGame(id: GameId, players: Players, handlerActor: ActorRef)

    // Messages that our actors are going to handle
    case class CreateGame(players: Players)

    case class CreateGameAck(createGameActionResult: Either[PlayersAlreadyInAGame, GameId])

    case class ScheduleMove(gameId: GameId, move: Move)

    case class MakeMove(actorBasedGame: ActorBasedGame, move: Move)

    case class MakeMoveAck(result: MoveResult, isGameOver: Boolean)

    case class ScheduleRetrieveGameStatus(gameId: GameId)

    case class RetrieveGameStatus(actorBasedGame: ActorBasedGame)

    case class RetrieveGameStatusAck(maybeGame: Option[Game])

    // Internals of the business logic
    private implicit val executionContext = actorSystem.dispatcher

    private trait OurAskableActor {
        this: Actor =>

        def pipeToSender[T](message: T): pattern.PipeableFuture[T] =
            pipeToSender(Future successful message)

        def pipeToSender[T](eventualMessage: Future[T]): pattern.PipeableFuture[T] =
            pipe(eventualMessage) to sender
    }

    /*
     *
     */
    private class GameSchedulerActor extends Actor with OurAskableActor {

        // Immutable data structure that represents the games in progress
        private class Games(games: Map[GameId, ActorBasedGame] = Map.empty) {
            private def gameView[K](key: ActorBasedGame => K): Map[K, ActorBasedGame] =
                games.foldLeft(Map.empty[K, ActorBasedGame]) {
                    (acc, gamesTuple) => {
                        val game = gamesTuple._2
                        acc + (key(game) -> game)
                    }
                }

            private lazy val gamesByPlayers: Map[Players, ActorBasedGame] = gameView(_.players)

            private lazy val gamesByHandlers: Map[ActorRef, ActorBasedGame] = gameView(_.handlerActor)

            def addGameForPlayers(players: Players): Either[PlayersAlreadyInAGame, Games] = {
                if (gamesByPlayers.contains(players))
                    Left(PlayersAlreadyInAGame(players))
                else {
                    // This is a performance killer. It's ok for the purpose of this exercise,
                    // but other alternatives to generate a unique id should be considered in a real world project-
                    val gameId = UUID.randomUUID.toString

                    val gameHandlerActorRef = context.actorOf(GameHandlerActor.props())

                    Right(
                        new Games(
                            games + (gameId -> ActorBasedGame(
                                gameId, players, gameHandlerActorRef
                            ))
                        )
                    )
                }
            }

            def gameForPlayers(players: Players): Option[ActorBasedGame] =
                gamesByPlayers.get(players)

            def gameIdForPlayers(players: Players): Option[GameId] = {
                gameForPlayers(players).map(_.id)
            }

            def handlerActorForGame(gameId: GameId): Option[ActorRef] =
                gameForId(gameId).map(_.handlerActor)

            def gameForId(gameId: GameId): Option[ActorBasedGame] =
                games.get(gameId)

            def removeGameForHandler(actor: ActorRef): Games =
                gamesByHandlers.get(actor).map(
                    g => new Games(games - g.id)
                ).getOrElse(this)
        }

        private var games: Games = new Games()

        override def receive(): Receive = {
            case CreateGame(players) =>
                val addGameActionResult = games.addGameForPlayers(players)
                val updatedGames = addGameActionResult.right

                updatedGames.foreach(games = _)
                val maybeNewGameId: Either[PlayersAlreadyInAGame, GameId] =
                    updatedGames.map(_.gameIdForPlayers(players).get)

                pipeToSender(CreateGameAck(maybeNewGameId))

            case ScheduleMove(gameId, move@Move(player, _)) =>
                val maybeGame = games.gameForId(gameId)
                maybeGame match {
                    case None => pipeToSender(MakeMoveAck(MoveForGameNotInProgress, isGameOver = false))
                    case Some(actorBasedGame) =>
                        if (actorBasedGame.players.contains(player)) {
                            val eventualMakeMoveAck =
                                (actorBasedGame.handlerActor ? MakeMove(actorBasedGame, move)).mapTo[MakeMoveAck]
                                    .map(ack => {
                                        if(ack.isGameOver) {
                                            games = games.removeGameForHandler(actorBasedGame.handlerActor)
                                            context.stop(actorBasedGame.handlerActor)
                                        }
                                        ack
                                    })

                            pipeToSender(
                                eventualMakeMoveAck
                            )
                        } else
                            pipeToSender(MakeMoveAck(MoveForPlayerNotInGame(player), isGameOver = false))
                }

            case ScheduleRetrieveGameStatus(gameId) =>
                val actorBasedGame = games.gameForId(gameId)
                val maybeEventualGameStatusAck: Option[Future[RetrieveGameStatusAck]] =
                    actorBasedGame.map(
                        g => (g.handlerActor ? RetrieveGameStatus(g)).mapTo[RetrieveGameStatusAck]
                    )

                if (maybeEventualGameStatusAck.isDefined)
                    pipeToSender(maybeEventualGameStatusAck.get)
                else {
                    // we need to obtain the thing from database
                    val eventualMaybePersistedGame: Future[Option[Game]] =
                    gameRepository.gameById(gameId)

                    pipeToSender(
                        eventualMaybePersistedGame.map(RetrieveGameStatusAck)
                    )
                }
        }
    }

    private object GameSchedulerActor {
        def props() = Props(new GameSchedulerActor)
    }

    private class GameHandlerActor extends Actor with OurAskableActor {
        private var grid: Grid = Grid()

        private def fromActorBasedGameToModelGame(actorBasedGame: ActorBasedGame): Game = {
            implicit def fromGridToGridStateEntries(gameGrid: Grid): Set[GridStateEntry] =
                gameGrid.state.foldLeft(Set.empty[GridStateEntry]) {
                    (acc, gridStateTuple) =>
                        acc + GridStateEntry(gridStateTuple._1, gridStateTuple._2)
                }

            Game(
                actorBasedGame.id,
                actorBasedGame.players.player1,
                actorBasedGame.players.player2,
                grid,
                grid.gameState
            )
        }

        override def receive(): Receive = {
            case MakeMove(actorBasedGame, move) =>
                val moveResult: Either[InvalidMove, Grid] = grid.move(move)

                moveResult match {
                    case Left(invalidMove) => pipeToSender(MakeMoveAck(InvalidMoveResult(invalidMove), isGameOver = false))

                    case Right(newGrid) =>
                        if (grid.gameState.gameOver) // This should never happen
                            pipeToSender(MakeMoveAck(InvalidMoveResult(GameAlreadyFinished), isGameOver = true))
                        else {
                            grid = newGrid

                            if (newGrid.gameState.gameOver) {
                                newGrid.gameState match {
                                    case valid: ValidGameState =>
                                        val successfulMoveAck = MakeMoveAck(SuccessfulMoveResult, isGameOver = true)
                                        // 1 - persist finished game and update leader board
                                        // An improvement to be done for a real project would be make this fault tolerant
                                        val eventualAck: Future[MakeMoveAck] = for {
                                            _ <- gameRepository.insertGame(fromActorBasedGameToModelGame(actorBasedGame))
                                            _ <- valid.winnerId.map(
                                                gameRepository.upsertLeaderBoard
                                            ).getOrElse(Future successful())
                                            ack <- Future successful successfulMoveAck
                                        } yield ack

                                        pipeToSender(eventualAck)

                                    case _ => throw new RuntimeException(
                                        s"Due to some bug in the system the state of the game ${actorBasedGame.id} is corrupt"
                                    )
                                }

                            } else {
                                val successfulMoveAck = MakeMoveAck(SuccessfulMoveResult, isGameOver = false)
                                pipeToSender(successfulMoveAck)
                            }
                        }
                }

            case RetrieveGameStatus(actorBasedGame) =>
                pipeToSender(
                    RetrieveGameStatusAck(
                        Some(fromActorBasedGameToModelGame(actorBasedGame))
                    )
                )
        }
    }

    private object GameHandlerActor {
        def props() = Props(new GameHandlerActor)
    }

    private val gameSchedulerActor = actorSystem.actorOf(GameSchedulerActor.props())

    override def createGame(players: Players): Future[GameCreationResult] = {
        val gameCreationResult: Future[CreateGameAck] =
            (gameSchedulerActor ? CreateGame(players)).mapTo[CreateGameAck]

        gameCreationResult.map(_.createGameActionResult match {
            case Right(gameId) => GameCreated(gameId)
            case Left(l) => l
        })
    }

    override def move(gameId: GameId, player: Player, targetPosition: GridPosition): Future[MoveResult] = {
        val eventualMoveResult: Future[MakeMoveAck] =
            (gameSchedulerActor ? ScheduleMove(gameId, Move(player, targetPosition))).mapTo[MakeMoveAck]

        eventualMoveResult.map(_.result)
    }

    override def state(gameId: GameId): Future[Option[GameState]] = {
        val eventualStateResult: Future[RetrieveGameStatusAck] =
            (gameSchedulerActor ? ScheduleRetrieveGameStatus(gameId)).mapTo[RetrieveGameStatusAck]

        eventualStateResult.map(_.maybeGame.map(_.state))
    }

    override def top10Scorers(): Future[List[LeaderBoard]] =
        gameRepository.findTop10LeaderBoard()
}

private[tictactoe] object GameLogic {
    def apply(gameRepository: GameRepository)(implicit actorSystem: ActorSystem, akkaTimeout: Timeout): GameLogic =
        new ActorsBasedGameLogic(gameRepository)
}
