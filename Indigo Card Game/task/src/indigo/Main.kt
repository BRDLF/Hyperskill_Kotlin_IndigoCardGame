package indigo

import java.lang.RuntimeException
import kotlin.random.Random

const val HAND_SIZE = 6
const val SUIT = "suit"
const val RANK = "rank"

val ranks = setOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")
val suits = setOf("♦", "♥", "♠", "♣")

data class Card(val rank: String, val suit: String) {
    override fun toString(): String = "$rank$suit"
    fun matches(other: Card): Boolean {
        return rank == other.rank || suit == other.suit
    }
    fun matches(other: Card, matchRule: String): Boolean {
        return when (matchRule) {
            "suit" -> suit == other.suit
            "rank" -> rank == other.rank
            else -> false
        }
    }
    fun points(): Int {
        return when (this.rank) {
            "A", "10", "J", "Q", "K" -> 1
            else -> 0
        }
    }
}

fun generateDeck(): MutableList<Card> {
    val generatedDeck = mutableListOf<Card>()
    for (s in suits) {
        for (r in ranks) {
            generatedDeck.add(Card(rank = r, suit = s))
        }
    }
    return generatedDeck
}

class IndigoGame {
    private val gameDeck: MutableList<Card> = generateDeck()
    private var isPlayerTurn: Boolean?
    private var playerIsRecentWinner: Boolean?
    private var playerFirst: Boolean?
    private val tableStack = mutableListOf<Card>()
    private val playerHand = mutableListOf<Card>()
    private val computerHand = mutableListOf<Card>()
    private val playerWinnings = mutableListOf<Card>()
    private val computerWinnings = mutableListOf<Card>()

    private fun MutableList<Card>.cardChosenMatchesStack(cardIndex: Int, hand: MutableList<Card>): Boolean {
        if (hand.playCard(cardIndex)) {
            this.takeWinnings()
            return true
        }
        return false
    }
    private fun MutableList<Card>.playCard(cardIndex: Int): Boolean {
        tableStack += this[cardIndex]
        this.removeAt(cardIndex)
        if (tableStack.size >= 2 && tableStack.last().matches(tableStack[tableStack.lastIndex-1])) return true
        return false
    }
    private fun MutableList<Card>.shuffle() {
        val shuffledDeck = this.shuffled()
        this.clear()
        this.addAll(shuffledDeck)
    }
    private fun MutableList<Card>.draw(n: Int) {
        this += gameDeck.take(n)
        gameDeck.retainAll(gameDeck.drop(n))
    }
    private fun MutableList<Card>.takeWinnings() {
        this += tableStack
        tableStack.clear()
    }

    init {
        println("Indigo Card Game")
        do {
            playerFirst = inputGoFirst()
            playerIsRecentWinner = playerFirst
            isPlayerTurn = playerFirst
        } while (isPlayerTurn == null)
        gameDeck.shuffle()
        tableStack.draw(4)
        println("Initial cards on the table: ${tableStack.joinToString(" ")}\n")
        printTableStack()
    }
    private fun inputGoFirst(): Boolean? {
        println("Play first?")
        return when (readln().lowercase()) {
            "yes" -> true
            "no" -> false
            else -> null
        }
    }

    fun gameLoop() {
        do{
            if (playerHand.isEmpty() && computerHand.isEmpty()) {
                deal()
            }
            isPlayerTurn = when(isPlayerTurn) {
                true -> {
                    runPlayerTurn() ?: break
                    false
                }
                false -> {
                    runOpponentTurn()
                    true
                }
                null -> {
                    throw RuntimeException("Something went wrong. Turn order was never decided!")
                }
            }
            printTableStack()
        } while(gameContinue())
        println("Game Over")
    }

    private fun printTableStack() {
        if (tableStack.isEmpty()) println("No cards on the table")
        else println("${tableStack.size} cards on the table, and the top card is ${tableStack.lastOrNull()}")
    }
    private fun deal() {
        repeat(HAND_SIZE) {
            playerHand.draw(1)
            computerHand.draw(1)
        }
    }

    private fun runPlayerTurn(): Unit? {
        printPlayerHand()
        while (true) {
            when(val chosen = inputPlayCard()) {
                -1 -> return null
                null -> {}
                else ->  {
                    if (playerWinnings.cardChosenMatchesStack(chosen, playerHand)) {
                        playerIsRecentWinner = true
                        cardWinShowScore("Player")
                    }
                    println()
                    return Unit
                }
            }
        }
    }
    private fun printPlayerHand() {
        print("Cards in hand: ")
        for (i in playerHand.indices) {
            print("${i+1})${playerHand[i]} ")
        }
        println()
    }
    private fun inputPlayCard(): Int? {
        println("Choose a card to play (1-${playerHand.size}):")
        val userInput = readln()
        if (userInput == "exit") return -1
        if (userInput.toIntOrNull() in 1..playerHand.size) return userInput.toInt().minus(1)
        return null
    }

    private fun runOpponentTurn() {
        printComputerHand()
        if (computerWinnings.cardChosenMatchesStack(computerChoosesCard(), computerHand)) {
            playerIsRecentWinner = false
            cardWinShowScore("Computer")
        }
        println()
    }
    private fun printComputerHand() {
        if (computerHand.isEmpty()) {
            println()
            return
        }
        else for (card in computerHand) {
            print("$card ")
        }
        println()
    }
    private fun computerChoosesCard(): Int {
        var chosen = Random.nextInt(0, computerHand.size) //1. One card in hand OR "Random card
        if (computerHand.size != 1) {
            val candidateCardIndices = mutableListOf<Int>()
            if(tableStack.isNotEmpty()) {
                for (cardIndex in computerHand.indices) {   //Find Candidates
                    if (computerHand[cardIndex].matches(tableStack.last())) {
                        candidateCardIndices += cardIndex
                    }
                }
            }
            if (candidateCardIndices.size == 1) { //2. One Eligible Card
                chosen = candidateCardIndices.last()
            }
            else if (tableStack.isEmpty() || candidateCardIndices.isEmpty()) {    //3. Empty Table OR 4. Empty Candidates
                val mSI = matchingJunk(SUIT)
                if (mSI.isNotEmpty()) {
                    chosen = mSI[Random.nextInt(0, mSI.size)]
                }
                else {
                    val mRI = matchingJunk(RANK)
                    if (mRI.isNotEmpty()) {
                        chosen = mRI[Random.nextInt(0, mRI.size)]
                    }
//                    else chosen = Random.nextInt(0, computerHand.size)
                }
            }
            else {  //5. Multiple Candidates
                val mSI = matchingTopCard(SUIT)
                chosen = if (mSI.size >= 2) {
                    mSI[Random.nextInt(0, mSI.size)]
                } else {
                    val mRI = matchingTopCard(RANK)
                    if (mRI.size >= 2) {
                        mRI[Random.nextInt(0, mRI.size)]
                    } else {
                        candidateCardIndices[Random.nextInt(0, candidateCardIndices.size)]
                    }
                }

            }
        }
        println("Computer plays ${computerHand[chosen]}")
        return chosen
    }

    private fun matchingJunk(matchRule: String): List<Int> {
        val returnList: MutableSet<Int> = mutableSetOf()
        for (firstCardIndex in computerHand.indices) {
            val firstCard = computerHand[firstCardIndex]
            for (secondCardIndex in firstCardIndex .. computerHand.lastIndex) {
                val secondCard = computerHand[secondCardIndex]
                if (firstCard != secondCard) {
                    when (matchRule) {
                        "suit", "rank" -> if (firstCard.matches(secondCard, matchRule)) {
                            returnList.add(firstCardIndex)
                            returnList.add(secondCardIndex)
                        }
                    }
                }
            }
        }
        return returnList.toList()
    }
    private fun matchingTopCard(matchRule: String): List<Int> {
        val returnList = mutableListOf<Int>()
        val topCard = tableStack.last()
        for (cardIndex in computerHand.indices) {
            if (topCard.matches(computerHand[cardIndex], matchRule)) returnList.add(cardIndex)
        }
        return returnList
    }
    private fun gameContinue(): Boolean {
        if (gameDeck.isEmpty() && playerHand.isEmpty() && computerHand.isEmpty()) {
            if (playerFirst!!) {
                playerWinnings.takeWinnings()
            }
            else {
                computerWinnings.takeWinnings()
            }
            showScore(true)
            return false
        }
        return true
    }
    private fun cardWinShowScore(winner: String) {
        println("$winner wins cards")
        showScore(false)
    }
    private fun showScore(gameEnd: Boolean) {
        println("Score: Player ${calculatePlayerScore(gameEnd)} - Computer ${calculateComputerScore(gameEnd)}")
        println("Cards: Player ${playerWinnings.size} - Computer ${computerWinnings.size}")
    }
    private fun calculatePlayerScore(gameEnd: Boolean): Int {
        return if (playerWinnings.isEmpty()) 0
        else {
            var score = if (gameEnd) when (true) {
                (playerWinnings.size > computerWinnings.size) -> 3
                (playerWinnings.size == computerWinnings.size) -> if (playerIsRecentWinner!!) 3 else 0
                else -> 0
            }
            else 0
            for (card in playerWinnings) {
                score += card.points()
            }
            return score
        }
    }
    private fun calculateComputerScore(gameEnd: Boolean): Int {
        return if (computerWinnings.isEmpty()) 0
        else {
            var score = if (gameEnd) when (true) {
                (playerWinnings.size < computerWinnings.size) -> 3
                (playerWinnings.size == computerWinnings.size) -> if (!playerIsRecentWinner!!) 3 else 0
                else -> 0
            }
            else 0
            for (card in computerWinnings) {
                score += card.points()
            }
            return score
        }
    }

}

fun main() {
    val myGame = IndigoGame()
    myGame.gameLoop()
}