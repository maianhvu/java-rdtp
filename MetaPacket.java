import java.net.*;
import java.nio.*;
import java.util.zip.CRC32;

public class MetaPacket {

    /**
     * Constants
     */
    public static final int MAX_PACKET_SIZE = 1000;
    public static final int HEADER_SIZE = 20;
    public static final int MAX_DATA_SIZE = MAX_PACKET_SIZE - HEADER_SIZE;

    public static final int FLAGS_ID_MASK = 0x3FFFFFFF;

    public static final int CHECKSUM_LENGTH = Long.SIZE / Byte.SIZE;

    /**
     * Types
     */
    public enum Type {
        PREFLIGHT,
        DATA,
        ACK,
        NAK;
    }

    /**
     * Properties
     */
    private int size;
    private Type type;
    private int sequenceNumber;
    private int idNumber;
    private byte[] rawData;
    private InetSocketAddress address;
    private DatagramPacket packet;

    /**
     * Private constructors to be used by static generators
     */
    private MetaPacket() {}

    //-------------------------------------------------------------------------------------------------
    //
    // PACKET SENDING
    //
    //-------------------------------------------------------------------------------------------------
    /**
     * Create a MetaPacket object with the provided specifications
     */
    private static MetaPacket create(Type type, int seqNo, int idNo, byte[] data, InetSocketAddress target) throws SocketException {
        // Make an instance
        MetaPacket meta = new MetaPacket();

        // Boolean control variables
        boolean isHeaderPacket = true;
        boolean isRawDataCopied = false;

        if (type.ordinal() < 2 && data != null && data.length > 0) {
            isHeaderPacket = false;
            // Truncate data if necessary
            if (data.length > MAX_DATA_SIZE) {
                byte[] newData = new byte[MAX_PACKET_SIZE];
                System.arraycopy(data, 0, newData, HEADER_SIZE, MAX_DATA_SIZE);
                meta.rawData = newData;
                isRawDataCopied = true;
            }
        }

        // Setting packet size
        meta.size = HEADER_SIZE;
        if (!isHeaderPacket) meta.size += data.length;
        // If raw data has not been copied, create it
        if (!isRawDataCopied) {
            meta.rawData = new byte[meta.size];
        }

        // Create a byte buffer that wraps everything
        ByteBuffer buffer = ByteBuffer.wrap(meta.rawData);

        // Reserve checksum
        buffer.putLong(0);

        // Put data length
        buffer.putInt(meta.size);

        // Put sequence number
        meta.sequenceNumber = (type == Type.PREFLIGHT) ? 0 : seqNo;
        buffer.putInt(meta.sequenceNumber);

        // Put flags
        meta.idNumber = (type == Type.PREFLIGHT) ? 0 : idNo;
        int flags = (meta.idNumber & FLAGS_ID_MASK) | (type.ordinal() << 30);
        meta.type = type;
        buffer.putInt(flags);

        // Put data
        if (!isHeaderPacket && !isRawDataCopied) {
            buffer.put(data);
        }

        // Calculate checksum
        CRC32 crc = new CRC32();
        crc.reset();
        crc.update(meta.rawData, CHECKSUM_LENGTH, meta.size - CHECKSUM_LENGTH);
        buffer.rewind();
        buffer.putLong(crc.getValue());

        // Create the datagram packet associated with this MetaPacket
        meta.packet = new DatagramPacket(meta.rawData, meta.size, target);
        // Set target
        meta.address = target;

        // Return the meta packet finally
        return meta;
    }

    /**
     * Individual methods to create different kinds of MetaPackets
     */
    public static MetaPacket createPreflight(String outFile, InetSocketAddress target) throws SocketException {
        return create(Type.PREFLIGHT, 0, 0, outFile.getBytes(), target);
    }

    public static MetaPacket createData(int seqNo, int idNo, byte[] data, InetSocketAddress target) throws SocketException {
        return create(Type.DATA, seqNo, idNo, data, target);
    }

    public static MetaPacket createACK(int seqNo, int idNo, InetSocketAddress target) throws SocketException {
        return create(Type.ACK, seqNo, idNo, null, target);
    }

    public static MetaPacket createNAK(int seqNo, int idNo, InetSocketAddress target) throws SocketException {
        return create(Type.NAK, seqNo, idNo, null, target);
    }

    /**
     * ACK and NAK as a reply
     */
    public static MetaPacket createACK(MetaPacket original) throws SocketException {
        return createACK(original.sequenceNumber, original.idNumber, original.address);
    }

    public static MetaPacket createNAK(MetaPacket original) throws SocketException {
        return createNAK(original.sequenceNumber, original.idNumber, original.address);
    }

    //-------------------------------------------------------------------------------------------------
    //
    // PACKET RECEIVING
    //
    //-------------------------------------------------------------------------------------------------
    /**
     * Waits for the socket to receive a packet, and then unwrap the packet by setting the
     * correct parameters for the MetaPacket object
     * @param receiver The DatagramSocket used to receive the reply
     * @param type The specific type of packet to expect, null for wildcard
     * @return The MetaPacket object containing the information about the reply packet
     */
    public static MetaPacket expectReply(DatagramSocket receiver, Type type) throws IOException {
        // Instantiate the MetaPacket object
        MetaPacket meta = new MetaPacket();
        // Set data buffer
        meta.rawData = new byte[MAX_PACKET_SIZE];
        meta.packet = new DatagramPacket(meta.rawData, meta.rawData.length);
        // Start waiting for packet
        while (true) {
            receiver.receive(meta.packet);
            // Discard small packet
            if (meta.packet.getLength() < HEADER_SIZE) continue;
            // Prepare to unwrap
            Debugger.printArray(meta.rawData);
            break;
        }
        return null; // stub
    }

    public static MetaPacket expectReply(DatagramSocket receiver) throws IOException { return expectReply(receiver, null); }

    /**
     * Getters
     */
    public Type getType() { return this.type; }
    public int getOrder() { return this.sequenceNumber; }
    public int getID()    { return this.idNumber; }
    public byte[] getRaw() { return this.rawData; }
    public DatagramPacket getPacket() { return this.packet; }
    public InetSocketAddress getAddress() { return this.address; }

}
