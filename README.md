#Division of work:
##Ciprian Hutanu:
Code: Requirements analysis, and main implementation of code, code cleanup, comments.
Classes: Assignment1, Vehicle, Warehouse, Robot, Dealership.
Document: Functional Specification, Class design, concurrency, shared data management, design patterns.

##Jordan Conway-McLaughlin:
Code: Code Refactoring/cleanup: bug fixes, reducing code duplication, fixing concurrency issues.
Classes: Station, ThreadCounter, Logger, ThreadRunner, 

See `git log` for more information or `git blame`

#How to compile and run the code on Linux/Ubuntu:
Once inside the extracted directory:
`make`
Or consult the Makefile for commands used to interact with the project

#Tasks/Dependencies in the program:
There aren’t any dependencies outside standard Java libraries. Using the latest version of the Java runtime should work fine.

#Design Patterns used to manage concurrency:
The patterns used to manage concurrency are mainly the use of built in monitors in java to control concurrent access to data. I also made use of Atomic data types like AtomicInteger and AtomicBoolean as suggested in the lecture notes. Atomic types work well for concurrent code, ensuring that values are synchronised across all threads that may need access to them. I made sure to use a synchronised block around shared data. Another important aspect is the careful management of threads, using things like ExecutorService for thread management. Finally, before even writing any code, I carefully read through the assignment specification, and wrote out in detail what the program is expected to do, and what different components might be needed to complete the program.

#How our solution addresses issues like fairness, prevention of starvation etc.:
The issues of fairness, and prevention of things like deadlocks, livelocks, and starvation, are things I considered from the planning stages before I wrote any code. The way the data is structured means that any shared resources are locked for as little time as possible. The priority was to reduce the amount of communication to the essentials only. Each robot on the production line has its own queue. At most, a queue is locked for 1-2 lines of code taking milliseconds to execute, so the ratio of inter-thread communication, and independent thread work means little time is wasted waiting for shared resources.
