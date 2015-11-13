import java.io.*;
import java.util.*;
import java.net.*;

public class FileSender extends ThreadedRunnable {

    /**
     * Properties
     */
    private PacketFactory factory;

    /**
     * Constructor
     */
    public FileSender(InetSocketAddress target, File inFile, String outFile) throws SocketException, IOException {
        // Create socket
        DatagramSocket source = new DatagramSocket();
        // Send a preflight request that retrieves the transfer ID
        int transferID = sendPreflight(outFile, source, target);
        // Instantiate the factory from the retrieved ID
        this.factory = new PacketFactory(inFile, source, target, transferID);
    }

    /**
     * Send a preflight request to the FileReceiver
     * @param outFile The path to the output file being sent via the packet
     * @param source The socket used to send the preflight packet
     * @param target Remote socket to send to
     * @return the transfer ID created by the FileReceiver
     */
    private int sendPreflight(String outFile, DatagramSocket source, InetSocketAddress target) throws SocketException {
        MetaPacket preflight = MetaPacket.createPreflight(outFile, target);
        PacketSender sender = new PacketSender(preflight, source);
        sender.start();
        // Only expect an ACK reply
        MetaPacket reply = null;
        try {
            reply = MetaPacket.expectReply(source, EnumSet.of(
                        MetaPacket.Type.ACK,
                        MetaPacket.Type.NAK));
            // Check for error
            if (reply.isNAK()) {
                System.out.println("Error: FileReceiver encountered an exception while replying to PREFLIGHT");
                System.exit(1);
            }
        } catch (IOException e) {
            System.out.println("Error: Cannot receive reply from FileReceiver");
            System.exit(1);
        }
        sender.stop();
        // Set the ID sent along with the packet as the transfer ID
        return reply.getID();
    }

    /**
     * ThreadedRunnable's implementation
     */
    @Override
    public void run() {
        // Start factory
        this.factory.start();
    }

    /**Main executable method*/
    public static void main(String[] args) {
        // Check arguments length
        if (args.length != 4) {
            printInstructions();
            System.exit(1);
        }

        // Check arguments validity
        try {
            // Create address
            InetSocketAddress target = new InetSocketAddress(
                    args[0],
                    Integer.parseInt(args[1])
                    );

            // Check input file's validity
            File inFile = new File(args[2]);
            if (!inFile.exists() || inFile.isDirectory()) {
                System.out.printf("Error: Cannot read file %s\n", args[2]);
                System.exit(1);
            }

            // Initialize sender
            FileSender sender = new FileSender(target, inFile, args[3]);
            // Start and wait for sender to finish
            sender.join();

        } catch (NumberFormatException|SocketException e) {
            System.out.printf("Error: Cannot connect to %s:%s\n", args[0], args[1]);
            System.exit(1);
        } catch (IOException e) {
            System.out.printf("Error: Cannot write to file %s\n", args[3]);
            System.exit(1);
        } catch (InterruptedException e) {
            System.out.println("Error: Main thread was interrupted");
            System.exit(1);
        }
    }

    private static void printInstructions() {
        System.out.println("Usage: java FileSender <host> <port> <inFile> <outFile>");
    }
}
