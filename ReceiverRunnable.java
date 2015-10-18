import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ReceiverRunnable extends ThreadedRunnable {

  /**
   * Properties
   */
  private final DatagramPacket packet;
  private final Checkpoint checkpt;

  /**
   * Constructor
   */
  public ReceiverRunnable(final DatagramPacket packet, final Checkpoint checkpt) {
    this.packet = packet;
    this.checkpt = checkpt;
  }

  /**
   * ThreadedRunnable implementation
   */
  public void run() {
    try {
      PacketData data = PacketService.unwrap(this.packet);

      // DEBUG
      System.out.println(data);

      if (!data.isValid()) {
        this.checkpt.control.release();
        return;
      }
      // Locate the transfer that is being referred to by this packet

      if (data.isPreflight()) {
        String fileName = new String(data.getData()).trim();
        boolean discard = false;

        // Enter critical section
        this.checkpt.trackerMutex.acquire();

        int id = this.checkpt.getTracker();
        if (this.checkpt.idTable.containsKey(fileName)) {
          id = this.checkpt.idTable.get(fileName);
          discard = true;
        }
        // Send ack first
        DatagramPacket ack = PacketService.ack(0, id, this.packet.getSocketAddress());
        this.checkpt.socket.send(ack);
        // If packet is to be discarded, then skip to next iteration of the loop
        if (discard) {
          this.checkpt.control.release();
          this.checkpt.trackerMutex.release();
          return;
        }

        // Create the appropriate transfer
        PendingTransfer transfer = new PendingTransfer(fileName);
        this.checkpt.transfersTable.put(id, transfer);
        this.checkpt.idTable.put(fileName, id);
        // IMPORTANT: Increase tracker
        this.checkpt.increaseTracker();
        // Scenario when tracker overflows 30 bit. This will never happen I think
        // but, oh well, just reset it
        if (this.checkpt.getTracker() > 0x3FFFFFFF) this.checkpt.resetTracker();

        this.checkpt.trackerMutex.release();

      } else { // If packet is NOT preflight, then let the PendingTransfer object handle it
        // Send ack first
        DatagramPacket ack = PacketService.ack(data.getOrder(), data.getID(), this.packet.getSocketAddress());
        this.checkpt.socket.send(ack);

        this.checkpt.trackerMutex.acquire();
        if (!this.checkpt.transfersTable.containsKey(data.getID())) {
          this.checkpt.control.release();
          this.checkpt.trackerMutex.release();
          return;
        }
        PendingTransfer transfer = this.checkpt.transfersTable.get(data.getID());
        this.checkpt.trackerMutex.release();

        transfer.queue(data);

        if (data.isAck()) { // Terminate signal
          Semaphore term = transfer.getTerminateSemaphore();

          term.acquire();
          this.checkpt.trackerMutex.acquire();

          this.checkpt.idTable.remove(transfer.getOutputFileName());
          this.checkpt.transfersTable.remove(data.getID());

          this.checkpt.trackerMutex.release();
          term.release();
        }
      }

    } catch (IOException|InterruptedException e) {
      e.printStackTrace();
    } finally {
      this.checkpt.control.release();
    }
  }
}
