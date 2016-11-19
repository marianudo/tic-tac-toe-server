package me.marianonavas.tictactoe.logic

import me.marianonavas.tictactoe.logic.Game.Player

/**
  *
  */
private[logic] case class Move(player: Player, position: GridPosition)

/*
  * Here algebraic data type (ADT) that represents not valid moves
  */
private[tictactoe] sealed trait InvalidMove

private[tictactoe] case class PositionAlreadyTaken(position: GridPosition) extends InvalidMove

private[tictactoe] case object GameAlreadyFinished extends InvalidMove

private[tictactoe] case class MoveFromAThirdPlayer(player: Player) extends InvalidMove

private[tictactoe] case object MoveOutOfTurn extends InvalidMove


/*
 * ADT that represents the possible results of a move action
 */
private[tictactoe] sealed trait MoveResult

private[tictactoe] case object SuccessfulMoveResult extends MoveResult

private[tictactoe] case class InvalidMoveResult(reason: InvalidMove) extends MoveResult

private[tictactoe] case object MoveForGameNotInProgress extends MoveResult

private[tictactoe] case class MoveForPlayerNotInGame(player: Player) extends MoveResult
