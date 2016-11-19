package me.marianonavas.tictactoe.logic

import org.scalatest.{FlatSpec, Matchers}

/**
  *
  */
class GridSpec extends FlatSpec with Matchers {
    val emptyGrid = Grid()

    def isValidGrid(either: Either[InvalidMove, Grid]): Boolean =
        either match {
            case Left(_) => false
            case Right(_) => true
        }

    "An empty grid" should "allow any movement" in {
        val newGrid = emptyGrid.move(Move("1", GridPosition(First, First)))

        assert(isValidGrid(newGrid))
    }

    "An empty grid after a movement" should "return a new grid with a game in progress" in {
        val newGrid = emptyGrid.move(Move("1", GridPosition(First, First)))

        newGrid.right.get.gameState shouldBe GameInProgress
    }

    "Three different moves from 3 players" should "return an invalid move" in {
        val resultOfThirdMove = for {
            a <- emptyGrid.move(Move("1", GridPosition(First, First))).right
            b <- a.move(Move("2", GridPosition(First, Center))).right
            c <- b.move(Move("3", GridPosition(Center, Center))).left
        } yield c

        resultOfThirdMove shouldBe Left(MoveFromAThirdPlayer("3"))
    }

    val gridAfterLegalMovesWithoutFinishGame = for {
        a <- emptyGrid.move(Move("1", GridPosition(First, First))).right
        b <- a.move(Move("2", GridPosition(First, Center))).right
        c <- b.move(Move("1", GridPosition(Center, Center))).right
        d <- c.move(Move("2", GridPosition(Last, Last))).right
        e <- d.move(Move("1", GridPosition(Last, Center))).right
        f <- e.move(Move("2", GridPosition(Center, First))).right
    } yield f

    "Several moves that do not end a game but are legal" should "be allowed" in {
        assert(isValidGrid(gridAfterLegalMovesWithoutFinishGame))
    }

    "Several moves that do not end a game but are legal" should "return a game in progress" in {
        gridAfterLegalMovesWithoutFinishGame.right.get.gameState shouldBe GameInProgress
    }

    "Trying to move to an already taken position" should "return an invalid move" in {
        val grid = for {
            a <- emptyGrid.move(Move("1", GridPosition(First, First))).right
            b <- a.move(Move("2", GridPosition(First, Last))).right
            c <- b.move(Move("1", GridPosition(First, Last))).right // This is the illegal move
            d <- c.move(Move("2", GridPosition(Last, Last))).right
        } yield d

        grid shouldBe Left(PositionAlreadyTaken(GridPosition(First, Last)))
    }

    "When a player wins a game in a complete board" should "show up as the winner in the game state" in {
        val startingGrid = gridAfterLegalMovesWithoutFinishGame.right.get

        val gridAfterWin = for {
            a <- startingGrid.move(Move("1", GridPosition(Last, First))).right
            b <- a.move(Move("2", GridPosition(Center, Last))).right
            c <- b.move(Move("1", GridPosition(First, Last))).right
        } yield c

        gridAfterWin.right.get.gameState shouldBe GameOver("1")
    }

    val gridAfterWinInIncompleteBoard = for {
        a <- emptyGrid.move(Move("1", GridPosition(First, First))).right
        b <- a.move(Move("2", GridPosition(Center, First))).right
        c <- b.move(Move("1", GridPosition(Last, First))).right
        d <- c.move(Move("2", GridPosition(Center, Center))).right
        e <- d.move(Move("1", GridPosition(Last, Last))).right
        f <- e.move(Move("2", GridPosition(Center, Last))).right
    } yield f

    "When a player wins in an incomplete board" should "show up as the winner in the game state" in {
        gridAfterWinInIncompleteBoard.right.get.gameState shouldBe GameOver("2")
    }

    val gridAfterDraw = {
        val initialGrid = gridAfterLegalMovesWithoutFinishGame.right.get

        for {
            a <- initialGrid.move(Move("1", GridPosition(First, Last))).right
            b <- a.move(Move("2", GridPosition(Last, First))).right
            c <- b.move(Move("1", GridPosition(Center, Last))).right
        } yield c
    }

    "When no player wins it" should "return a draw game state" in {
        gridAfterDraw.right.get.gameState shouldBe DrawGame
    }

    "When a game is finished with a winner" should "not allow more movements" in {
        val resultingGrid = gridAfterWinInIncompleteBoard.right.get.move(Move("1", GridPosition(First, Center)))

        resultingGrid.left.get shouldBe GameAlreadyFinished
    }

    "When the first user makes two consecutive moves" should "return an invalid move result" in {
        val gridAfterInvalidMove = for {
            a <- emptyGrid.move(Move("1", GridPosition(First, First))).right
            b <- a.move(Move("1", GridPosition(Center, Center))).right
        } yield b

        gridAfterInvalidMove.left.get shouldBe MoveOutOfTurn
    }

    "When the second user makes two consecutive moves" should "return and invalid move" in {
        val gridAfterInvalidMove = for {
            a <- emptyGrid.move(Move("1", GridPosition(First, First))).right
            b <- a.move(Move("2", GridPosition(Center, Center))).right
            c <- b.move(Move("2", GridPosition(Center, Last))).right
        } yield c

        gridAfterInvalidMove.left.get shouldBe MoveOutOfTurn
    }

    "An empty grid" should "have a game in progress state" in {
        emptyGrid.gameState shouldBe GameInProgress
    }
}
