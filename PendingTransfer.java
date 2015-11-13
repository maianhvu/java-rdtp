import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class PendingTransfer {

    /**
     * Constants
     */
    private static final int BUFFER_SIZE = 128;

    /**
     * Properties
     */
    private BufferedOutputStream outStream;
    private ArrayList<TerminationHandler> handlers;
    private PriorityQueue<MetaPacket> packetQueue;

    private int lastACKedSeq;

    /**
     * Constructor
     * @param outFile The path to the output file
     * @param transferID The ID assigned to this transfer object
     */
    public PendingTransfer(String outFile) throws IOException {
        // Instantiate properties
        this.outStream = new BufferedOutputStream(new FileOutputStream(outFile));
        this.handlers = new ArrayList<>();
        this.packetQueue = new PriorityQueue<>();
        this.lastACKedSeq = 0;
    }

    /**
     * Queue the MetaPacket to be resolved
     * @param packet The MetaPacket to be added to the queue
     * @return a Collection of the sequence number of the resolved packets
     */
    public synchronized Set<Integer> queue(MetaPacket packet) {
        CopyOnWriteArraySet<Integer> resolved = new CopyOnWriteArraySet<>();
        // Discard packets before last ACKed or outside buffer
        if (packet.getOrder() <= this.lastACKedSeq || packet.getOrder() >= this.lastACKedSeq + BUFFER_SIZE)
            return resolved;
        // Discard packets already in the queue
        if (this.packetQueue.contains(packet)) return resolved;
        // Add packet to queue
        this.packetQueue.add(packet);
        // Begin dequeuing
        while (!this.packetQueue.isEmpty() && this.packetQueue.peek().getOrder() == this.lastACKedSeq + 1) {
            MetaPacket topPacket = this.packetQueue.poll();
            try {
                // Write data to file if is DATA packet
                if (topPacket.isData()) {
                    topPacket.transferTo(this.outStream);
                } else if (topPacket.isACK()) {
                    this.outStream.close();
                    for (TerminationHandler handler : this.handlers)
                        handler.handleTermination();
                }
            } catch (IOException e) {
                System.out.println("Error: Cannot write to output file");
                System.exit(1);
            }
            // Add ACKed sequence to the set
            resolved.add(this.lastACKedSeq++);
        }
        return resolved;
    }

    /**
     * Add a handler that executes whenever this transfer terminates
     */
    public void addTerminationHandler(TerminationHandler handler) {
        this.handlers.add(handler);
    }

    public interface TerminationHandler {
        public void handleTermination();
    }
}
