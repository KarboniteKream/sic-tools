public class Storage extends Node {
    public static final int RESB = 0x00;
    public static final int RESW = 0x01;
    public static final int BYTE = 0x02;
    public static final int WORD = 0x03;

    byte[] data;
    int number;

    public Storage(Mnemonic mnemonic, byte[] data) {
        super(mnemonic);
        this.data = data;
    }

    public Storage(Mnemonic mnemonic, int number) {
        super(mnemonic);
        this.number = number;
    }

    public int length() {
        if (data == null) {
            return number;
        } else if (data[0] + data[1] + data[2] != 0) {
            return data.length;
        } else {
            return 1;
        }
    }

    public void emitCode(int[] code, int codeAddr) {
        if (data == null) {
            for (int i = 0; i < number; i++) {
                code[codeAddr + i] = 0x00;
            }
        } else if (data[0] + data[1] + data[2] != 0) {
            for (int i = 0; i < data.length; i++) {
                code[codeAddr + i] = data[i];
                code[codeAddr + i] = code[codeAddr + i] & 0xFF;
            }
        } else {
            code[codeAddr] = data[0];
        }
    }
}
