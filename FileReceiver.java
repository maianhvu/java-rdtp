import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class FileReceiver extends ThreadedRunnable {

  /**
   * Properties
   */
  private DatagramSocket socket;

  /**
   * Constructor
   */
  public FileReceiver(int port) {
    try {
      this.socket = new DatagramSocket(port);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * ThreadedRunnable implementation
   */
  public void run() {
    // Create Checkpoint
    final Checkpoint checkpt = new Checkpoint(this.socket);

    try {
      //---START OF WHILE LOOP
      while (true) {
        byte[] buffer = new byte[PacketService.PACKET_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        // Use semaphore to control the number of threads created
        checkpt.control.acquire();
        // Wait for packet to arrive
        this.socket.receive(packet);

        // Spawn a thread to handle packet
        ReceiverRunnable receiver = new ReceiverRunnable(packet, checkpt);
        receiver.start();
      }
      //---END OF WHILE LOOP---
    } catch (IOException|InterruptedException e) {
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
