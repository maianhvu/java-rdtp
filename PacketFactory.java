import java.io.*;
import java.util.*;
import java.net.*;

public class PacketFactory {

    /**
     * Properties
     */
    private int idNumber;
    private File inFile;
    private DatagramSocket source;
    private InetSocketAddress target;

    /**
     * Constructor
     * @param inFile The file to read data from
     * @param idNo The ID retrieved from FileReceiver
     */
    public PacketFactory(File inFile, DatagramSocket source, InetSocketAddress target, int idNo) {
        // Set properties
        this.inFile = inFile;
        this.source = source;
        this.target = target;
        this.idNumber = idNo;
    }

    /**
     * Start slicing the file
     */
    public void start() {
        try {

            // Open the file for reading
            BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(this.inFile));

            // Prepare data buffer
            byte[] buffer = new byte[MetaPacket.MAX_DATA_SIZE];
            int length;
            int sequenceNumber = 1;

            // Read data from file
            while ((length = inStream.read(buffer)) > 0) {
                // Produce the DATA MetaPacket
                MetaPacket data = null;
                // If the length read in corresponds to the default buffer length,
                // create the packet right away
                if (length == buffer.length) {
                    data = MetaPacket.createData(sequenceNumber, this.idNumber, buffer, this.target);
                } else {
                    // Else, truncate the data to its appropriate size
                    byte[] truncated = new byte[length];
                    System.arraycopy(buffer, 0, truncated, 0, length);
                    data = MetaPacket.createData(sequenceNumber, this.idNumber, truncated, this.target);
                }

                // Start sending
                PacketSender sender = new PacketSender(data, this.source);
                sender.start();

                // Wait for reply
                while (true) {
                    MetaPacket reply = MetaPacket.expectReply(this.source, EnumSet.of(MetaPacket.Type.ACK));
                    // Resolve
                    if (reply.getID() == this.idNumber && reply.getOrder() == sequenceNumber) break;
                }

                sender.stop();
                sequenceNumber++;
            }

            // TODO: Send ACK to close stream
            MetaPacket term = MetaPacket.createACK(sequenceNumber, this.idNumber, this.target);
            PacketSender sender = new PacketSender(term, this.source);
            sender.start();
            while (true) {
                MetaPacket reply = MetaPacket.expectReply(this.source, EnumSet.of(MetaPacket.Type.ACK));
                if (reply.getID() == this.idNumber && reply.getOrder() == sequenceNumber) break;
            }
            sender.stop();

            // Close the stream
            inStream.close();

        } catch (IOException e) {
            System.out.printf("Error: Cannot read data from file \"%s\"\n", this.inFile.getName());
            System.exit(1);
        }
    }
}
