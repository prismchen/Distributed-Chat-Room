package totalOrder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.FileReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Sequencer {

	private static int minDelay;
	private static int maxDelay;
	static HashMap<Integer, String> IPs;
	static HashMap<Integer, Integer> Ports; 
	private static int serverPort;
	private static String serverAddr;
	private static int serverId;
	
	private static ConcurrentLinkedQueue<String> recBuf;
	private static int S;
	

	/**
	 * Set up Sequencer from config file
	 * @param serverPort
	 * @param config
	 * @throws Exception
	 */
    public Sequencer(String serverPort, String config) throws Exception {

    	recBuf = new ConcurrentLinkedQueue<String>();
    	
    	S = 0;
    	
    	Sequencer.serverPort = Integer.parseInt(serverPort);

		Sequencer.serverAddr = InetAddress.getLocalHost().getHostAddress();

    	System.out.println("Server address: "+ Sequencer.serverAddr + " port: " + Sequencer.serverPort);

    	Sequencer.IPs = new HashMap<Integer, String>();
    	Sequencer.Ports = new HashMap<Integer, Integer>();

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
					
					if ((ip.equals(Sequencer.serverAddr) || ip.equals("localhost") || ip.equals("127.0.0.1")) && port == Sequencer.serverPort) {
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
	 * Send the String "server's ID and msg" to process with id 
	 * @param id
	 * @param msg
	 * @throws IOException
	 */
    private static void unicast_send(int id, String msg) throws IOException {

    	Socket socket = new Socket(IPs.get(id), Ports.get(id));

        try(PrintWriter out = new PrintWriter(socket.getOutputStream(), true)){
        	out.println(serverId + " " + msg);
        	System.out.println("Sent "+ msg +" to process "+ id +", system time is ­­­­­­­­­­­­­"+ System.currentTimeMillis());
        }
        socket.close();
    }
    

    /**
     * Receive the String "source ID with msg" and add it to hold-back queue
     * @param sourceIdAndMsg
     * @param sharedRecbuf
     */
    private static void unicast_recv(String sourceIdAndMsg, ConcurrentLinkedQueue<String> sharedRecbuf) {
    	synchronized(Sequencer.class) { 
    		sharedRecbuf.add(sourceIdAndMsg + " " + System.currentTimeMillis());
    	}
	}
    
    /**
     * multicat the msg to processes in the group except for the Sequencer
     * @param msg
     */
    private static void multicast(String msg) {
    	for(int id : IPs.keySet()) {
    		try {
    			if (id != serverId)
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

		if (args.length != 1) {
			System.out.println("Proper Usage is: java program serverPort");
        	System.exit(0);
		}	

		
    	new Sequencer(args[0], "config_TO");
    	
		ServerSocket listener = new ServerSocket(Sequencer.serverPort);

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
     * Tread for handling received msgs and re-multicast them
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
            		String[] sourceIdAndMsgAndTime = sharedRecbuf.poll().split(" ", 3);
            		String sourceId = sourceIdAndMsgAndTime[0];
            		String msg = sourceIdAndMsgAndTime[1];
            		String recvTime = sourceIdAndMsgAndTime[2];
            		String msgBody = sourceId + " " + msg + " " + recvTime;

            		deliver(msgBody);
            		S++;
            		multicast(sourceId + " " + S + " " + msg); // multicast format: source id of msg + sequence nmber + msg
            	}
            }
        }

    	/**
    	 * Deliver directly from hold-back queue
    	 * @param mb : msg body
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