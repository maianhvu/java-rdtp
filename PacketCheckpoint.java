import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;

public class PacketCheckpoint {

    /**
     * Properties
     */
    private DatagramSocket receiver;
    private final Hashtable<String, Integer> pathTable;
    private final Hashtable<Integer, PendingTransfer> transferTable;
    private int nextTransferID;

    /**
     * Constructor
     * @param socket The DatagramSocket this checkpoint will perform upon
     */
    public PacketCheckpoint(DatagramSocket socket) {
        // Save receiver instance
        this.receiver = socket;

        // Instantiate properties

        this.pathTable = new Hashtable<>();
        this.transferTable = new Hashtable<>();

        this.nextTransferID = 1;
    }

    /**
     * Start operating
     */
    public void operate() {
        MetaPacket incoming = null;

        try {

            while (true) {
                // Expect a packet
                incoming = MetaPacket.expectReply(
                        this.receiver,
                        EnumSet.of(
                            MetaPacket.Type.PREFLIGHT,
                            MetaPacket.Type.DATA,
                            MetaPacket.Type.ACK)
                        );
                // DEBUG: Print out packet received
                System.out.println(incoming);

                // Reply with ACK first if not PREFLIGHT
                if (!incoming.isPreflight()) {

                    // Queue the packet
                    PendingTransfer transfer = transferTable.get(incoming.getID());
                    Set<Integer> resolved = transfer.queue(incoming);

                    // Send ACK to all packets
                    MetaPacket reply = MetaPacket.createACK(incoming);
                    this.receiver.send(reply.getPacket());

                } else {
                    // Locate the transfer in the path table
                    String outFile = incoming.stringifyData();
                    Integer transferID = this.pathTable.get(outFile);
                    if (transferID == null) {
                        // Create a new transfer and reply with the created transfer's ID
                        transferID = createNewTransfer(outFile);
                    }
                    MetaPacket reply = MetaPacket.createACK(0, transferID, incoming.getAddress());
                    this.receiver.send(reply.getPacket());
                }
            }

        } catch (IOException e){
            System.out.println("Error: Socket cannot retrieve or send data");
            // Attempt to send NAK to FileSender so that it can close
            if (incoming != null && incoming.isPreflight()) {
                try {
                    MetaPacket nak = MetaPacket.createNAK(incoming);
                    this.receiver.send(nak.getPacket());
                } catch (IOException e2) {
                    System.out.println("Error: Cannot send NAK back to FileSender");
                }
            }
            System.exit(1);
        }
    }

    /**
     * Create a new PendingTransfer object, store it inside pathTable and
     * transferTable
     * @param outFile The path to the output file
     * @return the ID of the newly created transfer
     */
    private synchronized int createNewTransfer(final String outFile) throws IOException {
        final int transferID = this.nextTransferID++;
        // Create the PendingTransfer instance
        PendingTransfer transfer = new PendingTransfer(outFile);
        // Add TerminationHandler for the transfer
        transfer.addTerminationHandler(new PendingTransfer.TerminationHandler() {
            public void handleTermination() {
                pathTable.remove(outFile);
                transferTable.remove(transferID);
            }
        });
        // Add to table
        this.pathTable.put(outFile, transferID);
        this.transferTable.put(transferID, transfer);
        return transferID;
    }
}
