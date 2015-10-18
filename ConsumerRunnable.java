import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ConsumerRunnable extends ThreadedRunnable {

  /**
   * Properties
   */
  private final Factory factory;
  private final int id;

  /**
   * Consumer
   */
  public ConsumerRunnable(int id, final Factory factory) {
    this.id = id;
    this.factory = factory;
  }

  /**
   * ThreadedRunnable implementation
   */
  public void run() {

    // DEBUG
    System.out.printf("Consumer %d started!\n", this.id);

    try {
      //---START OF WHILE LOOP
      while (!(this.factory.bufferEmpty() && this.factory.isStopped())) {

        DatagramPacket reply = PacketService.receive(this.factory.socket);
        PacketData data = PacketService.unwrap(reply);
        if (!(data.isValid() && data.isAck())) continue;

        this.factory.notEmpty.acquire();

        if (this.factory.bufferEmpty() && this.factory.isStopped()) break;

        this.factory.bufMutex.acquire();

        // If the packet is already acked, or if the sequence number attached doesn't exist
        // in the buffer (anymore), discard the packet and move on
        if (this.factory.acked(data.getOrder()) || !this.factory.buffered(data.getOrder())) {
          this.factory.rescue(this.factory.bufMutex);
          this.factory.notEmpty.release();
          continue;
        }

        // Get the sender from the buffer
        PacketSender sender = this.factory.take(data.getOrder());

        this.factory.bufMutex.release();
        this.factory.notFull.release();

        sender.resolve();
        this.factory.ack(data.getOrder());

      }
      //--END OF WHILE LOOP

      this.factory.notEmpty.release();

      // DEBUG
      System.out.printf("Consumer %d stopped!\n", this.id);
      this.factory.printState();

    } catch (IOException|InterruptedException e) {
      e.printStackTrace();
      this.factory.rescue(this.factory.bufMutex);
    }

  }

  /**
   * Getters
   */
  public int getID() { return this.id; }

}
