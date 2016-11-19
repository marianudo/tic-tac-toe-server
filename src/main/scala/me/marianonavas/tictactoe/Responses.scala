package me.marianonavas.tictactoe

private[tictactoe] case class GameCreatedResponse(gameId: String)

private[tictactoe] case class GameStateResponse(winnerId: String, gameOver: Boolean)

private[tictactoe] case class ConflictResponse(cause: String)
