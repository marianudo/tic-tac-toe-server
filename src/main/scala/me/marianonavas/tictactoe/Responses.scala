package me.marianonavas.tictactoe

case class GameCreatedResponse(gameId: String)

case class GameStateResponse(winnerId: String, gameOver: Boolean)

case class ConflictResponse(cause: String)
