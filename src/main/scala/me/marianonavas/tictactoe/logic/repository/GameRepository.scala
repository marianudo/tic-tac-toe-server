package me.marianonavas.tictactoe.logic.repository

import akka.util.Timeout
import me.marianonavas.tictactoe.logic.{Game, LeaderBoard}
import me.marianonavas.tictactoe.logic.Game.{GameId, Player}
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.{MongoConnection, MongoDriver}
import reactivemongo.bson.BSONDocument

import scala.concurrent.{ExecutionContext, Future}

/**
  *
  */
trait GameRepository {
    def insertGame(game: Game): Future[Unit]

    def gameById(id: GameId): Future[Option[Game]]

    def upsertLeaderBoard(winner: Player): Future[Unit]

    def findTop10LeaderBoard(): Future[List[LeaderBoard]]

    def disconnect(): Unit
}


object GameRepository {

    def apply(mongoURI: String)(implicit ec: ExecutionContext, timeout: Timeout): Future[GameRepository] = {
        val driver = MongoDriver()
        val parsedURI = MongoConnection.parseURI(mongoURI)
        val connectionTry = parsedURI.map(driver.connection)
        val eventualConnection: Future[MongoConnection] = Future.fromTry(connectionTry)

        for {
            connection <- eventualConnection
            db <- connection.database("ticTacToe")
        } yield new MongoDbGameRepository(db.collection("games"), db.collection("leaderboard"), connection)
    }
}


private class MongoDbGameRepository
(val gamesColl: BSONCollection, val leaderBoardColl: BSONCollection, connection: MongoConnection)
(implicit ec: ExecutionContext)
    extends GameRepository {

    override def insertGame(game: Game): Future[Unit] =
        gamesColl.insert(game).map(_ => ())

    override def gameById(id: GameId): Future[Option[Game]] =
        gamesColl.find(gameIdSelector(id)).one[Game]

    override def disconnect(): Unit =
        connection.close()

    override def upsertLeaderBoard(winner: Player): Future[Unit] = {
        val eventualMaybeWinnerLeaderBoard: Future[Option[LeaderBoard]] =
            leaderBoardColl.find(leaderBoardSelector(winner)).one[LeaderBoard]

        val eventualUpdatedLeaderBoard: Future[LeaderBoard] =
            eventualMaybeWinnerLeaderBoard.map(
                _.map(
                    l => l.copy(points = l.points + 1)
                ).getOrElse(LeaderBoard(winner, 1))
            )

        for {
            updatedLeaderBoard <- eventualUpdatedLeaderBoard
            _ <- leaderBoardColl.findAndUpdate(
                leaderBoardSelector(winner),
                updatedLeaderBoard,
                fetchNewObject = true,
                upsert = true
            )
        } yield ()
    }

    override def findTop10LeaderBoard(): Future[List[LeaderBoard]] = {
        leaderBoardColl.find(BSONDocument())
            .sort(top10SortSelector)
            .cursor[LeaderBoard]()
            .collect[List](10)
    }
}
