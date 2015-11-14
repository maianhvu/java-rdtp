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
    public static final int HEADER_SIZE = 16;
    public static final int MAX_DATA_SIZE = MAX_PACKET_SIZE - HEADER_SIZE;
    private static final int CHECKSUM_SIZE = Long.SIZE / Byte.SIZE;
    private static final int OUTFILE_LENGTH_SIZE = Short.SIZE / Byte.SIZE;

    public static final int SEQ_MASK = 0x3FFFFFFF;

    /**
     * Types
     */
    public enum Type {
        ACK,
        NAK,
        DATA;
    }

    /**
     * Properties
     */
    private int totalSize;
    private Type type;
    private int sequenceNumber;
    private String outFile;
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
    private static MetaPacket create(Type type, int seqNo, String outFile, byte[] data, InetSocketAddress target) throws SocketException {
        // Make an instance
        MetaPacket meta = new MetaPacket();

        // Calculate total packet size, by first taking the metadata size
        meta.totalSize = HEADER_SIZE + OUTFILE_LENGTH_SIZE + outFile.length();
        // Pre-calculate max allowed data size to help determined if the data
        // should be resized
        int maxAllowedDataSize = MAX_PACKET_SIZE - meta.totalSize;

        // Add data if needed
        if (data != null && data.length > 0 && type == Type.DATA) {
            meta.totalSize += data.length;
            if (meta.totalSize > MAX_PACKET_SIZE)
                meta.totalSize = MAX_PACKET_SIZE;
        }

        // Create raw data array and the ByteBuffer that wraps it
        meta.rawData = new byte[meta.size];
        ByteBuffer buffer = ByteBuffer.wrap(rawData);

        // Reserver checksum
        buffer.putLong(0);

        // Put total packet size
        buffer.putInt(meta.totalSize);

        // Put type and sequence number
        int typeAndSequence = (type.ordinal() << 30) | (seqNo & SEQ_MASK);
        buffer.putInt(typeAndSequence);

        // Put output file name length and its content
        buffer.putShort((short) outFile.length);
        buffer.put(outFile.getBytes());

        // Put data
        buffer.put(data, 0, Math.min(maxAllowedDataSize, data.length));

        // Calculate checksum
        CRC32 crc = new CRC32();
        crc.reset();
        crc.update(meta.rawData, CHECKSUM_SIZE, meta.totalSize - CHECKSUM_SIZE);
        // Rewind and put checksum
        buffer.rewind();
        buffer.putLong(crc.getValue());

        // Set packet
        meta.packet = new DatagramPacket(meta.rawData, meta.totalSize, target);

        // Set other misc data
        meta.address = target;
        meta.outFile = outFile;

        return meta;
    }

    /**
     * Return the maximum data buffer size for a packet sending to this output file
     * @param outFile The path of the outFile that is being written to
     * @return the max size in bytes of the data
     */
    public static int maxDataSize(String outFile) {
        return MAX_DATA_SIZE - Short.SIZE / Byte.SIZE - outFile.length;
    }

    /**
     * Individual methods to create different kinds of MetaPackets
     */
    public static MetaPacket createData(int seqNo, String outFile, byte[] data, InetSocketAddress target) throws SocketException {
        return create(Type.DATA, seqNo, outFile, data, target);
    }

    public static MetaPacket createACK(int seqNo, String outFile, InetSocketAddress target) throws SocketException {
        return create(Type.ACK, seqNo, outFile, null, target);
    }

    public static MetaPacket createNAK(int seqNo, String outFile, InetSocketAddress target) throws SocketException {
        return create(Type.NAK, seqNo, outFile, null, target);
    }

    /**
     * ACK and NAK as a reply
     */
    public static MetaPacket createACK(MetaPacket original) throws SocketException {
        return createACK(original.sequenceNumber, original.outFile, original.address);
    }

    public static MetaPacket createNAK(MetaPacket original) throws SocketException {
        return createNAK(original.sequenceNumber, original.outFile, original.address);
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
                meta.totalSize = buffer.getInt();
                // Skip corrupted length
                if (meta.size > MAX_PACKET_SIZE || meta.size < HEADER_SIZE + OUTFILE_LENGTH_SIZE) {
                    System.out.printf("Info: Packet size too small (%d), discarding\n", meta.size);
                    continue;
                }

                // Checksum
                CRC32 crc = new CRC32();
                crc.reset();
                crc.update(meta.rawData, CHECKSUM_SIZE, meta.totalSize - CHECKSUM_SIZE);
                // Checksum mismatch, skip
                if (crc.getValue() != checksum) {
                    System.out.printf("Info: Checksum mismatch, discarding\n\tExpected: %d\n\tActual: %d\n", crc.getValue(), checksum);
                    continue;
                }

                // Read in type and sequence number
                int typeAndSequence = buffer.getInt();
                meta.sequenceNumber = typeAndSequenceNumber & SEQ_MASK;
                int typeID = typeAndSequence >>> 30;
                meta.type = Type.values()[typeID];

                // Check if type is to be expected
                if (types != null && !types.contains(meta.type)) {
                    System.out.printf("Info: Expected types %s but got %s, discard\n",
                            types.toString(), meta.type.name());
                    continue;
                }

                // Resize packet if needed
                if (meta.totalSize != meta.rawData.length) {
                    byte[] newData = new byte[meta.totalSize];
                    System.arraycopy(meta.rawData, 0, newData, 0, newData.length);
                    // Save buffer offset
                    int offset = buffer.position();
                    // Update new data and new buffer
                    meta.rawData = newData;
                    buffer = ByteBuffer.wrap(meta.rawData);
                    buffer.position(offset);
                }

                // Get output file
                int outFileLength = (int) buffer.getShort();
                meta.outFile = new String(meta.rawData, buffer.position(), outFileLength);

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
