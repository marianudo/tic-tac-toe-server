package me.marianonavas.tictactoe.logic

import me.marianonavas.tictactoe.logic.Game.{GameId, Player}
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

/**
  *
  */
package object repository {
    // Games
    private val gameInProgress = "inProgress"
    private val drawGame = "draw"
    private val gameOver = "gameOver"

    private val firstCoordinate = "first"
    private val centerCoordinate = "center"
    private val lastCoordinate = "last"

    private val xField = "x"
    private val yField = "y"
    private val gameIdField = "gameId"
    private val player1Field = "player1"
    private val player2Field = "player2"
    private val gridStateField = "gridState"
    private val stateField = "state"
    private val winnerField = "winner"
    private val errorValue = "ERROR"
    private val gridPositionField = "position"
    private val playerField = "player"

    implicit object GridPositionWriter extends BSONDocumentWriter[GridPosition] {

        private val coordinatesDictionary: Map[GridCoordinate, String] =
            Map(
                First -> firstCoordinate,
                Center -> centerCoordinate,
                Last -> lastCoordinate
            )

        override def write(t: GridPosition): BSONDocument =
            BSONDocument(
                xField -> coordinatesDictionary.get(t.x),
                yField -> coordinatesDictionary.get(t.y)
            )
    }

    implicit object GridPositionReader extends BSONDocumentReader[GridPosition] {

        private val coordinatesDictionary: Map[String, GridCoordinate] =
            Map(
                firstCoordinate -> First,
                centerCoordinate -> Center,
                lastCoordinate -> Last
            )

        override def read(bson: BSONDocument): GridPosition =
            GridPosition(
                coordinatesDictionary(bson.getAs[String](xField).get),
                coordinatesDictionary(bson.getAs[String](yField).get)
            )
    }

    implicit object GridStateEntryWriter extends BSONDocumentWriter[GridStateEntry] {
        override def write(t: GridStateEntry): BSONDocument =
            BSONDocument(
                gridPositionField -> t.position,
                playerField -> t.player
            )
    }

    implicit object GridStateEntryReader extends BSONDocumentReader[GridStateEntry] {
        override def read(bson: BSONDocument): GridStateEntry =
            GridStateEntry(
                bson.getAs[GridPosition](gridPositionField).get,
                bson.getAs[Player](playerField).get
            )
    }

    implicit object GameWriter extends BSONDocumentWriter[Game] {
        override def write(t: Game): BSONDocument =
            BSONDocument(
                gameIdField -> t.id,
                player1Field -> t.player1,
                player2Field -> t.player2,
                gridStateField -> t.gridState,
                stateField -> {
                    t.state match {
                        case GameInProgress => gameInProgress
                        case DrawGame => drawGame
                        case GameOver(_) => gameOver
                        case _ => errorValue
                    }
                },
                winnerField -> {
                    t.state match {
                        case GameOver(winner) => Some(winner)
                        case _ => None
                    }
                }
            )
    }

    implicit object GameReader extends BSONDocumentReader[Game] {
        override def read(bson: BSONDocument): Game =
            Game(
                bson.getAs[GameId](gameIdField).get,
                bson.getAs[Player](player1Field).get,
                bson.getAs[Player](player2Field).get,
                bson.getAs[Set[GridStateEntry]](gridStateField).get,
                {
                    val state = bson.getAs[String](stateField).get
                    if(state == gameInProgress) GameInProgress
                    else if(state == drawGame) DrawGame
                    else if(state == gameOver) GameOver(bson.getAs[Player](winnerField).get)
                    else UnknownGameState
                }
            )
    }

    def gameIdSelector(id: GameId) = BSONDocument(gameIdField -> id)

    def gamesInProgressForPlayersSelector(p1: Player, p2: Player) =
        BSONDocument(
            player1Field -> p1,
            player2Field -> p2,
            stateField -> gameInProgress
        )



    // Leader Board
    private val player = "player"
    private val points = "points"

    implicit object LeaderBoardWriter extends BSONDocumentWriter[LeaderBoard] {
        override def write(t: LeaderBoard): BSONDocument =
            BSONDocument(
                player -> t.player,
                points -> t.points
            )
    }

    implicit object LeaderBoardReader extends BSONDocumentReader[LeaderBoard] {
        override def read(bson: BSONDocument): LeaderBoard =
            LeaderBoard(
                bson.getAs[Player](player).get,
                bson.getAs[Long](points).get
            )
    }

    def leaderBoardSelector(p: Player) = BSONDocument(player -> p)

    def top10SortSelector = BSONDocument(points -> -1)
}
