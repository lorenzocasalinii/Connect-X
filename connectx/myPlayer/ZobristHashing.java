package connectx.myPlayer;

import connectx.CXBoard;
import java.util.Random;
import connectx.CXCellState;


public class ZobristHashing {

    private long[][][] zobristKeys;
    private int M; 
    private int N; 

    public ZobristHashing(int M, int N) {
        this.M = M;
        this.N = N;
        zobristKeys = new long[M][N][2];
        initializeKeys();
    }

    private void initializeKeys() {
        Random random = new Random();
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                zobristKeys[i][j][0] = random.nextLong();
                zobristKeys[i][j][1] = random.nextLong();
            }
        }
    }

    public long calculateBoardHash(CXBoard B) {
        long hash = 0;
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                CXCellState cellState = B.cellState(i, j);
                if (cellState == CXCellState.P1) {
                    hash ^= zobristKeys[i][j][0];
                } else if (cellState == CXCellState.P2) {
                    hash ^= zobristKeys[i][j][1];
                }
            }
        }
        return hash;
    }
}
