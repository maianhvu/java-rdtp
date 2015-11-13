import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.zip.CRC32;

public class MetaPacket implements Comparable<MetaPacket> {

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

    private byte[] data;

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
    public static MetaPacket expectReply(DatagramSocket receiver, EnumSet<Type> types) throws IOException {
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

            // Catch BufferUnderflowException
            try {

                // Read in packet size
                ByteBuffer buffer = ByteBuffer.wrap(meta.rawData);
                long checksum = buffer.getLong();
                meta.size = buffer.getInt();
                // Skip corrupted length
                if (meta.size > MAX_PACKET_SIZE || meta.size < HEADER_SIZE) {
                    System.out.printf("Info: Packet size too small (%d)\n", meta.size);
                    continue;
                }

                // Checksum
                CRC32 crc = new CRC32();
                crc.reset();
                crc.update(meta.rawData, CHECKSUM_LENGTH, meta.size - CHECKSUM_LENGTH);
                // Checksum mismatch, skip
                if (crc.getValue() != checksum) {
                    System.out.printf("Info: Checksum mismatch\n\tExpected: %d\n\tActual: %d\n", crc.getValue(), checksum);
                    continue;
                }

                // Read in sequence number
                meta.sequenceNumber = buffer.getInt();

                // Read in flags
                int flags = buffer.getInt();
                meta.idNumber = flags & FLAGS_ID_MASK;
                int typeID = flags >>> 30;
                meta.type = Type.values()[typeID];

                // Check if type is to be expected
                if (types != null && !types.contains(meta.type)) {
                    System.out.printf("Info: Expected types %s but got %s, discard\n",
                            types.toString(), meta.type.name());
                    continue;
                }

                // Resize packet if needed: if is neither ACK or NAK
                // and if the size differs
                if (typeID <= 1 && meta.size != meta.rawData.length) {
                    byte[] newData = new byte[meta.size];
                    System.arraycopy(meta.rawData, 0, newData, 0, newData.length);
                    // Save buffer offset
                    int offset = buffer.position();
                    // Update new data and new buffer
                    meta.rawData = newData;
                    buffer = ByteBuffer.wrap(meta.rawData);
                    buffer.position(offset);
                }

                // Set address
                meta.address = (InetSocketAddress) meta.packet.getSocketAddress();

                return meta;

            } catch (BufferUnderflowException e) {
                // Buffer underflow, corrupted packet!
                e.printStackTrace();
                continue;
            }
        }
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

    public boolean isPreflight() { return this.type == MetaPacket.Type.PREFLIGHT; }
    public boolean isData() { return this.type == MetaPacket.Type.DATA; }
    public boolean isACK() { return this.type == MetaPacket.Type.ACK; }
    public boolean isNAK() { return this.type == MetaPacket.Type.NAK; }

    //-------------------------------------------------------------------------------------------------
    //
    // Special methods
    //
    //-------------------------------------------------------------------------------------------------
    /**
     * Attempt to write to the stream using the data contained in this packet
     * @param outStream The BufferedOutputStream to write to
     * @return null if the type of this packet prevents it from writing
     * @return false if this packet is an ACK which closes the stream
     * @return true if this packet is a DATA packet which keeps the stream open
     */
    public Boolean transferTo(BufferedOutputStream outStream) throws IOException {
        // Do not transfer PREFLIGHT or NAK packet
        if (this.type == Type.PREFLIGHT || this.type == Type.NAK) return null;
        // Close the stream if type is ACK
        if (this.type == Type.ACK) {
            outStream.close();
            return false;
        }
        // Write and return true if is DATA
        outStream.write(this.rawData, HEADER_SIZE, this.size - HEADER_SIZE);
        return true;
    }

    /**
     * Convert the data in this packet to a String
     */
    public String stringifyData() {
        return new String(this.rawData, HEADER_SIZE, this.size - HEADER_SIZE);
    }

    /**
     * Comparable implementation
     */
    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (this == o) return true;
        if (!(o instanceof MetaPacket)) return false;
        MetaPacket another = (MetaPacket) o;
        if (this.idNumber != another.idNumber || this.sequenceNumber != another.sequenceNumber)
            return false;
        return true;
    }

    public int compareTo(MetaPacket another) {
        return this.sequenceNumber - another.sequenceNumber;
    }

    /**
     * Debug
     */
    public String toString() {
        String data = "";
        if (this.type == Type.PREFLIGHT) {
            data = "\t\"" + this.stringifyData() + "\"";
        }
        return String.format("[ #%d\t%s\tID = %d\tSIZE = %d%s ]",
                this.sequenceNumber,
                this.type.name(),
                this.idNumber,
                this.size,
                data
                );
    }
}
