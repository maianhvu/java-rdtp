import java.io.*;
import java.net.*;

public class ProducerRunnable extends ThreadedRunnable {

  /**
   * Constants
   */
  private static final int BYTE_BUFFER_SIZE = PacketService.PACKET_SIZE - PacketService.HEADER_SIZE;

  /**
   * Properties
   */
  private final Factory factory;
  private final int id;

  /**
   * Constructor
   */
  public ProducerRunnable(int id, final Factory factory) {
    this.factory = factory;
    this.id = id;
  }

  /**
   * Threaded runnable implementation
   */
  public void run() {
    byte[] buffer = new byte[BYTE_BUFFER_SIZE];

    try {
      //---START OF WHILE LOOP
      while (!this.factory.isStopped()) {
        this.factory.notFull.acquire();

        // Read from file first, ensure correct order
        this.factory.readMutex.acquire();
        this.factory.seqMutex.acquire();

        int length = this.factory.stream.read(buffer);
        int order = this.factory.getSequenceNumber();
        boolean isTerminating = length <= 0;

        if (isTerminating) this.factory.stop();

        this.factory.seqMutex.release();
        this.factory.readMutex.release();

        if (order > 0) {

          // Create packet and its sender
          DatagramPacket packet;
          if (!isTerminating) {
            packet = PacketService.data(order, this.factory.getID(), this.factory.target, buffer);
          } else {
            packet = PacketService.ack(order, this.factory.getID(), this.factory.target);
          }
          PacketSender sender = new PacketSender(packet, this.factory.socket);

          // Add sender to buffer
          // Enter Critical Section
          this.factory.bufMutex.acquire();
          this.factory.buffer(order, sender);
          this.factory.bufMutex.release();

          sender.start();

        }

        this.factory.notEmpty.release();
      }
      //---END OF WHILE LOOP
    } catch (InterruptedException|IOException e) {
      e.printStackTrace();
      this.factory.rescue(
          this.factory.readMutex,
          this.factory.seqMutex,
          this.factory.bufMutex
          );
    }
  }

  /**
   * Getters
   */
  public int getID() { return this.id; }
}
