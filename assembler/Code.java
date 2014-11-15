import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Code {
    public static final int MAX_WORD = 16777216;
    public static final int MAX_ADDR = 1048576;

    public String name;

    public int currentAddr;
    public int nextAddr;
    public int baseAddr;

    private final List<Node> program;
    Map<String, Symbol> symbols;

    public int length;
    public int startAddr;
    public int endAddr;

    public Code() {
        this.name = "";
        this.program = new ArrayList<>();
        this.symbols = new HashMap<>();
    }

    public void append(Node node) {
        node.enter(this);
        node.activate(this);
        node.leave(this);

        program.add(node);
    }

    public void begin() {
        this.currentAddr = 0;
        this.nextAddr = 0;
        this.baseAddr = 0;
    }

    public void end() {
    }

    public void defineSymbol(String symbol, int value, boolean isEQU) {
        symbols.put(symbol, new Symbol(value, isEQU));
    }

    public String dumpCode() {
        StringBuilder dump = new StringBuilder();
        int addr = startAddr;

        for (Node node : program) {
            int len = node.length();
            StringBuilder hex = new StringBuilder();

            if (len > 0) {
                int[] data = new int[len];
                node.emitCode(data, 0);

                if (len <= 4) {
                    for (int d : data) {
                        hex.append(String.format("%02X", d));
                    }
                } else {
                    hex.append(String.format("%02X..%02X", data[0], data[len - 1]));
                }
            }

            dump.append(String.format(
                    "%06X  %-8s  %-7s %s\n",
                    addr, hex.toString(), node.label == null ? "" : node.label, node));

            addr += len;
        }

        return dump.toString();
    }

    public void resolve() throws SemanticError {
        begin();

        for (Node node : program) {
            node.enter(this);
            node.resolve(this);
            this.length += node.length();
            node.leave(this);
        }

        end();
    }

    public int[] emitCode() {
        int[] code = new int[1048576];
        int codeAddr = 0;

        for (Node node : program) {
            node.emitCode(code, codeAddr);
            codeAddr += node.length();
        }

        return code;
    }

    public String emitText() {
        StringBuilder obj = new StringBuilder();

        obj.append(String.format(
                "H%-6s%06X%06X\n",
                name, Integer.parseInt(Integer.toString(startAddr), 16), length));

        int[] code = emitCode();
        StringBuilder data = new StringBuilder();
        int count = 0;
        // FIXME: startAddr is in hexadecimal.
        int recordAddress = startAddr;
        int address = startAddr;
        boolean dataMode = false;

        for (Node node : program) {
            if (node instanceof Storage && ((Storage) node).data == null) {
                if (count > 0) {
                    obj.append(String.format("T%06X%02X%s\n", recordAddress, count, data.toString()));
                    count = 0;
                    data = new StringBuilder();
                }

                dataMode = true;
                address += node.length();
                continue;
            }

            if (dataMode) {
                recordAddress = address;
                dataMode = false;
            }

            int len = node.length();

            if (address + len - recordAddress > 30) {
                obj.append(String.format("T%06X%02X%s\n", recordAddress, count, data.toString()));

                count = 0;
                data = new StringBuilder();
                recordAddress = address;
            }

            for (int i = 0; i < len; i++) {
                data.append(String.format("%02X", code[address + i]));
            }

            count += len;
            address += len;
        }

        if (!dataMode) {
            obj.append(String.format("T%06X%02X%s\n", recordAddress, count, data.toString()));
        }

        obj.append(String.format("E%06X\n", endAddr));
        return obj.toString();
    }
}
