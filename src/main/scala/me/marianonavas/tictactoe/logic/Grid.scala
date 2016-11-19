package me.marianonavas.tictactoe.logic

import me.marianonavas.tictactoe.logic.Game.Player

/*
  * Given that the number of possible positions inside the game grid is limited and small an algebraic data type (ADT)
  * will be used to represent those possible positions. This way we enforce compile time checking of the game grid
  * positions and avoid the need of parameter validations and their testing.
  */
sealed trait GridCoordinate

object First extends GridCoordinate

object Center extends GridCoordinate

object Last extends GridCoordinate

/**
  * Represents a coordinate inside the game 3X3 grid
  *
  * @param x The x coordinate.
  * @param y The y coordinate.
  */
case class GridPosition(x: GridCoordinate, y: GridCoordinate)

/*
  * Class that represents the state of the grid (therefore the current state of the game)
  */
private[logic] case class Grid(state: Map[GridPosition, Player] = Map.empty, lastMoveMadeBy: Option[Player] = None) {

    def move(m: Move): Either[InvalidMove, Grid] = {
        if(lastMoveMadeBy.contains(m.player))
            Left(MoveOutOfTurn)
        else if(state.contains(m.position))
            Left(PositionAlreadyTaken(m.position))
        else if(gameState.gameOver)
            Left(GameAlreadyFinished)
        else if(state.values.size == 2 && !state.values.toSet.contains(m.player))
            Left(MoveFromAThirdPlayer(m.player))
        else
            Right(Grid(state + (m.position -> m.player), Some(m.player)))
    }

    private type PlayerPlay = Set[GridPosition]

    lazy val gameState: GameState = {
        val winningCombinations: Set[PlayerPlay] =
            Set(
                // Horizontal winning combinations
                Set(
                    GridPosition(First, First),
                    GridPosition(First, Center),
                    GridPosition(First, Last)
                ),
                Set(
                    GridPosition(Center, First),
                    GridPosition(Center, Center),
                    GridPosition(Center, Last)
                ),
                Set(
                    GridPosition(Last, First),
                    GridPosition(Last, Center),
                    GridPosition(Last, Last)
                ),
                // Vertical winning combinations
                Set(
                    GridPosition(First, First),
                    GridPosition(Center, First),
                    GridPosition(Last, First)
                ),
                Set(
                    GridPosition(First, Center),
                    GridPosition(Center, Center),
                    GridPosition(Last, Center)
                ),
                Set(
                    GridPosition(First, Last),
                    GridPosition(Center, Last),
                    GridPosition(Last, Last)
                ),
                // Diagonal winning combinations
                Set(
                    GridPosition(First, First),
                    GridPosition(Center, Center),
                    GridPosition(Last, Last)
                ),
                Set(
                    GridPosition(First, Last),
                    GridPosition(Center, Center),
                    GridPosition(Last, First)
                )
            )

        val playersPositions: Map[Player, Set[GridPosition]] =
            state.foldLeft(Map.empty[Player, Set[GridPosition]])((acc, mapTuple) => {
                val position = mapTuple._1
                val player = mapTuple._2
                val maybePlayerState = acc.get(player)
                val playerNewState = maybePlayerState.map(_ + position).getOrElse(Set(position))
                acc + (player -> playerNewState)
            })

        val playersWinOrNot: Map[Player, Boolean] =
            playersPositions map {
                (entry: (Player, Set[GridPosition])) =>
                    entry._1 -> winningCombinations.exists(_.forall(entry._2))
            }

        val winnerPlayers: Iterable[Player] = (playersWinOrNot filter (_._2)).keys

        if (winnerPlayers.size > 1)
            MoreThanOneWinnerInvalidState
        else if (winnerPlayers.size == 1)
            GameOver(winnerPlayers.head)
        else if(state.size == 9) // All positions occupied without a winner
            DrawGame
        else
            GameInProgress
    }

}