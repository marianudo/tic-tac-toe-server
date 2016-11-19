package me.marianonavas.tictactoe.logic

import me.marianonavas.tictactoe.logic.Game.{GameId, Player}

import scala.concurrent.Future

/**
  *
  */
/**
  * Trait that defines the interface for the business logic
  */
trait GameLogic {
    def createGame(players: Players): Future[GameCreationResult]

    def move(gameId: GameId, player: Player, targetPosition: GridPosition): Future[MoveResult]

    def state(gameId: GameId): Future[Option[GameState]]

    def top10Scorers(): Future[List[LeaderBoard]]
}
