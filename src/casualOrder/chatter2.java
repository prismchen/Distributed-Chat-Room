package casualOrder;

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

public class chatter2 {

	private static int minDelay;
	private static int maxDelay;
	static HashMap<Integer, String> IPs;
	static HashMap<Integer, Integer> Ports; 
	private static int serverPort;
	private static String serverAddr;
	private static int serverId;
	
	private static ConcurrentLinkedQueue<String> recBuf;
	private static int[] vecStamp;
	private static HashMap<String, String> deliverQueue;
	

    /**
     * Constructor and print config info
     * @param serverPort
     * @param config
     * @throws Exception
     */
    public chatter2(String serverPort, String config) throws Exception {

    	recBuf = new ConcurrentLinkedQueue<String>();
    	
    	vecStamp = new int[]{0,0,0,0};
    	
    	deliverQueue = new HashMap<String, String>();
    	
    	chatter2.serverPort = Integer.parseInt(serverPort);

		chatter2.serverAddr = InetAddress.getLocalHost().getHostAddress();

    	System.out.println("Server address: "+ chatter2.serverAddr + " port: " + chatter2.serverPort);

    	chatter2.IPs = new HashMap<Integer, String>();
    	chatter2.Ports = new HashMap<Integer, Integer>();

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
					
					if ((ip.equals(chatter2.serverAddr) || ip.equals("localhost") || ip.equals("127.0.0.1")) && port == chatter2.serverPort) {
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
	 * Send the String "serverId + msg + vecString" to process with id
	 * @param id
	 * @param msg
	 * @throws IOException
	 */
    private static void unicast_send(int id, String msg) throws IOException {

    	Socket socket = new Socket(IPs.get(id), Ports.get(id));

        try(PrintWriter out = new PrintWriter(socket.getOutputStream(), true)){
        	String vecString = intArrToString(vecStamp);
        	out.println(serverId + " " + msg + " " + vecString);
        	System.out.println("Sent "+ msg +" to process "+ id +", system time is ­­­­­­­­­­­­­"+ System.currentTimeMillis());
        }
        socket.close();
    }
    
    /**
     * Convert int array to String
     * @param vecS
     * @return
     */
    private static String intArrToString(int[] vecS) {
		StringBuilder sb = new StringBuilder();
		for(int i=0; i<vecS.length; i++) {
			sb.append(vecS[i]);
			sb.append('|');
		}
		return sb.toString();
	}

	/**
     * Add received msg with system time into the hold-back queue
     * @param source
     * @param msg
     */
    private static void unicast_recv(String sourceIdAndMsgAndVecString, ConcurrentLinkedQueue<String> sharedRecbuf) {
    	synchronized(chatter2.class) { 
    		sharedRecbuf.add(sourceIdAndMsgAndVecString + " " + System.currentTimeMillis());
    	}
	}
    
    /**
     * Multicast msg to all the processes in the group except itself
     * @param msg
     */
    private static void multicast(String msg) {
    	vecStamp[serverId - 1]++;
    	System.out.println("Current vecStamp: " + intArrToString(vecStamp));
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

		if (args.length != 2) {
			System.out.println("Proper Usage is: java program serverPort config");
        	System.exit(0);
		}	

		new chatter2(args[0], args[1]);

    	ServerSocket listener = new ServerSocket(chatter2.serverPort);

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
            		String[] sourceIdAndMsgAndVecStringAndTime = sharedRecbuf.poll().split(" ", 4);
            		String sourceId = sourceIdAndMsgAndVecStringAndTime[0];
            		String msg = sourceIdAndMsgAndVecStringAndTime[1];
            		String vecS = sourceIdAndMsgAndVecStringAndTime[2];
            		String recvTime = sourceIdAndMsgAndVecStringAndTime[3];
            		String sourceIdAndmsgAndTime = sourceId + " " + msg + " " + recvTime;

            		deliverQueue.put(vecS, sourceIdAndmsgAndTime);
            		
            		List<String> toBeRemoved = new ArrayList<>();
            		for (String vs : deliverQueue.keySet()) {
            			if (isDeliverable(vs, Integer.parseInt(deliverQueue.get(vs).split(" ")[0]))) {
            				toBeRemoved.add(vs);
            				deliver(vs);
            			}
            		}                                                                                                                                                 
            		for (String delivered : toBeRemoved) {
            			deliverQueue.remove(delivered);
            		}
            		// multicast("hello");
            	}
            }
        }

    	/**
    	 * Deliver the msg form deliverQueue based on vector timestamp
    	 * @param vs: vector timestamp
    	 */
		private void deliver(String vs) {
			String[] sourceIdAndmsgAndTime = deliverQueue.get(vs).split(" ", 3);
			vecStamp[Integer.parseInt(sourceIdAndmsgAndTime[0]) - 1]++;
			System.out.println(intArrToString(vecStamp) + " Received "+ sourceIdAndmsgAndTime[1] + " from process "+ sourceIdAndmsgAndTime[0] +", system time is ­­­­­­­­­­­­­" + sourceIdAndmsgAndTime[2]);
			
		}

		/**
		 * Judge if a msg with vector timestamp vecS is deliverable or not
		 * @param vecS
		 * @param sourceId
		 * @return
		 */
		public boolean isDeliverable(String vecS, int sourceId) {
			List<Integer> vs = stringToIntArr(vecS);
			for (int i=0; i<vs.size(); i++) {
				if (i == sourceId - 1) {
					if (vecStamp[i] + 1 != vs.get(i)) {
						return false;
					}
				}
				else { 
					if (vecStamp[i] < vs.get(i)) {
						return false;
					}
				}
			}
			return true;
		}

		/** 
		 * Convert a String into List<Integer>
		 * @param str
		 * @return
		 */
		private List<Integer> stringToIntArr(String str) {
			List<Integer> res = new ArrayList<Integer>();
			int i = 0;
			for (int j=0; j<str.length(); j++) {
				if (str.charAt(j) == '|') {
					res.add(Integer.parseInt(str.substring(i, j))); 
					i = j + 1;
				}
			}
			return res;
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

    	public clientHandler(Socket socket, ConcurrentLinkedQueue<String> sharedRecbuf) {
    		this.sharedRecbuf = sharedRecbuf;
    		this.socket = socket;
    	}

    	public void run() {
    		try {

    			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            	while(true) {
            		String input = in.readLine();
            		
	                if (input == null) {
	                    return;
	                }
	                
	                new msgGetter(input, sharedRecbuf).start();
	                
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