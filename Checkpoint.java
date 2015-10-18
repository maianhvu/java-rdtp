import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Checkpoint {

  /**
   * Constants
   */
  private static final int THREADS_COUNT = 8;

  /**
   * Properties
   */
  public final DatagramSocket socket;
  public final Semaphore control;
  public final Hashtable<String, Integer> idTable;
  public final Hashtable<Integer, PendingTransfer> transfersTable;

  private int tracker;
  public Semaphore trackerMutex;

  /**
   * Constructor
   */
  public Checkpoint(DatagramSocket socket) {
    this.socket = socket;
    this.control = new Semaphore(THREADS_COUNT);
    this.tracker = tracker;

    this.idTable = new Hashtable<>();
    this.transfersTable = new Hashtable<>();

    resetTracker();
    this.trackerMutex = new Semaphore(1);
  }

  /**
   * Tracker methods
   */
  public int getTracker() { return this.tracker; }
  public void increaseTracker() { this.tracker++; }
  public void resetTracker() { this.tracker = 1; }
}
