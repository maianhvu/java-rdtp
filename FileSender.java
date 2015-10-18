import java.io.*;
import java.net.*;
import java.util.*;

public class FileSender extends ThreadedRunnable {

  public static final int THREADS_COUNT = 8;

  /**
   * Properties
   */
  private BufferedInputStream inStream;
  private InetSocketAddress target;
  private DatagramSocket socket;
  private int idNo;
  private int seqNo;
  private boolean valid;

  /**
   * Constructor
   */
  public FileSender(String host, int port, String inFile, String outFile) {

    try {
      // Get the remote address
      this.target = new InetSocketAddress(host, port);
      this.socket = new DatagramSocket();
      // Open stream
      this.inStream = new BufferedInputStream(new FileInputStream(new File(inFile)));
      // Start preflight request
      DatagramPacket preflight = PacketService.preflight(outFile, this.target);
      PacketSender sender = new PacketSender(preflight, this.socket);
      sender.start();

      // Wait for ACK
      while (true) {
        DatagramPacket reply = PacketService.receive(this.socket);
        PacketData data = PacketService.unwrap(reply);
        if (data.isValid() && data.isAck()) {
          if (this.valid = (data.getID() != 0)) {
            this.idNo = data.getID();
            sender.resolve();
          }
          break;
        }
      }

    } catch (IOException e) {
      e.printStackTrace();
      this.valid = false;
    }
  }

  /**
   * ThreadedRunnable implementation
   */
  public void run() {
    if (!this.valid) return;

    // Prepare factory
    final Factory factory = new Factory(this.idNo, this.inStream, this.socket, this.target);
    ArrayList<Thread> runners = new ArrayList<>();

    // Start factory
    for (int i = 0; i < THREADS_COUNT; i++) {
      // Producers
      ProducerRunnable producer = new ProducerRunnable(i+1, factory);
      runners.add(producer.getThread());
      producer.start();

      // Consumers
      ConsumerRunnable consumer = new ConsumerRunnable(i+1, factory);
      runners.add(consumer.getThread());
      consumer.start();
    }

    // Wait for all runners to finish
    // and then close stream
    try {
      for (Thread runner : runners) runner.join();
      this.inStream.close();
    } catch (IOException|InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Main executable method
   */
  public static void main(String[] args) {
    // Check args length
    if (args.length != 4) {
      printInstructions();
      return;
    }
    // Parse port
    int port;
    try {
      port = Integer.parseInt(args[1]);
    } catch (NumberFormatException e) {
      printInstructions();
      return;
    }

    // Get the rest of the arguments
    String host = args[0];
    String inFile = args[2];
    String outFile = args[3];

    // Start Sender
    try {
      final FileSender sender = new FileSender(host, port, inFile, outFile);
      final Thread senderThread = sender.getThread();

      senderThread.start();
      senderThread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Convenient method to print out the instructions required to execute this
   * program
   */
  private static void printInstructions() {
    System.out.println("Usage: java FileSender <host> <port> <infile> <outfile>");
  }
}
