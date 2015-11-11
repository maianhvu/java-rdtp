import java.io.*;
import java.net.*;

public class FileReceiver extends ThreadedRunnable {

    /**
     * Properties
     */
    private DatagramSocket socket;

    /**
     * Constructor
     */
    public FileReceiver(int port) throws SocketException {
        // Create listening socket
        this.socket = new DatagramSocket(port);
    }

    /**
     * ThreadedRunnable's implementation
     */
    @Override
    public void run() {
        // TODO: Code this part
    }

    /**Main executable method*/
    public static void main(String[] args) {
        // Check arguments length
        if (args.length != 1) {
            printInstructions();
            System.exit(1);
        }

        // Check arguments validity
        try {
            // Create address
            FileReceiver receiver = new FileReceiver(Integer.parseInt(args[0]));
            receiver.join();
        } catch (NumberFormatException e) {
            printInstructions();
            System.exit(1);
        } catch (IOException|SecurityException e) {
            System.out.printf("Error: Cannot create socket at port %s\n", args[0]);
            System.exit(1);
        } catch (InterruptedException e) {
            System.out.println("Error: Main thread was interrupted");
            System.exit(1);
        }
    }

    private static void printInstructions() {
        System.out.println("Usage: java FileReceiver <port>");
    }
}
