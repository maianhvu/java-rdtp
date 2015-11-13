import java.io.IOException;
import java.net.*;

public class FileReceiver extends ThreadedRunnable {

    /**
     * Properties
     */
    private PacketCheckpoint checkpt;

    /**
     * Constructor
     */
    public FileReceiver(int port) throws SocketException {
        // Instantiate checkpoint
        this.checkpt = new PacketCheckpoint(new DatagramSocket(port));
    }

    /**
     * ThreadedRunnable's implementation
     */
    @Override
    public void run() {
        this.checkpt.operate();
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
