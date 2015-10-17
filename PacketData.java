import java.net.*;

public class PacketData implements Comparable<PacketData> {

  /**
   * Properties
   */
  private PacketService.PacketType type;
  private int seqNo;
  private int idNo;
  private byte[] data;
  private SocketAddress source;

  private boolean valid;

  /**
   * Constructor
   */
  public PacketData(PacketService.PacketType type, int seqNo, int idNo, SocketAddress source, byte[] data) {
    this.type = type;
    this.seqNo = seqNo;
    this.idNo = idNo;
    this.source = source;
    this.data = data;
    this.valid = true;
  }

  /**
   * Alternative constructor to signify an invalid data (mostly due to failed checksum
   */
  private PacketData(boolean valid) { this.valid = valid; }
  public static PacketData invalid() { return new PacketData(false); }

  /**
   * Packet identification convenient methods
   */
  public boolean isAck() { return this.type == PacketService.PacketType.ACK; }
  public boolean isNak() { return this.type == PacketService.PacketType.NAK; }
  public boolean isPreflight() { return this.type == PacketService.PacketType.PREFLIGHT; }
  public boolean isData() { return this.type == PacketService.PacketType.DATA; }

  /**
   * Getters for properties
   */
  public PacketService.PacketType getType() { return this.type; }
  public int getOrder() { return this.seqNo; }
  public int getID() { return this.idNo; }
  public byte[] getData() { return this.data; }
  public SocketAddress getOrigin() { return this.source; }

  public boolean isValid() { return this.valid; }

  /**
   * Comparable implementation
   */
  @Override
  public int compareTo(PacketData another) {
    return this.seqNo - another.seqNo;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) return false;
    if (this == o) return true;
    if (!(o instanceof PacketData)) return false;
    PacketData another = (PacketData) o;
    return this.idNo == another.idNo && this.seqNo == another.seqNo;
  }

  /**
   * FOR DEBUGGING
   */
  @Override
  public String toString() {
    if (!this.isValid()) return String.format("[ INVALID PACKET\tFROM: %s ]", this.source);
    return String.format("[ #%d\t%s\tID: %d\tDATA: %s\tFROM: %s ]",
        this.seqNo,
        this.type.name(),
        this.idNo,
        this.data == null ? "EMPTY" : Integer.toString(this.data.length) + " bytes",
        this.source);
  }
}
