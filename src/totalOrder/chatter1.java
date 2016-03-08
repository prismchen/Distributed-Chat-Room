package totalOrder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.FileReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;

public class chatter1 {

	private static int minDelay;
	private static int maxDelay;
	static HashMap<Integer, String> IPs;
	static HashMap<Integer, Integer> Ports; 
	private static int serverPort;
	private static String serverAddr;
	private static int serverId;
	
	private static ConcurrentLinkedQueue<String> recBuf;
	private static HashMap<String, String> deliverQueue;
	private static HashMap<Integer, String> seqNums;
	private static int Si;
	

    /**
     * Constructor and print config info
     * @param serverPort
     * @param config
     * @throws Exception
     */
    public chatter1(String serverPort, String config) throws Exception {

    	recBuf = new ConcurrentLinkedQueue<String>();
    	
    	Si = 0;
    	
    	deliverQueue = new HashMap<String, String>();
    	seqNums = new HashMap<Integer, String>();
    	
    	chatter1.serverPort = Integer.parseInt(serverPort);

		chatter1.serverAddr = InetAddress.getLocalHost().getHostAddress();

    	System.out.println("Server address: "+ chatter1.serverAddr + " port: " + chatter1.serverPort);

    	chatter1.IPs = new HashMap<Integer, String>();
    	chatter1.Ports = new HashMap<Integer, Integer>();

    	try (BufferedReader br = new BufferedReader(new FileReader(config))) {

    		String currLine = null;		

    		boolean isFirst = true;

    		while ((currLine = br.readLine()) != null) {

				if (isFirst) {
					int spaceLoc = currLine.indexOf(" ");
					minDelay = Integer.parseInt(currLine.substring(0, spaceLoc));
					maxDelay = Integer.parseInt(currLine.substring(spaceLoc + 1));
					isFirst = false;
				}
				else {
					String[] tokens = currLine.split(" ", 3);
					int id = Integer.parseInt(tokens[0]);
					String ip = tokens[1];
					int port = Integer.parseInt(tokens[2]);

					IPs.put(id, ip);
					Ports.put(id, port);
					
					if ((ip.equals(chatter1.serverAddr) || ip.equals("localhost") || ip.equals("127.0.0.1")) && port == chatter1.serverPort) {
						serverId = id;
					}
				}
			}
    	} catch (IOException e) {
			e.printStackTrace();
		}

		for (int id : IPs.keySet()) {
			System.out.println("Added: " + id + " " + IPs.get(id) + " " + Ports.get(id));
		}
		System.out.println("Bounds of delay in milliSec: " + minDelay + " " + maxDelay);
    } 

    /**
     * Delay random number of milliseconds in range of minDelay and maxDelay
     * @throws InterruptedException
     */
	private static void delay() throws InterruptedException {
		TimeUnit.MILLISECONDS.sleep((long) (minDelay + (long)(Math.random() * ((maxDelay - minDelay) + 1))));
	}
    
	/**
	 * Send the String "serverId + msg" to process with id
	 * @param id
	 * @param msg
	 * @throws IOException
	 */
    private static void unicast_send(int id, String msg) throws IOException {

    	Socket socket = new Socket(IPs.get(id), Ports.get(id));

        try(PrintWriter out = new PrintWriter(socket.getOutputStream(), true)){
        	out.println(serverId + " " + msg);
        	System.out.println("Sent "+ msg +" to process "+ id +", system time is ­­­­­­­­­­­­­"+ new Timestamp(System.currentTimeMillis()).toString());
        }
        socket.close();
    }

	/**
     * Add received msg with system time into the hold-back queue
     * @param source
     * @param msg
     */
    private static void unicast_recv(String sourceIdAndMsg, ConcurrentLinkedQueue<String> sharedRecbuf) {
    	synchronized(chatter1.class) { 
    		sharedRecbuf.add(sourceIdAndMsg + " " + new Timestamp(System.currentTimeMillis()).toString());
    	}
	}
    
    /**
     * Multicast the msg to all the processes in the group, including itself
     * @param msg
     */
    private static void multicast(String msg) {
    	for(int id : IPs.keySet()) {
    		try {
    			unicast_send(id, msg);
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    }
    
    /**
     * Main method
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

		if (args.length != 2) {
			System.out.println("Proper Usage is: java program serverPort config");
        	System.exit(0);
		}	

		new chatter1(args[0], args[1]);

    	ServerSocket listener = new ServerSocket(chatter1.serverPort);

    	new inputHandler().start();
    	
    	new printHandler(recBuf).start();

    	try {
            while (true) {
                new clientHandler(listener.accept(), recBuf).start();
            }
        } finally {
            listener.close(); 
        }

    }	
    
    /**
     * Thread handles received msgs and the order of delivery
     * @author xiaochen
     *
     */
    private static class printHandler extends Thread {
    	
    	private ConcurrentLinkedQueue<String> sharedRecbuf;
    	
    	// Constructor
    	public printHandler(ConcurrentLinkedQueue<String> sharedRecbuf) {
    		this.sharedRecbuf = sharedRecbuf;
    	}
    	
    	@Override
        public void run() {
            while (true) {
            	if (!sharedRecbuf.isEmpty()) {
            		String[] sourceIdAndMsgAndTime = sharedRecbuf.poll().split(" ");
            		String sourceId = sourceIdAndMsgAndTime[0];
            		int seqNum;
            		int msgSourceId;
        			String msg = "";
            		String recvTime = "";
            		String msgBody = "";

            		if (sourceId.equals("5")) { // msg from sequencer
            			msgSourceId = Integer.parseInt(sourceIdAndMsgAndTime[1]);
            			seqNum = Integer.parseInt(sourceIdAndMsgAndTime[2]);
            			msg = sourceIdAndMsgAndTime[3];
                		seqNums.put(seqNum, msgSourceId + " " + msg);
            		}
            		else { // msg from peers
            			msg = sourceIdAndMsgAndTime[1];
            			recvTime = sourceIdAndMsgAndTime[2] + " " + sourceIdAndMsgAndTime[3];
                		msgBody = sourceId + " " + msg + " " + recvTime;
                		deliverQueue.put(sourceId + " " + msg, msgBody);
            		}
            	}
            	
            	if (!seqNums.isEmpty()) {
            		int minSeqNum = Collections.min(seqNums.keySet());
            		String msgAndSourceId = seqNums.get(minSeqNum);
            		if (minSeqNum == Si + 1 && deliverQueue.containsKey(msgAndSourceId)) {
            			String mb = deliverQueue.get(msgAndSourceId);
            			deliver(mb);
            			deliverQueue.remove(msgAndSourceId);
            			seqNums.remove(minSeqNum);
            			Si++;
            		}
            	}
            }
        }

    	/**
    	 * Deliver the msg from msg body
    	 * @param mb: msg body
    	 */
		private void deliver(String mb) {
			String[] sourceIdAndMsgAndTime = mb.split(" ", 3);
			System.out.println("Received "+ sourceIdAndMsgAndTime[1] + " from process "+ sourceIdAndMsgAndTime[0] +", system time is ­­­­­­­­­­­­­" + sourceIdAndMsgAndTime[2]);
			
		}

    }
    
    /**
     * Thread handling user input
     * @author xiaochen
     *
     */
	private static class inputHandler extends Thread {
    	
    	// Constructor
    	public inputHandler() {
    	}
    	
    	public void run() {
    		try {
    			BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
    			
    			System.out.println("Now you can type msg below: ");
    			
    			String userInput;
                while ((userInput = stdIn.readLine()) != null) {			

                    if(userInput.indexOf("send") == 0) {
                    	String[] tokens = userInput.split(" ", 3);
                    	int sendToID = Integer.parseInt(tokens[1]);
                    	String msg = tokens[2];
                    	unicast_send(sendToID, msg);
                    }
                    else if(userInput.indexOf("msend") == 0) {
                    	String msg = userInput.substring(userInput.indexOf(" ") + 1);
                    	multicast(msg);
                    }
                }
		        
    		} catch(IOException e){
    			System.out.println(e);
    		}
    	}
    	
    }

	/**
	 * Thread handling accepted client process
	 * @author xiaochen
	 *
	 */
    private static class clientHandler extends Thread {
    	BufferedReader in;
    	private Socket socket;
    	private ConcurrentLinkedQueue<String> sharedRecbuf;

    	// Constructor
    	public clientHandler(Socket socket, ConcurrentLinkedQueue<String> sharedRecbuf) {
    		this.sharedRecbuf = sharedRecbuf;
    		this.socket = socket;
    	}

    	public void run() {
    		try {

    			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            	while(true) {
            		String rawInput = in.readLine();
            		
	                if (rawInput == null) {
	                    return;
	                }
	                
	                new msgGetter(rawInput, sharedRecbuf).start();
	                
            	}
                 
    		} catch (IOException e) {
                System.out.println(e);
            } finally {
            	try {
                    socket.close();
                } catch (IOException e) {
                }
            } 		
    	}
    }
    
    /**
     * Thread for handling individual msg received under each clientHandler
     * @author xiaochen
     *
     */
    private static class msgGetter extends Thread {
    	private String msg;
    	ConcurrentLinkedQueue<String> sharedRecbuf;
    	
    	// Constructor
    	public msgGetter(String msg, ConcurrentLinkedQueue<String> sharedRecbuf) {
    		this.msg = msg;
    		this.sharedRecbuf = sharedRecbuf;
    	}
    	
    	@Override
    	public void run() {
        	try {
				delay();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        	unicast_recv(msg, sharedRecbuf);
    	}	
    }
}