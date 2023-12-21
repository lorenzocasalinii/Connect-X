package connectx.myPlayer;

import connectx.CXPlayer;
import connectx.CXBoard;
import connectx.CXGameState;
import connectx.CXCellState;
import java.util.Random;

public class myPlayer implements CXPlayer {
	
	private long start;    
	private int M;		// number of rows 
	private int N;		// number of columns
	private int X;		// number of pieces to connect in order to win
	private int timeout;		// number of seconds to make the move 
	private int buffer;			// buffer used by alpha-beta
	private int middleCol;			// index of the middle column
	private CXGameState myWin;		
	private CXGameState yourWin;
	private boolean P1;				
	private TranspositionTable transpositionTable;		
	private ZobristHashing zobristHashing;
	private Random rand;
	
    public myPlayer() {
        
    }

	@Override
	public void initPlayer(int M, int N, int X, boolean first, int timeout_in_secs) {
		this.M = M;
		this.N = N;
		this.X = X;
		this.myWin = first ? CXGameState.WINP1 : CXGameState.WINP2;
		this.yourWin = first ? CXGameState.WINP2 : CXGameState.WINP1;
		this.P1 = first ? true : false;
		this.timeout = timeout_in_secs;
		this.buffer = 90;
		this.middleCol = N/2;
		rand = new Random(System.currentTimeMillis());
		transpositionTable = TranspositionTable.getInstance();
		zobristHashing = new ZobristHashing(M, N);
	}

	@Override
	public int selectColumn(CXBoard B) {
		start = System.currentTimeMillis();  		// stores the start time
		
		// check if we can win immediately
		for (int col = 0; col < N; col++) {
			if (!B.fullColumn(col)) {
				// simulate the move
				B.markColumn(col);
				// check if it leads to victory, if yes, remove the move and return that move
				if (B.gameState() == myWin) {
					B.unmarkColumn();
					return col;
				}
				// remove the move
				B.unmarkColumn();
			}
		}

		// if we are at the beginning of the match, play to occupy the central column
		if (B.numOfMarkedCells() == 0)
			return middleCol;
		if (B.numOfMarkedCells() == 1)
			return middleCol;
		if (B.getLastMove().j == middleCol && !B.fullColumn(middleCol) && B.numOfMarkedCells() < 5)
			return middleCol;
		
		// if only one column is available, choose that one
		Integer[] availableColumns = B.getAvailableColumns();
		if (availableColumns.length == 1)
			return availableColumns[0];
			
		// otherwise we have to explore the game tree and choose a move
		// perform a search on the game tree using alpha-beta pruning with iterative deepening
		int depth = 1;  	// set the depth to 1
		double bestMoveScore = 0;  		// set the best move score to 0
		int bestMove = availableColumns[availableColumns.length / 2];  // choose the central column among the available ones in case alpha-beta doesn't find a better move in the available time
		
		// as long as we have available time, increase the search depth of the game tree
		while ((System.currentTimeMillis() - start) / 1000.0 < timeout - 0.01) {
			// call alphabeta with the board, true (indicating our turn), depth, and initially set alpha and beta to positive and negative infinity
			double moveScore = alphabeta(B, true, depth, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
			// if we run out of time after alphabeta execution, exit the loop
			if ((System.currentTimeMillis() - start) / 1000.0 >= timeout - 0.01)
				break;
			// update the best score found
			bestMoveScore = moveScore;
			// find the move corresponding to the new score calculated by alphabeta at a certain depth
			bestMove = getBestMove(B, true, depth, bestMoveScore);
			depth++;  // increment the depth
		}
		return bestMove;
	}
	

	private double alphabeta(CXBoard B, boolean myTurn, int depth, double alpha, double beta) {
		double eval;  	// best value found
		Integer[] availableColumns = B.getAvailableColumns();
		long boardHash = zobristHashing.calculateBoardHash(B);  	// calculate the key corresponding to the current game state using Zobrist Hashing
		
		if (transpositionTable.contains(boardHash))  		// check if the hash table already contains the evaluation of the current game state
			return transpositionTable.retrieve(boardHash);  	// if positive, return that value directly
		
		// if depth reaches 0 or the game is no longer open or the available time expires, then evaluate the game state
		if (depth <= 0 || B.gameState() != CXGameState.OPEN || (System.currentTimeMillis() - start) / 1000.0 > timeout * (buffer / 100.0)) {
			eval = evaluate(B);
		}

		// if it's our turn try to maximize the value
		else if (myTurn) {
			eval = Double.NEGATIVE_INFINITY;
			// check available columns
			for (int col : availableColumns) {
				// simulate the move
				B.markColumn(col);
				// update the eval value by taking the maximum value among the children
				eval = Math.max(eval, alphabeta(B, false, depth - 1, alpha, beta));
				// update the alpha value by taking the maximum between alpha and eval
				alpha = Math.max(eval, alpha);
				// remove the move
				B.unmarkColumn();
				// check if pruning is possible
				if (beta <= alpha) {
					break;
				}
			}
		}
		// if it's the opponent's turn try to minimize the value
		else {
			eval = Double.POSITIVE_INFINITY;
			// check available columns
			for (int col : availableColumns) {
				// simulate the move
				B.markColumn(col);
				// update the eval value by taking the minimum value among the children
				eval = Math.min(eval, alphabeta(B, true, depth - 1, alpha, beta));
				// update the beta value by taking the minimum between beta and eval
				beta = Math.min(eval, beta);
				// remove the move
				B.unmarkColumn();
				// check if pruning is possible
				if (beta <= alpha) {
					break;
				}
			}
		}
		transpositionTable.store(boardHash, eval);  // before returning the value, store it in the transposition table
		return eval;
	}	
	

	private int getBestMove(CXBoard B, boolean myTurn, int depth, double targetScore) {
		Integer[] availableColumns = B.getAvailableColumns();		
		int bestMove = availableColumns[rand.nextInt(availableColumns.length)];
		double scoreThreshold = 1;  	// set a threshold so that the search for the score doesn't have to be exactly precise but falls within a certain range
		double bestMoveScore = myTurn ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;  		// the best score is set to + or - infinity depending on which player is playing
		
		// if we have no available time left, return the best score
		if ((System.currentTimeMillis() - start) / 1000.0 < timeout) {
			// iterate through available columns
			for (int col : availableColumns) {
				// simulate making the move
				B.markColumn(col);
				// calculate the score of the new move with alphabeta
				double moveScore = alphabeta(B, !myTurn, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
				// remove the move
				B.unmarkColumn();
				// check if the found score matches the one we are looking for
				if (Math.abs(moveScore - targetScore) <= scoreThreshold)
					return col;
				// if the score doesn't match but is better than the previously found one, update it
				if ((myTurn && moveScore > bestMoveScore) || (!myTurn && moveScore < bestMoveScore)) {
					bestMoveScore = moveScore;
					bestMove = col;
				}
			}
		}
		return bestMove;
	}


	private double evaluate(CXBoard B) {
		// if the current game state leads to our win, return positive infinity
		if (B.gameState() == myWin)
			return Double.POSITIVE_INFINITY;
		// if the current game state leads to the opponent's win, return negative infinity
		if (B.gameState() == yourWin)
			return Double.NEGATIVE_INFINITY;
		// if the current game state leads to a draw, return 0
		else if (B.gameState() == CXGameState.DRAW)
			return 0.0;
		// otherwise, return a value based on the configuration of the cells
		else {
			boolean myThreats = checkForMajorThreats(B, P1 ? true : false);  // check if we can win on the next move
			if (myThreats)
				return Double.POSITIVE_INFINITY;
			boolean yourThreats = checkForMajorThreats(B, P1 ? false : true);  // check if we can lose on the next move
			if (yourThreats)
				return Double.NEGATIVE_INFINITY;
			double connections = countConnections(B, P1 ? true : false);  // check the sequences on the game board
			return connections;
		}
	}	


	private double countConnections(CXBoard B, boolean myTurn) {
		double bestScore;
		// initialize bestScore depending on who's playing 
		if (myTurn)
			bestScore = Double.NEGATIVE_INFINITY;
		else
			bestScore = Double.POSITIVE_INFINITY;
		
		// iterate through the columns
		for (int col = 0; col < N; col++) {
			if (!B.fullColumn(col)) {
				// simulate the move
				B.markColumn(col);
				double colScore = 0.0;
				// count our connections
				colScore += countRows(B, myTurn);
				colScore += countDiagonals(B, myTurn);
				colScore += countColumns(B, myTurn);
				// count opponent's connections
				colScore -= countRows(B, !myTurn);
				colScore -= countDiagonals(B, !myTurn);
				colScore -= countColumns(B, !myTurn);
				// if the score is better than what has been found so far, update it
				if ((myTurn && colScore > bestScore) || (!myTurn && colScore < bestScore)) {
					bestScore = colScore;
				}
				// remove the move
				B.unmarkColumn();
			}
		}
		return bestScore;
	}


	// Returns the maximum height among all columns
	private int highestColumn(CXBoard B) {
		int max = 0;
		int count;
		int row;
		int col = 0;
		// iterate through all columns
		while (col < N) {
			count = 0;
			row = M - 1;
			// start from the bottom row
			while (row >= 0) {
				// if the cell is not free, increment the counter.
				if (B.cellState(row, col) != CXCellState.FREE) {
					count++;
					row--;
				} else {
					// Move to the next column if the cell is free.
					break;
				}
			}
			// update the maximum height
			if (count > max)
				max = count;
			if (count == M)
				return M;
			col++;
		}
		return max;
	}


	// Checks the formed connections in rows and calculate a score
	private double countRows(CXBoard B, boolean myTurn) {
		CXCellState myPlayer = myTurn ? CXCellState.P1 : CXCellState.P2;
		CXCellState otherPlayer = myTurn ? CXCellState.P2 : CXCellState.P1;
		double rowScore = 0.0;
		int maxHeight = M - highestColumn(B); 		// Get the maximum height among all columns
		
		for (int row = M - 1; row >= maxHeight; row--) {
			for (int col = 0; col <= N - X; col++) {
				int myPieces = 0;
				int myPieceLastPos = -1;
				int freeSpaces = 0;
				int freeSpaceFirstPos = -1;
				int freeSpaceLastPos = -1;
				
				// to understand better let's assume X = 4, however it works with any X value  
				for (int i = 0; i < X; i++) {
					int currentCol = col + i;

					if (B.cellState(row, currentCol) == myPlayer) {
						myPieces++;
						myPieceLastPos = i;

						// Scenario: FREE P1 FREE P1
						if (myPieces == X - 2 && freeSpaces == 2 && freeSpaceFirstPos == 0 && freeSpaceLastPos == X - 2) {
							if (currentCol < N - 1) {
								if (B.cellState(row, currentCol + 1) == CXCellState.FREE) {
									rowScore += 10000;
								} else {
									rowScore += 10;
								}
								col++;
							} else {
								rowScore += 10;
							}
							break;
						}

						// Scenario: FREE FREE P1 P1
						if (myPieces == X - 2 && freeSpaces == 2 && freeSpaceFirstPos == 0 && freeSpaceLastPos == 1) {
							if (currentCol < N - 1) {
								if (B.cellState(row, currentCol + 1) == CXCellState.FREE) {
									rowScore += 10000;
								} else {
									rowScore += 10;
								}
								col++;
							} else {
								rowScore += 10;
							}
							break;
						}

						// Scenario: P1 FREE FREE P1
						if (myPieces == X - 2 && freeSpaces == 2) {
							rowScore += 10;
							break;
						}

						// Scenario: FREE P1 P1 P1
						if (myPieces == X - 1 && freeSpaces == 1 && freeSpaceFirstPos == 0) {
							if (currentCol < N - 1) {
								if (B.cellState(row, currentCol + 1) == CXCellState.FREE) {
									return 100000;
								} else {
									rowScore += 1000;
								}
								col++;
							} else {
								rowScore += 1000;
							}
							break;
						}

						// Scenario: P1 FREE P1 P1 or Scenario: P1 P1 FREE P1
						if (myPieces == X - 1 && freeSpaces == 1) {
							rowScore += 1000;
							break;
						}
					} else if (B.cellState(row, currentCol) == otherPlayer) {
						break;
					} else if (B.cellState(row, currentCol) == CXCellState.FREE) {
						if (freeSpaces == 0) {
							freeSpaceFirstPos = i;
						} else if (freeSpaces >= X - 2) {
							if (myPieces > 0) {
								rowScore += 1;
							}
							break;
						}
						freeSpaces++;
						freeSpaceLastPos = i;

						// Scenario: P1 P1 P1 FREE
						if (myPieces == X - 1) {
							if (col != 0) {
								if (B.cellState(row, currentCol - X) == CXCellState.FREE) {
									return 100000;
								} else {
									rowScore += 1000;
								}
							} else {
								rowScore += 1000;
							}
							break;
						}

						// Scenario: FREE P1 P1 FREE
						if (myPieces == X - 2 && freeSpaces == 2 && myPieceLastPos == X - 2 && freeSpaceFirstPos == 0) {
							if (currentCol != N - 1) {
								if (B.cellState(row, currentCol + 1) == CXCellState.FREE) {
									rowScore += 10000;
								} else {
									rowScore += 10;
								}
								col++;
							} else {
								rowScore += 10;
							}
							break;
						}

						// Scenario: P1 FREE P1 FREE
						if (myPieces == X - 2 && freeSpaces == 2 && myPieceLastPos == X - 2) {
							rowScore += 10;
							break;
						}

						// Scenario: P1 P1 FREE FREE
						if (myPieces == X - 2 && freeSpaces == 2) {
							rowScore += 10;
							break;
						}
					}
				}
			}
		}
		return rowScore;
	}


	// Checks the formed connections in columns and calculate a score
	private double countColumns(CXBoard B, boolean myTurn) {
		CXCellState player = myTurn ? CXCellState.P1 : CXCellState.P2;
		CXCellState otherPlayer = myTurn ? CXCellState.P2 : CXCellState.P1;
		double colScore = 0.0;

		for (int col = 0; col < N; col++) {
			int myPieces = 0;

			for (int row = M - 1; row >= X - 1; row--) {
				if (B.cellState(row, col) == player) {
					myPieces++;
				} else if (B.cellState(row, col) == otherPlayer) {
					myPieces = 0;
				} else {
					if (myPieces > 0) {
						colScore += Math.pow(10, myPieces - 1);
					}
					break;
				}
			}
		}

		return colScore;
	}

	
	// Checks the formed connections in diagonals and calculate a score
	private double countDiagonals(CXBoard B, boolean myTurn) {
		CXCellState myPlayer = myTurn ? CXCellState.P1 : CXCellState.P2;
		CXCellState otherPlayer = myTurn ? CXCellState.P2 : CXCellState.P1;
		double diagScore = 0.0;

		// From bottom left to top right
		for (int row = M - 1; row >= X - 1; row--) {
			for (int col = 0; col <= N - X; col++) {
				int myPieces = 0;
				int myPieceLastPos = -1;
				int freeSpaces = 0;
				int freeSpaceFirstPos = -1;
				int freeSpaceLastPos = -1;

				for (int i = 0; i < X; i++) {
					int currentCol = col + i;
					int currentRow = row - i;

					if (B.cellState(currentRow, currentCol) == myPlayer) {
						myPieces++;
						myPieceLastPos = i;

						// Scenario FREE P1 FREE P1
						if (myPieces == X - 2 && freeSpaces == 2 && freeSpaceFirstPos == 0 && freeSpaceLastPos == X - 2) {
							if (currentCol != N - 1 && currentRow != 0) {
								if (B.cellState(currentRow - 1, currentCol + 1) == CXCellState.FREE) {
									diagScore += 10000;
								} else {
									diagScore += 10;
								}
							} else {
								diagScore += 10;
							}
							break;
						}
						// Scenario FREE FREE P1 P1
						if (myPieces == X - 2 && freeSpaces == 2 && freeSpaceFirstPos == 0 && freeSpaceLastPos == 1) {
							if (currentCol != N - 1 && currentRow != 0) {
								if (B.cellState(currentRow - 1, currentCol + 1) == CXCellState.FREE) {
									diagScore += 10000;
								} else {
									diagScore += 10;
								}
							} else {
								diagScore += 10;
							}
							break;
						}
						// Scenario P1 FREE FREE P1
						if (myPieces == X - 2 && freeSpaces == 2) {
							diagScore += 10;
							break;
						}
						// Scenario FREE P1 P1 P1
						if (myPieces == X - 1 && freeSpaces == 1 && freeSpaceFirstPos == 0) {
							if (currentCol != N - 1 && currentRow != 0) {
								if (B.cellState(currentRow - 1, currentCol + 1) == CXCellState.FREE) {
									return 100000;
								} else {
									diagScore += 1000;
								}
							} else {
								diagScore += 1000;
							}
							break;
						}
						// Scenario P1 FREE P1 P1 or Scenario P1 P1 FREE P1
						if (myPieces == X - 1 && freeSpaces == 1) {
							diagScore += 1000;
							break;
						}
					} else if (B.cellState(currentRow, currentCol) == otherPlayer) {
						break;
					} else if (B.cellState(currentRow, currentCol) == CXCellState.FREE) {
						if (freeSpaces == 0) {
							freeSpaceFirstPos = i;
						} else if (freeSpaces >= X - 2) {
							if (myPieces > 0) {
								diagScore += 1;
							}
							break;
						}
						freeSpaces++;
						freeSpaceLastPos = i;

						// Scenario P1 P1 P1 FREE
						if (myPieces == X - 1) {
							if (col != 0) {
								if (B.cellState(currentRow, currentCol - X) == CXCellState.FREE) {
									return 100000;
								} else {
									diagScore += 1000;
								}
							} else {
								diagScore += 1000;
							}
							break;
						}
						// Scenario FREE P1 P1 FREE
						if (myPieces == X - 2 && freeSpaces == 2 && myPieceLastPos == X - 2 && freeSpaceFirstPos == 0) {
							if (currentCol != N - 1) {
								if (B.cellState(currentRow - 1, currentCol + 1) == CXCellState.FREE) {
									diagScore += 10000;
								} else {
									diagScore += 10;
								}
								col++;
							} else {
								diagScore += 10;
							}
							break;
						}
						// Scenario P1 FREE P1 FREE
						if (myPieces == X - 2 && freeSpaces == 2 && myPieceLastPos == X - 2) {
							diagScore += 10;
							break;
						}
						// Scenario P1 P1 FREE FREE
						if (myPieces == X - 2 && freeSpaces == 2) {
							diagScore += 10;
							break;
						}
					}
				}
			}
		}

		// From bottom right to top left
		for (int row = M - 1; row >= X - 1; row--) {
			for (int col = N - 1; col >= X - 1; col--) {
				int myPieces = 0;
				int freeSpaces = 0;
				int myPieceLastPos = -1;
				int freeSpaceFirstPos = -1;
				int freeSpaceLastPos = -1;

				for (int i = 0; i < X; i++) {
					int currentCol = col - i;
					int currentRow = row - i;

					if (B.cellState(currentRow, currentCol) == myPlayer) {
						myPieces++;
						myPieceLastPos = i;

						// Scenario FREE P1 FREE P1
						if (myPieces == X - 2 && freeSpaces == 2 && freeSpaceFirstPos == 0 && freeSpaceLastPos == X - 2) {
							if (currentCol != 0 && currentRow != 0) {
								if (B.cellState(currentRow - 1, currentCol - 1) == CXCellState.FREE) {
									diagScore += 10000;
								} else {
									diagScore += 10;
								}
							} else {
								diagScore += 10;
							}
							break;
						}
						// Scenario FREE FREE P1 P1
						if (myPieces == X - 2 && freeSpaces == 2 && freeSpaceFirstPos == 0 && freeSpaceLastPos == 1) {
							if (currentCol != 0 && currentRow != 0) {
								if (B.cellState(currentRow - 1, currentCol - 1) == CXCellState.FREE) {
									diagScore += 10000;
								} else {
									diagScore += 10;
								}
							} else {
								diagScore += 10;
							}
							break;
						}
						// Scenario P1 FREE FREE P1
						if (myPieces == X - 2 && freeSpaces == 2) {
							diagScore += 10;
							break;
						}
						// Scenario FREE P1 P1 P1
						if (myPieces == X - 1 && freeSpaces == 1 && freeSpaceFirstPos == 0) {
							if (currentCol != 0 && currentRow != 0) {
								if (B.cellState(currentRow - 1, currentCol - 1) == CXCellState.FREE) {
									return 100000;
								} else {
									diagScore += 1000;
								}
							} else {
								diagScore += 1000;
							}
							break;
						}
						// Scenario P1 FREE P1 P1 or Scenario P1 P1 FREE P1
						if (myPieces == X - 1 && freeSpaces == 1) {
							diagScore += 1000;
							break;
						}
					} else if (B.cellState(currentRow, currentCol) == otherPlayer) {
						break;
					} else if (B.cellState(currentRow, currentCol) == CXCellState.FREE) {
						if (freeSpaces == 0) {
							freeSpaceFirstPos = i;
						} else if (freeSpaces >= X - 2) {
							if (myPieces > 0) {
								diagScore += 1;
							}
							break;
						}
						freeSpaces++;
						freeSpaceLastPos = i;

						// Scenario FREE P1 P1 FREE
						if (myPieces == X - 2 && freeSpaces == 2 && myPieceLastPos == X - 2 && freeSpaceFirstPos == 0) {
							if (currentCol != 0 && currentRow != 0) {
								if (B.cellState(currentRow - 1, currentCol - 1) == CXCellState.FREE) {
									diagScore += 10000;
								} else {
									diagScore += 10;
								}
							} else {
								diagScore += 10;
							}
							break;
						}
						// Scenario P1 FREE P1 FREE
						if (myPieces == X - 2 && freeSpaces == 2 && myPieceLastPos == X - 2) {
							diagScore += 10;
							break;
						}
						// Scenario P1 P1 FREE FREE
						if (myPieces == X - 2 && freeSpaces == 2) {
							diagScore += 10;
							break;
						}
						// Scenario P1 P1 P1 FREE
						if (myPieces == X - 1) {
							diagScore += 1000;
							break;
						}
					}
				}
			}
		}
		return diagScore;
	}


	// Check for major threats in the current state
	private boolean checkForMajorThreats(CXBoard B, boolean myTurn) {
		CXGameState win = myTurn ? CXGameState.WINP1 : CXGameState.WINP2;
		
		// iterate through the columns
		for (int col = 0; col < N; col++) {
			if (!B.fullColumn(col)) {
				// simulate the move
				B.markColumn(col);

				// check if it leads to victory, if yes, remove the move and return true
				if (B.gameState() == win) {
					B.unmarkColumn();
					return true;
				}

				// remove the move
				B.unmarkColumn();
			}
		}

		// if no move leads to victory, return false
		return false;
	}
	

	
	public String playerName() {
		return "myPlayer";
	}

}
