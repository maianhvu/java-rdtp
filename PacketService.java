import java.net.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.CRC32;

public class PacketService {

  /**
   * Packet Architecture Reference:
   *
   *    0                            31
   *    ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
   *  0 ┃                             ┃
   *    ┃      CHECKSUM (64-bit)      ┃
   *  1 ┃                             ┃
   *    ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫
   *  2 ┃     DATA LENGTH (32-bit)    ┃
   *    ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫
   *  3 ┃       SEQ NO (32-bit)       ┃
   *    ┣━┳━┳━━━━━━━━━━━━━━━━━━━━━━━━━┫
   *  4 ┃A┃N┃     ID NO (30-bit)      ┃ (FLAGS)
   *    ┣━┻━┻━━━━━━━━━━━━━━━━━━━━━━━━━┫
   *  5 ┃           (DATA)            ┃
   *    ┆▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒┆
   *
   *                1 ROW  = 32-bit = 4 bytes
   *  HEADER_SIZE = 5 ROWS = 20 bytes
   */

  /**
   * Types
   */
  public enum PacketType {
    PREFLIGHT, ACK, NAK, DATA;
  }

  /**
   * Constants
   */
  public static final int PACKET_SIZE = 1000;
  public static final int HEADER_SIZE = 20;

  private static final int CHECKSUM_SIZE = Long.SIZE / Byte.SIZE;
  private static final int DATA_LENGTH_SIZE = Integer.SIZE / Byte.SIZE;
  private static final int SEQ_NO_SIZE = Integer.SIZE / Byte.SIZE;
  private static final int FLAGS_SIZE  = Integer.SIZE / Byte.SIZE;

  /*----------------------------------------------------------
   * PACKET CREATION
   *--------------------------------------------------------*/
  /**
   * Creates a packet containing the data supplied
   * @param type The PacketType of this packet
   * @param seqNo The sequence number to attach with the packet
   * @param idNo The identification number given by the Receiver
   * @param target The SocketAddress that this packet will be delivered to
   * @param data The data to attach along with
   * @return The DatagramPacket created with the above parameters
   */
  public static DatagramPacket create(PacketType type, int seqNo, int idNo, SocketAddress target, byte[] data) {
    // Initialize packet size
    int packetSize = HEADER_SIZE;
    boolean isMetaPacket = true; // Meta packet === ACK || NAK

    if ((type == PacketType.PREFLIGHT || type == PacketType.DATA) && data != null && data.length > 0) {
      isMetaPacket = false;
      packetSize += data.length;
      // If the total size exceeds max packet size, truncate the data
      if (packetSize > PACKET_SIZE) {
        packetSize = PACKET_SIZE;
        byte[] temp = new byte[PACKET_SIZE - HEADER_SIZE];
        System.arraycopy(data, 0, temp, 0, temp.length);
        data = temp;
      }
    }

    // Initialize the container
    byte[] packetData = new byte[packetSize];
    ByteBuffer buffer = ByteBuffer.wrap(packetData);

    // Reserve space for checksum
    buffer.putLong(0);

    // Put data length
    buffer.putInt(packetSize);

    // Put sequence number
    buffer.putInt(seqNo);

    // Calculate flags and put in
    int flags = (type == PacketType.PREFLIGHT ? 0 : (idNo & 0x3FFFFFFF));
    if (isMetaPacket) {
      if (type == PacketType.ACK)
        flags |= 0x80000000;
      else
        flags |= 0x40000000;
    }
    buffer.putInt(flags);

    // If is not MetaPacket, then put in data as well
    if (!isMetaPacket) {
      buffer.put(data, 0, data.length);
    }

    // Calculate checksum
    CRC32 crc = new CRC32();
    crc.reset();
    crc.update(packetData, CHECKSUM_SIZE, packetSize - CHECKSUM_SIZE);
    buffer.rewind();
    buffer.putLong(crc.getValue());

    // Create packet
    DatagramPacket pkt = null;
    try {
      pkt = new DatagramPacket(packetData, packetSize, target);
    } catch (SocketException e) {
      e.printStackTrace();
    }
    return pkt;
  }

  /**
   * Convenient methods to create various types of packets
   */
  public static DatagramPacket ack(int seqNo, int idNo, SocketAddress target) {
    return create(PacketType.ACK, seqNo, idNo, target, null);
  }
  public static DatagramPacket nak(int seqNo, int idNo, SocketAddress target) {
    return create(PacketType.NAK, seqNo, idNo, target, null);
  }

  public static DatagramPacket preflight(String data, SocketAddress target) {
    return create(PacketType.PREFLIGHT, 0, 0, target, data.trim().getBytes());
  }
  public static DatagramPacket data(int seqNo, int idNo, SocketAddress target, byte[] data) {
    return create(PacketType.DATA, seqNo, idNo, target, data);
  }

  /*----------------------------------------------------------
   * PACKET RECEIVING & UNWRAPPING
   *--------------------------------------------------------*/

  public static DatagramPacket receive(DatagramSocket socket) throws IOException {
    byte[] buffer = new byte[PACKET_SIZE];
    DatagramPacket packet = new DatagramPacket(buffer, PACKET_SIZE);
    socket.receive(packet);
    return packet;
  }

  /**
   * Unwraps the packet, parse the data and put into a PacketData object
   */
  public static PacketData unwrap(DatagramPacket packet) {
    // Read the data from the packet
    byte[] data = packet.getData();
    // Wraps it using a ByteBuffer for easy reading
    ByteBuffer buffer = ByteBuffer.wrap(data);

    // Get checksum
    long checksum = buffer.getLong();

    // Get packet size
    int packetSize = buffer.getInt();
    if (packetSize < HEADER_SIZE || packetSize > PACKET_SIZE) return PacketData.invalid();

    // Perform checksum!
    CRC32 crc = new CRC32();
    crc.reset();
    crc.update(data, CHECKSUM_SIZE, packetSize - CHECKSUM_SIZE);
    if (crc.getValue() != checksum) return PacketData.invalid();

    // Checksum Clear! Get the data
    int seqNo = buffer.getInt();
    int flags = buffer.getInt();
    int idNo  = flags & 0x3FFFFFFF;
    PacketType type = PacketType.DATA;

    // Prepare the byte array for the attached data
    byte[] attachedData = null;

    if (packetSize == HEADER_SIZE || (flags & 0xC0000000) > 0) {
      // Meta bits turned on, must be either ACK or NAK
      if ((flags & 0x80000000) < 0)
        type = PacketType.ACK;
      else
        type = PacketType.NAK;
    } else {
      if (idNo == 0) type = PacketType.PREFLIGHT;
      // Populate the attachedData
      attachedData = new byte[packetSize - HEADER_SIZE];
      System.arraycopy(data, HEADER_SIZE, attachedData, 0, attachedData.length);
    }

    // Create the packet data
    PacketData pktData = new PacketData(type, seqNo, idNo,
        packet.getSocketAddress(), attachedData);
    return pktData;
  }
}
