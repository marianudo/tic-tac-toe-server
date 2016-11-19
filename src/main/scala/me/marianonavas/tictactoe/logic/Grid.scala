package me.marianonavas.tictactoe.logic

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