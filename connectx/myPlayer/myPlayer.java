package connectx.myPlayer;

import connectx.CXPlayer;
import connectx.CXBoard;
import connectx.CXGameState;
import connectx.CXCellState;
import java.util.Random;

public class myPlayer implements CXPlayer {
	
	private long start;    
	private int M;		// numero di righe 
	private int N;		// numero di colonne
	private int X;		// numero di pezzi da collegare per vincere
	private int timeout;		// tempo disponibile per scegliere la mossa
	private int buffer;			// buffer utilizzato in alphabeta 	
	private int middleCol;		// indice della colonna centrale
	private CXGameState myWin;		
	private CXGameState yourWin;
	private boolean P1;		// variabile booleana che indica se siamo il primo player	
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
		start = System.currentTimeMillis();				// viene memorizzato il tempo di partenza
		
		// conquistare la colonna centrale
		if(B.numOfMarkedCells() == 0) 
			return middleCol;
		if(B.numOfMarkedCells() == 1) 
			return middleCol;
		if(B.getLastMove().j == middleCol && !B.fullColumn(middleCol) && B.numOfMarkedCells() < 5) 
			return middleCol;
		
		// se rimane una sola colonna disponibile allora viene scelta quella
		Integer [] availableColumns = B.getAvailableColumns();
		if(availableColumns.length == 1) 
			return availableColumns[0];
			
		// la mossa va scelta
		// viene effettuata una ricerca sul game tree mediante alphabeta utilizzando un approccio iterative deepening
		int depth = 1;		// la profondità viene settata a 1
		double bestMoveScore = 0;		// il punteggio della mossa migliore viene settato a 0
		int bestMove = availableColumns[availableColumns.length/2];		// viene scelta la colonna centrale tra quelle disponibili nel caso in cui alphabeta non trovi una mossa migliore nel tempo disponibile
		// finchè abbiamo tempo disponibile, aumentiamo la profondità di ricerca del game tree
		while((System.currentTimeMillis()-start)/1000.0 < timeout - 0.01) {
			// chiamiamo alphabeta passando come argomenti la tavola, true (per indicare che è il nostro turno), la profondità e alpha e beta inizialmente settati a + e - infinito
            double moveScore = alphabeta(B, true, depth, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
			// se dopo aver eseguito alphabeta abbiamo esaurito il tempo disponibile, usciamo dal ciclo
            if((System.currentTimeMillis() - start) / 1000.0 >= timeout - 0.01) 
				break;
			// aggiorniamo il miglior punteggio trovato
			bestMoveScore = moveScore;
			// troviamo la mossa corrispondente al nuovo punteggio calcolato da alphabeta data una certa profondità
			bestMove = getBestMove(B, true, depth, bestMoveScore);
			depth++;		// incrementiamo la profondità	
        }
		return bestMove;
	}

	private double alphabeta(CXBoard B, boolean myTurn, int depth, double alpha, double beta) {
		double eval;		// valore migliore trovato
		Integer[] availableColumns = B.getAvailableColumns();
		long boardHash = zobristHashing.calculateBoardHash(B);		// viene calcolata la chiave corrispondente all'attuale gamestate mediante Zobrist Hashing		
    	if(transpositionTable.contains(boardHash))		// viene controllata se la tabella hash contiene già la valutazione dell'attuale gamestate
            return transpositionTable.retrieve(boardHash);		// in caso positivo ritorna direttamente quel valore
		// se si raggiunge profondità 0 o se la partita non è più aperta o se scade il tempo disponibile allora viene valutato il gamestate
		if(depth <= 0 || B.gameState() != CXGameState.OPEN || (System.currentTimeMillis() - start) / 1000.0 > timeout * (buffer / 100.0)) {
			eval = evaluate(B);
		}
		// è il nostro turno quindi si cerca di massimizzare il valore
		else if(myTurn) {
			eval = Double.NEGATIVE_INFINITY;
			// si controllano le colonne disponibili
			for(int col : availableColumns) {
				// simuliamo la mossa
				B.markColumn(col);
				// aggiorniamo il valore di eval prendendo il valore massimo tra i figli
				eval = Math.max(eval, alphabeta(B, false, depth - 1, alpha, beta));
				// aggiorniamo il valore di alpha prendendo il massimo tra alpha ed eval
				alpha = Math.max(eval, alpha);
				// rimuoviamo la mossa 
				B.unmarkColumn();
				// controlliamo se è possibile effettuare potature
				if (beta <= alpha) {
					break;
				}
			}
		} 
		// è il turno dell'avversario quindi si cerca di minimizzare il valore 
		else {
			eval = Double.POSITIVE_INFINITY;
			// si controllano le colonne disponibili
			for(int col : availableColumns) {
				// simuliamo la mossa
				B.markColumn(col);
				// aggiorniamo il valore di eval prendendo il valore minimo tra i figli
				eval = Math.min(eval, alphabeta(B, true, depth - 1, alpha, beta));
				// aggiorniamo il valore di beta prendendo il minimo tra beta ed eval
				beta = Math.min(eval, beta);
				// rimuoviamo la mossa 
				B.unmarkColumn();
				// controlliamo se è possibile effettuare potature
				if (beta <= alpha) {
					break;
				}
			}
		}
		transpositionTable.store(boardHash, eval);		// prima di ritornare il valore, viene inserito nella transposition table
		return eval;
	}
	
	private int getBestMove(CXBoard B, boolean myTurn, int depth, double targetScore) {
		Integer [] availableColumns = B.getAvailableColumns();		
		int bestMove = availableColumns[rand.nextInt(availableColumns.length)]; 
		double scoreThreshold = 1;		// mettiamo una soglia in modo che la ricerca del punteggio non debba essere esattamente precisa ma rientri in un certo intervallo
		double bestMoveScore = myTurn ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;	// il punteggio migliore viene settato a + o - infinito a seconda di quale player stia giocando
		// se abbiamo esaurito il tempo disponibile ritorniamo il punteggio migliore
		if((System.currentTimeMillis() - start) / 1000.0 < timeout) {
			// si scorre tra le colonne disponibili 
			for(int col : availableColumns) {
				// simuliamo fare la mossa 
				B.markColumn(col);
				// calcoliamo il punteggio della nuova mossa con alphabeta 
				double moveScore = alphabeta(B, !myTurn, depth - 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
				// rimuoviamo la mossa 
				B.unmarkColumn();
				// controlliamo se il punteggio trovato coincide con quello che stiamo cercando
				if(Math.abs(moveScore - targetScore) <= scoreThreshold) 
					return col;
				// se il punteggio non coincide ma è migliore di quello trovato precendentemente allora si aggiorna
				if((myTurn && moveScore > bestMoveScore) || (!myTurn && moveScore < bestMoveScore)) {
					bestMoveScore = moveScore;
					bestMove = col;
				}
			} 
		}
		return bestMove;
	}

	private double evaluate(CXBoard B) {
		// se il gamestate attuale fa vincere noi, ritorniamo + infinito
		if(B.gameState() == myWin) 
			return Double.POSITIVE_INFINITY;
		// se il gamestate attuale fa vincere l'avversario, ritorniamo - infinito
		if(B.gameState() == yourWin) 
			return Double.NEGATIVE_INFINITY;
		// se il gamestate attuale fa pareggiare, ritorniamo 0
		else if(B.gameState() == CXGameState.DRAW)
			return 0.0;
		// altrimenti ritorniamo un valore a seconda della posizione della configurazione delle celle
		else{
			boolean myThreats = checkForMajorThreats(B, P1 ? true : false);			// controlliamo se possiamo vincere al prossimo turno
			if(myThreats)
				return Double.POSITIVE_INFINITY;		
			boolean yourThreats = checkForMajorThreats(B, P1 ? false : true);		// controlliamo se possiamo perdere al prossimo turno
			if(yourThreats)
				return Double.NEGATIVE_INFINITY;	
			double connections = countConnections(B, P1 ? true : false);		// controlliamo le sequenze nella tavola di gioco
			return connections;
		}
	}

	private double countConnections(CXBoard B, boolean myTurn) {
		double bestScore;
		if (myTurn) 
			bestScore = Double.NEGATIVE_INFINITY; 
		else 
			bestScore = Double.POSITIVE_INFINITY; 
		for (int col = 0; col < N; col++) {
			if (!B.fullColumn(col)) { 
				// simuliamo la mossa
				B.markColumn(col);
				double colScore = 0.0;
				// contiamo le nostre connessioni
				colScore += countRows(B, myTurn);
				colScore += countDiagonals(B, myTurn);
				colScore += countColumns(B, myTurn);
				// contiamo le connessioni dell'avversario 
				colScore -= countRows(B, !myTurn);
				colScore -= countDiagonals(B, !myTurn);
				colScore -= countColumns(B, !myTurn);
				// se il punteggio è migliore di quello trovato fino ad ora, allora lo aggiorniamo
				if((myTurn && colScore > bestScore) || (!myTurn && colScore < bestScore)) {
					bestScore = colScore;
				}
				// rimuoviamo la mossa
				B.unmarkColumn();
			}
		}
		return bestScore;
	}

	//ritorna la altezza massima fra tutte colonne di gioco
	private int highestColumn(CXBoard B) {
		int max = 0;
		int count;
		int row;
		int col = 0;
		// scorriamo fra tutte le colonne
		while(col < N) {
			count = 0;
			row = M - 1;
			// partiamo dalla riga più in basso
			while(row >= 0) {
				// se la cella non è libera, incrementiamo il contatore
				if(B.cellState(row, col) != CXCellState.FREE) {
					count++;
					row--;
				}		
				// altrimenti andiamo alla prossima colonna
				else
					break;
			}
			// aggiorniamo l'altezza massima
			if(count > max) 
				max = count;
			if(count == M)
				return M;
			col++;
		}
		return max;
	}

	// controlla le connessioni delle righe
	private double countRows(CXBoard B, boolean myTurn) {
		CXCellState myPlayer = myTurn ? CXCellState.P1 : CXCellState.P2;
		CXCellState otherPlayer = myTurn ? CXCellState.P2 : CXCellState.P1;
		double rowScore = 0.0;
		int maxHeight = M - highestColumn(B);			// prende l'altezza massima fra tutte le colonne 
		for(int row = M - 1; row >= maxHeight; row--) {
			for(int col = 0; col <= N - X; col++) {
				int myPieces = 0;
				int myPieceLastPos = -1;
				int freeSpaces = 0;
				int freeSpaceFirstPos = -1;
				int freeSpaceLastPos = -1;
				for(int i = 0; i < X; i++) {			// assumiamo X = 4 ma funziona pure con altri valori di X
					int currentCol = col + i;
					if(B.cellState(row, currentCol) == myPlayer) {
						myPieces++;
						myPieceLastPos = i;
						// scenario FREE P1 FREE P1
						if(myPieces == X - 2 && freeSpaces == 2 && freeSpaceFirstPos == 0 && freeSpaceLastPos == X - 2) {
							if(currentCol < N - 1) {
								if(B.cellState(row, currentCol + 1) == CXCellState.FREE) {
									rowScore += 10000;
								}
								else {
									rowScore += 10;
								}
								col++;
							}
							else {
								rowScore += 10;
							}
							break;
						}
						// scenario FREE FREE P1 P1 
						if(myPieces == X - 2 && freeSpaces == 2 && freeSpaceFirstPos == 0 && freeSpaceLastPos == 1) {
							if(currentCol < N - 1) {
								if(B.cellState(row, currentCol + 1) == CXCellState.FREE) {
									rowScore += 10000;
								}
								else {
									rowScore += 10;
								}
								col++;
							}
							else {
								rowScore += 10;
							}
							break;
						}
						// scenario P1 FREE FREE P1
						if(myPieces == X - 2 && freeSpaces == 2) {
							rowScore += 10;
							break;
						}
						// scenario FREE P1 P1 P1 				
						if(myPieces == X - 1 && freeSpaces == 1 && freeSpaceFirstPos == 0) {
							if(currentCol < N - 1) {
								if(B.cellState(row, currentCol + 1) == CXCellState.FREE) {
									return 100000;
								}
								else {
									rowScore += 1000;
								}
								col++;
							}
							else {
								rowScore += 1000;
							}
							break;
						}
						// scenario P1 FREE P1 P1 oppure scenario P1 P1 FREE P1
						if(myPieces == X - 1 && freeSpaces == 1) {
							rowScore += 1000;
							break;
						}
					}
					else if(B.cellState(row, currentCol) == otherPlayer) {
						break;
					}
					else if(B.cellState(row, currentCol) == CXCellState.FREE){
						if(freeSpaces == 0) {
							freeSpaceFirstPos = i;
						}
						else if(freeSpaces >= X - 2) {
							if(myPieces > 0) {
								rowScore += 1;
							}
							break;
						}
						freeSpaces++;
						freeSpaceLastPos = i;
						// scenario P1 P1 P1 FREE
						if(myPieces == X - 1) {
							if(col != 0) {
								if(B.cellState(row, currentCol - X) == CXCellState.FREE) {
									return 100000;
								}
								else {
									rowScore += 1000;
								}
							}
							else {
								rowScore += 1000;
							}
							break;
						}
						// scenario FREE P1 P1 FREE
						if(myPieces == X - 2 && freeSpaces == 2 && myPieceLastPos == X - 2 && freeSpaceFirstPos == 0) {
							if(currentCol != N - 1) {
								if(B.cellState(row, currentCol + 1) == CXCellState.FREE) {
									rowScore += 10000;
								}
								else {
									rowScore += 10;
								}
								col++;
							}
							else {
								rowScore += 10;
							}
							break;
						}
						// scenario P1 FREE P1 FREE
						if(myPieces == X - 2 && freeSpaces == 2 && myPieceLastPos == X - 2) {
							rowScore += 10;
							break;
						}
						// scenario P1 P1 FREE FREE 
						if(myPieces == X - 2 && freeSpaces == 2) {
							rowScore+= 10;
							break;
						}
					}
				}
			}
		}
		return rowScore;
	}
	
	// controlla le connessioni delle colonne
	private double countColumns(CXBoard B, boolean myTurn) {
		CXCellState Player = myTurn ? CXCellState.P1 : CXCellState.P2;
		CXCellState otherPlayer = myTurn ? CXCellState.P2 : CXCellState.P1;
		double colScore = 0.0;
		for(int col = 0; col < N; col++) {		
			int myPieces = 0;
			for(int row = M - 1; row >= X - 1; row--) {	
				if(B.cellState(row, col) == Player) {
					myPieces++;
				}
				else if(B.cellState(row, col) == otherPlayer) {
					myPieces = 0;
				}
				else {
					if(myPieces > 0) {
						colScore += Math.pow(10, myPieces-1);
					}
					break;
				}
			}
		}
		return colScore;
	}
	
	// controlla le connessioni di entrambe le diagonali
	private double countDiagonals(CXBoard B, boolean myTurn) {
		CXCellState myPlayer = myTurn ? CXCellState.P1 : CXCellState.P2;
		CXCellState otherPlayer = myTurn ? CXCellState.P2 : CXCellState.P1;
		double diagScore = 0.0;
		// da in basso a sinistra fino a in alto a destra
		for(int row = M - 1; row >= X - 1; row--) {
			for(int col = 0; col <= N - X; col++) {
				int myPieces = 0;
				int myPieceLastPos = -1;
				int freeSpaces = 0;
				int freeSpaceFirstPos = -1;
				int freeSpaceLastPos = -1;
				for(int i = 0; i < X; i++) {		// assumiamo X = 4 ma funziona pure con altri valori di X
					int currentCol = col + i;
					int currentRow = row - i;
					if(B.cellState(currentRow, currentCol) == myPlayer) {		// se la cella è del nostro giocatore incrementiamo il contatore
						myPieces++;
						myPieceLastPos = i;
						// scenario FREE P1 FREE P1
						if(myPieces == X - 2 && freeSpaces == 2 && freeSpaceFirstPos == 0 && freeSpaceLastPos == X - 2) {
							if(currentCol != N - 1 && currentRow != 0) {
								if(B.cellState(currentRow - 1, currentCol + 1) == CXCellState.FREE) {
									diagScore += 10000;
								}
								else {
									diagScore += 10;
								}
							}
							else {
								diagScore += 10;
							}
							break;
						}
						// scenario FREE FREE P1 P1 
						if(myPieces == X - 2 && freeSpaces == 2 && freeSpaceFirstPos == 0 && freeSpaceLastPos == 1) {
							if(currentCol != N - 1 && currentRow != 0) {
								if(B.cellState(currentRow - 1, currentCol + 1) == CXCellState.FREE) {
									diagScore += 10000;
								}
								else {
									diagScore += 10;
								}
							}
							else {
								diagScore += 10;
							}
							break;
						}
						// scenario P1 FREE FREE P1 
						if(myPieces == X - 2 && freeSpaces == 2) {
							diagScore += 10;
							break;
						}
						// scenario FREE P1 P1 P1 				
						if(myPieces == X - 1 && freeSpaces == 1 && freeSpaceFirstPos == 0) {
							if(currentCol != N - 1 && currentRow != 0) {
								if(B.cellState(currentRow - 1, currentCol + 1) == CXCellState.FREE) {
									return 100000;
								}
								else {
									diagScore += 1000;
								}
							}
							else {
								diagScore += 1000;
							}
							break;
						}
						// scenario P1 FREE P1 P1 oppure scenario P1 P1 FREE P1
						if(myPieces == X - 1 && freeSpaces == 1) {
							diagScore += 1000;
							break;
						}
					}
					else if(B.cellState(currentRow, currentCol) == otherPlayer){
						break;
					}
					else if(B.cellState(currentRow, currentCol) == CXCellState.FREE){
						if(freeSpaces == 0) {
							freeSpaceFirstPos = i;
						}
						else if(freeSpaces >= X - 2) {
							if(myPieces > 0) {
								diagScore += 1;
							}
							break;
						}
						freeSpaces++;
						freeSpaceLastPos = i;
						// scenario FREE P1 P1 FREE
						if(myPieces == X - 2 && freeSpaces == 2 && myPieceLastPos == X - 2 && freeSpaceFirstPos == 0) {
							if(currentCol != N - 1 && currentRow != 0) {
								if(B.cellState(currentRow - 1, currentCol + 1) == CXCellState.FREE) {
									diagScore += 10000;
								}
								else {
									diagScore += 10;
								}
							}
							else {
								diagScore += 10;
							}
							break;
						}
						// scenario P1 FREE P1 FREE
						if(myPieces == X - 2 && freeSpaces == 2 && myPieceLastPos == X - 2) {
							diagScore += 10;
							break;
						}
						// scenario P1 P1 FREE FREE 
						if(myPieces == X - 2 && freeSpaces == 2) {
							diagScore+= 10;
							break;
						}
						// scenario P1 P1 P1 FREE
						if(myPieces == X - 1) {
							diagScore += 1000;
							break;
						}
					}
				}
			}
		}
		// da in basso a destra fino a in alto a sinistra
		for(int row = M - 1; row >= X - 1; row--) {
			for(int col = N - 1; col >= X - 1; col--) {
				int myPieces = 0;
				int freeSpaces = 0;
				int myPieceLastPos = -1;
				int freeSpaceFirstPos = -1;
				int freeSpaceLastPos = -1;
				for(int i = 0; i < X; i++) {			// assumiamo X = 4 ma funziona pure con altri valori di X
					int currentCol = col - i;
					int currentRow = row - i;
					if(B.cellState(currentRow, currentCol) == myPlayer) {		// se la cella è del nostro giocatore incrementiamo il contatore
						myPieces++;
						myPieceLastPos = i;
						// scenario FREE P1 FREE P1
						if(myPieces == X - 2 && freeSpaces == 2 && freeSpaceFirstPos == 0 && freeSpaceLastPos == X - 2) {
							if(currentCol != 0 && currentRow != 0) {
								if(B.cellState(currentRow - 1, currentCol - 1) == CXCellState.FREE) {
									diagScore += 10000;
								}
								else {
									diagScore += 10;
								}
							}
							else {
								diagScore += 10;
							}
							break;
						}
						// scenario FREE FREE P1 P1 
						if(myPieces == X - 2 && freeSpaces == 2 && freeSpaceFirstPos == 0 && freeSpaceLastPos == 1) {
							if(currentCol != 0 && currentRow != 0) {
								if(B.cellState(currentRow - 1, currentCol - 1) == CXCellState.FREE) {
									diagScore += 10000;
								}
								else {
									diagScore += 10;
								}
							}
							else {
								diagScore += 10;
							}
							break;
						}
						// scenario P1 FREE FREE P1 
						if(myPieces == X - 2 && freeSpaces == 2) {
							diagScore += 10;
							break;
						}
						// scenario FREE P1 P1 P1 				
						if(myPieces == X - 1 && freeSpaces == 1 && freeSpaceFirstPos == 0) {
							if(currentCol != 0 && currentRow != 0) {
								if(B.cellState(currentRow - 1, currentCol - 1) == CXCellState.FREE) {
									return 100000;
								}
								else {
									diagScore += 1000;
								}
							}
							else {
								diagScore += 1000;
							}
							break;
						}
						// scenario P1 FREE P1 P1 oppure scenario P1 P1 FREE P1
						if(myPieces == X - 1 && freeSpaces == 1) {
							diagScore += 1000;
							break;
						}
					}
					else if(B.cellState(currentRow, currentCol) == otherPlayer){
						break;
					}
					else if(B.cellState(currentRow, currentCol) == CXCellState.FREE){
						if(freeSpaces == 0) {
							freeSpaceFirstPos = i;
						}
						else if(freeSpaces >= X - 2) {
							if(myPieces > 0) {
								diagScore += 1;
							}
							break;
						}
						freeSpaces++;
						freeSpaceLastPos = i;
						// scenario FREE P1 P1 FREE
						if(myPieces == X - 2 && freeSpaces == 2 && myPieceLastPos == X - 2 && freeSpaceFirstPos == 0) {
							if(currentCol != 0 && currentRow != 0) {
								if(B.cellState(currentRow - 1, currentCol - 1) == CXCellState.FREE) {
									diagScore += 10000;
								}
								else {
									diagScore += 10;
								}
							}
							else {
								diagScore += 10;
							}
							break;
						}
						// scenario P1 FREE P1 FREE
						if(myPieces == X - 2 && freeSpaces == 2 && myPieceLastPos == X - 2) {
							diagScore += 10;
							break;
						}
						// scenario P1 P1 FREE FREE 
						if(myPieces == X - 2 && freeSpaces == 2) {
							diagScore+= 10;
							break;
						}
						// scenario P1 P1 P1 FREE
						if(myPieces == X - 1) {
							diagScore += 1000;
							break;
						}
					}
				}
			}
		}
		return diagScore;
	}

	private boolean checkForMajorThreats(CXBoard B, boolean myTurn) {
		CXGameState win = myTurn ? CXGameState.WINP1 : CXGameState.WINP2;
		for(int col = 0; col < N; col++) {
            if(!B.fullColumn(col)) {
				// simuliamo la mossa
                B.markColumn(col); 
                if(B.gameState() == win) {		// controlliamo se è una mossa che porta alla vittoria
                    // in caso positivo rimuoviamo la mossa e ritorniamo true
					B.unmarkColumn(); 
                    return true; 
                }
				// rimuoviamo la mossa
                B.unmarkColumn(); 
            }
        }
		// se non troviamo nessuna mossa che porta alla vittoria, ritorniamo false
		return false;
	}
	
	public String playerName() {
		return "myPlayer";
	}
}
