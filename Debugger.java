import java.nio.*;

public class Debugger {

  public static void invalidChecksum(PacketData data) {
    System.out.println("Invalid checksum! Culprit:");
    System.out.println(data);
    printBinary(data.getData());
  }

  public static void printBinary(byte[] data) {
    if (data.length == 0) return;
    // Extend data
    if ((data.length & 0b11) != 0) {
      int newLength = ((data.length >> 2) + 1) << 2;
      byte[] newData = new byte[newLength];
      System.arraycopy(data, 0, newData, 0, data.length);
      data = newData;
    }
    // Use buffer
    ByteBuffer buf = ByteBuffer.wrap(data);

    System.out.println("┏━━━━━━┳━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┳━━━━━━━━━━━━━━┳━━━━━━━━━━━━━┓");
    System.out.println("┃ Word ┃ Binary                                   ┃ Hexadecimal  ┃ Decimal     ┃");
    System.out.println("┣━━━━━━╋━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━╋━━━━━━━━━━━━━━╋━━━━━━━━━━━━━┫");

    for (int i = 0; i < (data.length / 4); i++) {
      int word = buf.getInt();
      System.out.printf("┃ %4d ┃ %s ┃ 0x %s ┃ %-11d ┃\n", i, binaryString(word), hexString(word), word);

      if (i % 4 == 3 && i != (data.length / 4) - 1) {
        System.out.println("┃      ┃                                          ┃              ┃             ┃");
      }
    }

    System.out.println("┗━━━━━━┻━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┻━━━━━━━━━━━━━━┻━━━━━━━━━━━━━┛");
  }

  private static String binaryString(int num) {
    StringBuilder sb = new StringBuilder(Integer.toBinaryString(num));
    while (sb.length() < 32) sb.insert(0, 0);
    final int[] pos = new int[] {4, 9, 14, 19, 20, 25, 30, 35};
    for (int i = 0; i < pos.length; i++)
      sb.insert(pos[i], ' ');
    return sb.toString();
  }

  private static String hexString(int num) {
    StringBuilder sb = new StringBuilder(Integer.toHexString(num));
    while (sb.length() < 8) sb.insert(0, 0);
    sb.insert(4, ' ');
    return sb.toString();
  }
}
