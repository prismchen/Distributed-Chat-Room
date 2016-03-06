# Distributed-Chat-Room

## Introduction

This project presents a distributed chat room. Each chatter keeps a membership list (config file) to be informed of all the existing chatters other than itself. Each chatter use multicast to send messages, and message is delivered in either casual ordering or total ordering. In case of indistinguishable communication delay between chatters, an artificial random delay is added to each channel. 

## Workflow in Each Chatter
0. No central server existed, each chatter performs as a server and a client as well.
1. Construct a new tf object (tf is the main object function as each chatting individual), while reading in config file
2. Create a serverSocket, listener to listen for incoming client connection
3. Create a new thread, inputHandler, to read in user's type-in
4. Create a new thread, printHandler, to check if deliverQueue is empty and deliver message from it based on the prescribed order
5. Use a while loop to listen for incoming connection request, and creat a new thread, clientHandler to handler the communication with
   each client process
6. Inside each client handler, for each message received, create a new thread, msgGetter, to process this message, including 
   delay simulation and push the message into the holdback queue, and further into deliver queue.
7. Inside inputHandler, users type-in is firstly parsed, then it goes to either unicast_send or multicast, which calls
   several unicast_sent.
8. For casual ordering, vector stamp is appended at the end of each message
9. For total ordering, a special process, sequencer, is created, and it send message with sequence number to other processes
		
## Usage



### Casual Ordering
- cd cs425MP1/src
- javac casualOrder/*.java
- (open four terminals and run each of the following commands)
- java casualOrder/tf1 9001
- java casualOrder/tf2 9002
- java casualOrder/tf3 9003
- java casualOrder/tf4 9004
	
### Total Ordering
- cd cs425MP1/src
- javac totalOrder/*.java
- (open four terminals and run each of the following commands)
- java totalOrder/tf1 9001
- java totalOrder/tf2 9002
- java totalOrder/tf3 9003
- java totalOrder/tf4 9004
- java totalOrder/Sequencer 9005
	
