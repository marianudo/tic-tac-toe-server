package me.marianonavas.tictactoe.logic

import me.marianonavas.tictactoe.logic.Game.Player

/**
  *
  */
case class Move(player: Player, position: GridPosition)

/*
  * Here algebraic data type (ADT) that represents not valid moves
  */
sealed trait InvalidMove

case class PositionAlreadyTaken(position: GridPosition) extends InvalidMove

case object GameAlreadyFinished extends InvalidMove

case class MoveFromAThirdPlayer(player: Player) extends InvalidMove

case object MoveOutOfTurn extends InvalidMove


/*
 * ADT that represents the possible results of a move action
 */
sealed trait MoveResult

case object SuccessfulMoveResult extends MoveResult

case class InvalidMoveResult(reason: InvalidMove) extends MoveResult

case object MoveForGameNotInProgress extends MoveResult

case class MoveForPlayerNotInGame(player: Player) extends MoveResult
