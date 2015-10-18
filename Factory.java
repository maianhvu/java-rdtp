import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Factory {

  /**
   * Constants
   */
  private static final int THREADS_COUNT = 8;

  /**
   * Properties
   */
  public final Semaphore notFull;
  public final Semaphore notEmpty;
  public final Semaphore bufMutex;
  public final Semaphore readMutex;
  public final Semaphore seqMutex;

  private final TreeMap<Integer, PacketSender> buffer;
  private final TreeSet<Integer> ackedSet;

  public final BufferedInputStream stream;
  public final DatagramSocket socket;
  public final SocketAddress target;

  private int seqNo;
  private final int idNo;

  private boolean stopped;

  /**
   * Constructor
   */
  public Factory(final int idNo, final BufferedInputStream stream, final DatagramSocket socket, final SocketAddress target) {
    this.notFull = new Semaphore(THREADS_COUNT);
    this.notEmpty = new Semaphore(0);

    this.bufMutex = new Semaphore(1);
    this.readMutex = new Semaphore(1);
    this.seqMutex = new Semaphore(1);

    this.buffer = new TreeMap<>();
    this.ackedSet = new TreeSet<>();

    this.seqNo = 1;

    this.idNo = idNo;
    this.stream = stream;
    this.socket = socket;
    this.target = target;

    this.stopped = false;
  }

  /**
   * Getters and setters
   */
  public int getSequenceNumber() {
    if (this.stopped) return -1;
    return this.seqNo++;
  }
  public int getID() { return this.idNo; }

  public void stop() { this.stopped = true; }
  public boolean isStopped() { return this.stopped; }

  /**
   * Buffer methods
   */
  public boolean buffered(Integer seqNo) { return this.buffer.containsKey(seqNo); }
  public boolean acked(Integer seqNo) { return this.ackedSet.contains(seqNo); }

  public void buffer(int order, PacketSender sender) {
    this.buffer.put(order, sender);
  }

  public void ack(int order) {
    this.ackedSet.add(order);
  }

  public boolean bufferEmpty() { return this.buffer.size() == 0; }

  public PacketSender take(int order) {
    PacketSender sender = this.buffer.get(order);
    this.buffer.remove(order);
    return sender;
  }

  public void rescue(Semaphore... mutexes) {
    for (Semaphore mutex : mutexes) {
      if (mutex.availablePermits() == 0) mutex.release();
    }
  }

  public void printState() {
    System.out.println("Factory state:");
    System.out.printf("\tnotFull: %s\n",   notFull.toString());
    System.out.printf("\tnotEmpty: %s\n",  notEmpty.toString());
    System.out.printf("\tbufMutex: %s\n",  bufMutex.toString());
    System.out.printf("\treadMutex: %s\n", readMutex.toString());
    System.out.printf("\tseqMutex: %s\n",  seqMutex.toString());
  }

}
