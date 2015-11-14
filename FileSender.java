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
        // Instantiate the factory from the retrieved ID
        this.factory = new PacketFactory(inFile, outFile, source, target);
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
