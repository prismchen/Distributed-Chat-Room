# Distributed-Chat-Room

## Introduction

This project presents the skeleton of distributed chat room. Each chatter keeps a membership list (config file) to be aware of all the existing chatters other than itself. Each chatter use multicast to send messages, and message is delivered in either casual ordering or total ordering. In case of indistinguishable communication delay between chatters, an artificial random delay is added to each channel. 

## Workflow in Each Chatter
0. No central server existed, each chatter performs as a server and a client as well.
1. Construct a new chatter object, while reading in config file
2. Create a serverSocket, listener to listen for incoming client connection
3. Create a new thread, inputHandler, to read in user's type-in
4. Create a new thread, printHandler, to check if deliverQueue is empty and deliver message from it based on the prescribed order
5. Use a while loop to listen for incoming connection request, and creat a new thread, clientHandler to handler the communication with
   each client process
6. Inside each client handler, for each message received, create a new thread, msgGetter, to process this message, including 
   delay simulation and push the message into the holdback queue, and further into deliver queue.
7. Inside inputHandler, users' type-in is firstly parsed, then it goes to multicast
8. For casual ordering, vector stamp is appended at the end of each message
9. For total ordering, a special process, sequencer, is created, and it send message with sequence number to other processes
		
## Usage

First step would be modifying the config files of each chatter, which are all identical so that each chatter knows the existance of others<br>

Config file format: 

	1st line: lower-bound-of-delay	upper-bound-of-delay (in milli sec)
	2st line: chatter-id	IP	Port Number
	3rd line: chatter-id	IP	Port Number
	4th line: chatter-id	IP	Port Number
	... 

For demo simplicity: we run 4 chatters on the same IP with different port numbers: 9001, 9002, 9003 and 9004, and for total ordering, we need to allocate port 9005 for the sequencer.

### Casual Ordering
	javac src/casualOrder/*.java -d bin
	cd bin
	java casualOrder/chatter1 9001 config_CA
	java casualOrder/chatter2 9002 config_CA
	java casualOrder/chatter3 9003 config_CA
	java casualOrder/chatter4 9004 config_CA
	
### Total Ordering
	javac src/totalOrder/*.java -d bin
	cd bin
	java totalOrder/chatter1 9001 config_TO
	java totalOrder/chatter2 9002 config_TO
	java totalOrder/chatter3 9003 config_TO
	java totalOrder/chatter4 9004 config_TO
	java totalOrder/Sequencer 9005 config_TO

### Multicast Messages
	
	msend <message>
	
