import java.io.*;
import java.net.*;

public class FileSender extends ThreadedRunnable {

  /**
   * Properties
   */
  private BufferedInputStream inStream;
  private InetSocketAddress target;
  private DatagramSocket socket;
  private int idNo;
  private int seqNo;
  private int lastAcked;
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
    // Initialize prerequisites
    this.seqNo = 1;
    byte[] buffer = new byte[PacketService.PACKET_SIZE - PacketService.HEADER_SIZE];
    int length;

    try {
      while ((length = this.inStream.read(buffer)) > 0) {
        DatagramPacket packet = PacketService.data(this.seqNo, this.idNo, this.target, buffer);
        PacketSender sender = new PacketSender(packet, this.socket);
        sender.start();

        while (true) {
          DatagramPacket reply = PacketService.receive(this.socket);
          PacketData data = PacketService.unwrap(reply);
          if (data.isValid() && data.isAck() && data.getID() == this.idNo && data.getOrder() == this.seqNo) {
            sender.resolve();
            break;
          }
        }

        this.seqNo++;
      }
      // Dispatch terminate signal
      DatagramPacket term = PacketService.ack(this.seqNo, this.idNo, this.target);
      PacketSender sender = new PacketSender(term, this.socket);
      sender.start();

      while (true) {
        DatagramPacket reply = PacketService.receive(this.socket);
        PacketData data = PacketService.unwrap(reply);
        if (data.isValid() && data.isAck() && data.getID() == this.idNo && data.getOrder() == this.seqNo) {
          sender.resolve();
          break;
        }
      }

      // Close stream finally
      this.inStream.close();
    } catch (IOException e) {
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
