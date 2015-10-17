import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class PendingTransfer {

  private PriorityQueue<PacketData> packetQueue;
  private TreeSet<Integer> receivedSet;
  private String outFileName;
  private BufferedOutputStream outStream;
  private int lastAcked;
  private Semaphore ackMutex;
  private Semaphore termSem;

  public PendingTransfer(String fileName) throws IOException {
    this.outFileName = fileName;
    this.packetQueue = new PriorityQueue<>();
    this.receivedSet = new TreeSet<>();
    this.outStream = new BufferedOutputStream(new FileOutputStream(new File(this.outFileName)));
    this.lastAcked = 0;
    this.ackMutex = new Semaphore(1);
    this.termSem = new Semaphore(0);
  }

  public void queue(PacketData data) throws IOException {
    // Discard duplicates
    if (this.receivedSet.contains(data.getOrder())) return;
    this.packetQueue.add(data);
    this.receivedSet.add(data.getOrder());
    // Try to write if the packet arrived matches the one that
    // we are waiting for
    try {
      //---START OF WHILE LOOP
      while (this.packetQueue.size() > 0 && this.packetQueue.peek().getOrder() == this.lastAcked + 1) {
        PacketData pktData = this.packetQueue.poll();
        if (pktData.isData()) {
          this.outStream.write(pktData.getData());
          System.out.printf("#%d wrote %d bytes.\n", pktData.getOrder(), pktData.getData().length);
        } else if (pktData.isAck()) {
          this.outStream.flush();
          this.outStream.close();
          this.termSem.release();
          System.out.println("Stream closed!");
        } else continue;
        this.lastAcked++;
      }
      //--END OF WHILE LOOP
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public String getOutputFileName() { return this.outFileName; }
  public Semaphore getTerminateSemaphore() { return this.termSem; }

}
