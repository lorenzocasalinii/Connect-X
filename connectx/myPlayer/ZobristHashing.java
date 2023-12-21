package connectx.myPlayer;

import connectx.CXBoard;
import java.util.Random;
import connectx.CXCellState;


// Class implementing Zobrist Hashing for Connect X game board states
public class ZobristHashing {

    // 3D array to store Zobrist keys for each cell and player(P1 and P2)
    private long[][][] zobristKeys;

    private int M;      // number of rows
    private int N;      // number of columns

    // Constructor to initialize ZobristHashing with the dimensions of the game board
    public ZobristHashing(int M, int N) {
        this.M = M;
        this.N = N;
        zobristKeys = new long[M][N][2];        // 2 for two players (P1 and P2)
        initializeKeys();
    }

    // Initialize the Zobrist keys with random values
    private void initializeKeys() {
        Random random = new Random();
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                zobristKeys[i][j][0] = random.nextLong();       // Zobrist key for P1
                zobristKeys[i][j][1] = random.nextLong();       // Zobrist key for P2
            }
        }
    }

    // Calculate the Zobrist hash for the given Connect X game board state
    public long calculateBoardHash(CXBoard B) {
        long hash = 0;
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                CXCellState cellState = B.cellState(i, j);
                if (cellState == CXCellState.P1) {
                    hash ^= zobristKeys[i][j][0];       // XOR with P1 key
                } else if (cellState == CXCellState.P2) {
                    hash ^= zobristKeys[i][j][1];       // XOR with P2 key
                }
            }
        }
        return hash;
    }
    
}

