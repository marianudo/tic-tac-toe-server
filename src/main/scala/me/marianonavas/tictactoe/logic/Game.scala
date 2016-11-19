package me.marianonavas.tictactoe.logic

import me.marianonavas.tictactoe.logic.Game.{GameId, Player}

private[logic] sealed trait GameState {
    def gameOver: Boolean
}

private[logic] sealed trait ValidGameState extends GameState {
    def winnerId: Option[Player]
}

private[logic] sealed trait GameNotInProgress extends ValidGameState


private[tictactoe] case object GameInProgress extends ValidGameState {
    lazy val gameOver = false
    lazy val winnerId = None
}

private[tictactoe] case object DrawGame extends GameNotInProgress {
    lazy val gameOver = true
    lazy val winnerId = None
}

private[tictactoe] case class GameOver(winner: Player) extends GameNotInProgress {
    lazy val gameOver: Boolean = true
    lazy val winnerId: Option[Player] = Some(winner)
}


private[logic] sealed trait InvalidGameState extends GameState

private[logic] case object MoreThanOneWinnerInvalidState extends InvalidGameState {
    val gameOver: Boolean = true
}

private[logic] case object UnknownGameState extends InvalidGameState {
    val gameOver: Boolean = false
}

private[logic] case class GridStateEntry(position: GridPosition, player: Player)

private[tictactoe] case class Players(player1: Player, player2: Player) {
    def contains(player: Player): Boolean =
        player == player1 || player == player2
}


/*
 * ADT for the result of a create game operation
 */
private[tictactoe] sealed trait GameCreationResult

private[tictactoe] case class GameCreated(gameId: GameId) extends GameCreationResult

private[tictactoe] case class PlayersAlreadyInAGame(players: Players) extends GameCreationResult


private[logic] case class Game
(
    id: GameId,
    player1: Player,
    player2: Player,
    gridState: Set[GridStateEntry],
    state: GameState
)

private[logic] object Game {
    // Let's give a type alias to the some representations
    // This way we improve readability and also ease the future use of an actual type (case class)
    type GameId = String
    type Points = Int
    type Player = String
}

private[logic] case class LeaderBoard(player: Player, points: Long)