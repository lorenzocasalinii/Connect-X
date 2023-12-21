# Connect-X Solver
Algorithms and Data Structures project for uni.

![Screenshot](connectX.png)


### Note:
*All next commands must be executed in the connectx/ directory.*

*Replace either L0 or L1 with myPlayer and replace M, N, X with values.*

- N = number of rows
- M = number of columns
- X = number of pieces to connect in order to win


## To compile:  

	javac -cp ".." *.java */*.java

## CXGame application:

- Human vs Computer:  
	
		java -cp ".." connectx.CXGame N M X connectx.L0.L0

- Computer vs Computer:

		java -cp ".." connectx.CXGame N M X connectx.L0.L0 connectx.L1.L1


## CXPlayerTester application:

- Output score only:

	    java -cp ".." connectx.CXPlayerTester N M X connectx.L0.L0 connectx.L1.L1


- Verbose output:

	    java -cp ".." connectx.CXPlayerTester N M X connectx.L0.L0 connectx.L1.L1 -v


- Verbose output and customized timeout (time) and number of game repetitions (rounds):

	    java -cp ".." connectx.CXPlayerTester N M X connectx.L0.L0 connectx.L1.L1 -v -t time -r rounds
