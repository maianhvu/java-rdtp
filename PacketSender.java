import java.io.*;
import java.net.*;
import java.util.*;

public class PacketSender extends TimerTask {

  public static final int TIMEOUT = 1000; // 2 millis

  private DatagramPacket packet;
  private DatagramSocket socket;
  private Timer timer;

  public PacketSender(DatagramPacket packet, DatagramSocket socket) {
    this.packet = packet;
    this.socket = socket;
    this.timer = new Timer();
  }

  public void start() {
    this.timer.scheduleAtFixedRate(this, 0, TIMEOUT);
  }

  public void run() {
    try {
      this.socket.send(this.packet);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void resolve() {
    this.timer.cancel();
  }
}
