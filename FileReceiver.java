import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class FileReceiver extends ThreadedRunnable {

  /**
   * Properties
   */
  private DatagramSocket socket;
  private Hashtable<String, Integer> idMap;
  private Hashtable<Integer, PendingTransfer> transfersMap;
  private int tracker;

  /**
   * Constructor
   */
  public FileReceiver(int port) {
    try {
      this.socket = new DatagramSocket(port);
    } catch (IOException e) {
      e.printStackTrace();
    }
    this.idMap = new Hashtable<>();
    this.transfersMap = new Hashtable<>();
    this.tracker = 1;
  }

  /**
   * ThreadedRunnable implementation
   */
  public void run() {
    try {
      //---START OF WHILE LOOP
      while (true) {
        byte[] buffer = new byte[PacketService.PACKET_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        // Wait for packet to arrive
        this.socket.receive(packet);
        // Preprocess package to avoid spam
        PacketData data = PacketService.unwrap(packet);
        System.out.println(data);
        if (!data.isValid()) { continue; }
        // Locate the transfer that is being referred to by this packet

        if (data.isPreflight()) {
          String fileName = new String(data.getData()).trim();
          boolean discard = false;
          int id = tracker;
          if (this.idMap.containsKey(fileName)) {
            id = this.idMap.get(fileName);
            discard = true;
          }
          // Send ack first
          DatagramPacket ack = PacketService.ack(0, id, packet.getSocketAddress());
          this.socket.send(ack);
          // If packet is to be discarded, then skip to next iteration of the loop
          if (discard) continue;

          // Create the appropriate transfer
          PendingTransfer transfer = new PendingTransfer(fileName);
          this.transfersMap.put(id, transfer);
          this.idMap.put(fileName, id);
          // IMPORTANT: Increase tracker
          this.tracker++;
          // Scenario when tracker overflows 30 bit. This will never happen I think
          // but, oh well, just reset it
          if (this.tracker > 0x3FFFFFFF) this.tracker = 1;

        } else { // If packet is NOT preflight, then let the PendingTransfer object handle it
          // Send ack first
          DatagramPacket ack = PacketService.ack(data.getOrder(), data.getID(), packet.getSocketAddress());
          this.socket.send(ack);

          if (!this.transfersMap.containsKey(data.getID())) continue;
          PendingTransfer transfer = this.transfersMap.get(data.getID());
          transfer.queue(data);

          if (data.isAck()) { // Terminate signal
            Semaphore term = transfer.getTerminateSemaphore();
            term.acquire();
            this.idMap.remove(transfer.getOutputFileName());
            this.transfersMap.remove(data.getID());
            term.release();
          }
        }
      }
      //---END OF WHILE LOOP---
    } catch (InterruptedException|IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Main executable method
   */
  public static void main(String[] args) {
    // Check args length
    if (args.length != 1) { printInstructions(); return; }
    int port;
    try {
      port = Integer.parseInt(args[0]);
    } catch (NumberFormatException e) {
      printInstructions();
      return;
    }

    // Start Receiver
    try {
      final FileReceiver receiver = new FileReceiver(port);
      final Thread receiverThread = receiver.getThread();

      receiverThread.start();
      receiverThread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Convenient method to print out the instructions required to execute this
   * program
   */
  private static void printInstructions() {
    System.out.println("Usage: java FileReceiver <port>");
  }
}
