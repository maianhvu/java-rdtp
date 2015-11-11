import java.io.IOException;
import java.util.*;
import java.net.*;

public class PacketSender extends TimerTask {

    /**
     * Constants
     */
    private static final long TIMEOUT = 1000;

    /**
     * Types
     */
    private enum State {
        NEW,
        RUNNING,
        TERMINATED;
    }

    /**
     * Properties
     */
    private MetaPacket packet;
    private DatagramSocket source;
    private Timer timer;
    private State state;

    /**
     * Constructor
     * @param packet The MetaPacket that contains all information
     *               about the DatagramPacket
     * @param source The DatagramSocket to send out this packet from
     */
    public PacketSender(MetaPacket packet, DatagramSocket source) {
        // Set properties
        this.packet = packet;
        this.source = source;
        // Initialize timer and state
        this.timer = new Timer();
        this.state = State.NEW;
    }

    // Only issue schedule if timer is pristine
    public void start() {
        if (this.state != State.NEW) return;
        this.timer.scheduleAtFixedRate(this, 0, TIMEOUT);
        this.state = State.RUNNING;
    }

    public void stop() {
        if (this.state != State.RUNNING) return;
        this.timer.cancel();
        this.state = State.TERMINATED;
    }

    @Override
    public void run() {
        try {
            this.source.send(this.packet.getPacket());
        } catch (IOException e) {
            System.out.println("Error: Packet delivery failed");
            this.stop();
            System.exit(1);
        }
    }
}
