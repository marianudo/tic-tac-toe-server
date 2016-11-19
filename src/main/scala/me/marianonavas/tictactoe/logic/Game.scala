package me.marianonavas.tictactoe.logic

import me.marianonavas.tictactoe.logic.Game.{GameId, Player}

sealed trait GameState {
    def gameOver: Boolean
}

sealed trait ValidGameState extends GameState {
    def winnerId: Option[Player]
}

sealed trait GameNotInProgress extends ValidGameState


case object GameInProgress extends ValidGameState {
    lazy val gameOver = false
    lazy val winnerId = None
}

case object DrawGame extends GameNotInProgress {
    lazy val gameOver = true
    lazy val winnerId = None
}

case class GameOver(winner: Player) extends GameNotInProgress {
    lazy val gameOver: Boolean = true
    lazy val winnerId: Option[Player] = Some(winner)
}


sealed trait InvalidGameState extends GameState

case object MoreThanOneWinnerInvalidState extends InvalidGameState {
    val gameOver: Boolean = true
}

case object UnknownGameState extends InvalidGameState {
    val gameOver: Boolean = false
}

case class GridStateEntry(position: GridPosition, player: Player)

/**
  *
  */
case class Game
(
    id: GameId,
    player1: Player,
    player2: Player,
    gridState: Set[GridStateEntry],
    state: GameState
)

object Game {
    // Let's give a type alias to the some representations
    // This way we improve readability and also ease the future use of an actual type (case class)
    type GameId = String
    type Points = Int
    type Player = String
}
