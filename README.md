Tic-Tac-Toe REST Server
=======================

Rules
-----

Two players take turns to place a mark in one of the spaces on a 3x3 grid.  Each player cannot place a mark where either
player has placed one previously.  The first player who places three consecutive marks in a horizontal, vertical or
diagonal row wins the game.  If all of the spaces are taken and no player has succeeded in placing 3 marks in a row
then the game is a draw.

API
---
### Create a game ###

    method                : POST
    url                   : /game?player1Id=<id of player 1>&player2Id=<id of player 2>
    example response body : "<id of the new game>"

The client will provide the ids of the players. Players can create games against multiple different players concurrently
but an appropriate error code should be returned if a player tries to create a  new game against a player that they
currently have an unfinished game against.  The response should be a json string containing an id that identifies the
new game.

### Make a move ###

    method                : PUT
    url                   : /game/<id of the game>
    example request body  : {"playerId": "<id of player making the move>", "x": <column index to make a mark in>, , "y": <row index to make a mark in>}
    example response body : <empty>

The <id of the game> will be the id of a game previously created via a call the *Create a game* endpoint.  The player id
will be the id of the player making the move.  An error code should be returned if a player makes a move out of turn.
Assume that player 1 will always go first.

### Get the game state ###

    method                                   : GET
    url                                      : /game/<id of the game>
    example response body (game in progress) : {"winnerId": null, "gameOver": false}
    example response body (win)              : {"winnerId": "<id of the winning player>", "gameOver": true}
    example response body (draw)             : {"winnerId": null, "gameOver": true}

The <id of the game> will be the id of a game previously created via a call the *Create a game* endpoint.  If the game
is still in progress the winnerId should be null and gameOver should be false.  If a player has won then then the
winnerId should be the the id of that player and gameOver should be true. If the game is complete and a draw then the
winnerId should be null and gameOver should be true.

### Get the LeaderBoard with the top 10 scorers (players that have won more games) ###

    method                                   : GET
    url                                      : /top10
    example response body                    : [{"player":"1","points":51},{"player":"you","points":51}]

IMPLEMENTATION NOTES
--------------------

### Overall design ###

A traditional MVC design has been followed. As this is only an API we can consider the view the serialization logic.
The packages structure reflects that decision. The root package of the application (com.spaceape.hiring) contains the
entry point (NoughtsApplication class) and the controller code (NoughtsResource class). Under the logic package we find
actual business logic of the application. It's public interface is formed by both the trait (contract) definition (for
the logic) and the model entities that being part of the business logic itself are going to be needed by the controller
layer. Part of that business logic is the persistence layer, which is located in the repository package inside logic.

Access modifiers are used in each class and trait to limit the visibility of each to the minimum. Only those classes
that are part of the public interface of each layer will be accessible to packages up in the hierarchy. The rest will
be either private or package private (not accessible for upper packages). Classes inside one package should not access
in any way other classes in packages up in the package hierarchy. The compiler cannot enforce that rule, so we have to
be careful. Violating that rule will couple the internals of the layers with each other and the modularity of the
application would be destroyed. Dependencies should go from top to bottom: outer packages can depend on public members
of it's inner packages. Not the other way around.

Each layer is formed by the following elements:

* A trait with the contract (public interface) of the service that is going to be exported by the corresponding layer.
* A companion object for the aforementioned trait that give us an apply method that acts as a factory to get an actual
instance of the given trait. That instance belongs to a class that is not part of the public interface of the layer.
This way we decouple the contract from it's implementation, allowing to change it without breaking anything outside the
layer itself.
* An implementation of the trait that is private, and therefore cannot be instantiated by other layers without using
the aforementioned factory method.
* Some classes and traits that represent the entities (domain model) that belong to the layer. Only those that are meant
to be used by other layers will be public. The rest will have a private modifier that will prevent them from being
accessed from other packages.

The controller layer (resource class) will make use of the logic layer (trait and corresponding factory method). The
logic layer in turn will make use of the repository layer.

### How rules for the game have been coded ###

A pure functional programming approach has been followed to code the game rules. The Grid class represents the state of
the game at any given point in time and give us methods to manipulate it's state. It's an immutable data structure that,
when methods that modify the state of the game are called, returns a new immutable instance that represents the new
state.

Algebraic data types (ADTs) have been used to represent game artifacts (such as game grid positions, move action results,
etc.) and enable a compile time checking of some of the game constraints. For example using the **GridCoordinate** ADT
we make sure at compile time that a position in the game grid that doesn't exist is not referenced (something that cannot
be enforced using plain integer values). Also the use of ADTs give us a compile time checking of all possible logic
paths through the corresponding compiler warning about partial functions not defined for possible values (pattern match
not exhaustive).

### How concurrency has been managed ###

This game is a good example of an stateful program that has to maintain game data consistency when all kind of movements
and actions are executed concurrently. To ease reasoning about the implementation and solve that concurrency / mutable -
shared state problem we use the [actor programming model](https://en.wikipedia.org/wiki/Actor_model) with the
[akka](http://akka.io) library. Each game is handled by a different actor. This way many games can be played concurrently,
but any single game operations are always executed one at a time, preserving the game state consistency.

For the sake of brevity the state of every "in progress" game is kept only in-memory. A real, production ready
implementation should consider the use of [akka persistence](http://doc.akka.io/docs/akka/current/scala/persistence.html)
to recover from failures without loosing the state of those games.

The games that are finished (either draw or with a winner) are not kept in-memory. They are saved into a MongoDB database.
When asking for the state of a game, if that game cannot be found in memory, a query is issued to look for it in the
database. This way we avoid a memory leak and also make response times for game operations minimum. This approach give
use good scalability as the memory footprint per game is extremely small.

The use of actors makes the program able to handle an unbounded number of concurrent games and players with efficiency
and without blocking. It can be tested with probably tens of thousands of concurrent games in a regular laptop without
any issue (I personally bet that it can get to the hundreds of thousands).

### How persistence has been managed ###

As previously stated a MongoDB instance is used to store all completed games once they are finished. The library used
to access the database is [reactivemongo](http://reactivemongo.org). It's an asynchronous library built on top of akka.
It's use allows us to save and query also without blocking, maximizing the performance of the program.

The business logic involve actors communication, which is also asynchronous by default. Futures and it's composition
using map and flatMap is ubiquitous in the codebase.

### About the HTTP layer ###
Although I'm personally a big fan of Dropwizard as a tool to build RESTFUL APIs it's use collides with the fully
asynchronous nature of the business and persistence logic. Dropwizard is designed under the traditional synchronous,
blocking request / response models, and forces us to wait for the future returned by the business logic layer to
return a response to the caller. My suggestion here would be to use a fully asynchronous library such as Spray or Play
to bring the scalability and reactivity of the program to the top.

### Conclusion ###
All the constraints and extra tasks of the assignment has been fulfilled. The leader board feature has also been
implemented. A GET method on the resource class has been added to obtain the top ten scorers for all completed games.
It uses also MongoDB as persistence mechanism.

**In order to run the app the environment variable DB_HOST must be set** to point to the IP or host where the MongoDB
instance is running. The use of the default port (27017) is assumed. The database instance used to run the application
should allowed connections without user / password.