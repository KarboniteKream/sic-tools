import java.util.HashMap;
import java.util.Map;

public class Parser {
    public Lexer lexer;
    public Map<String, Mnemonic> mnemonics;

    public Parser() {
        initMnemonics();
    }

    public String parseLabel() {
        if (lexer.col == 1 && Character.isLetter(lexer.peek())) {
            return lexer.readAlphanumeric();
        }

        return null;
    }

    public Mnemonic parseMnemonic() throws SyntaxError {
        boolean isExtended = lexer.advanceIf('+');
        String name = lexer.readAlphanumeric();
        Mnemonic mnemonic = get(isExtended ? "+" + name : name);

        if (mnemonic == null) {
            throw new SyntaxError(String.format("Invalid mnemonic '%s'", name), lexer.row, lexer.col);
        }

        return mnemonic;
    }

    public String parseSymbol() {
        return lexer.readAlphanumeric();
    }

    public int parseRegister() throws SyntaxError {
        int ch = lexer.advance();
        int reg = "AXLBSTF".indexOf(ch);

        if (reg < 0) {
            throw new SyntaxError(String.format("Invalid register '%c'", ch), lexer.row, lexer.col);
        }

        return reg;
    }

    public void parseComma() throws SyntaxError {
        lexer.skipWhitespace();
        lexer.advance(',');
        lexer.skipWhitespace();
    }

    public boolean parseIndexed() throws SyntaxError {
        lexer.skipWhitespace();

        if (lexer.advanceIf(',')) {
            lexer.skipWhitespace();
            lexer.advance('X');
            return true;
        }

        return false;
    }

    public int parseNumber(int lo, int hi) throws SyntaxError {
        int num;

        if (lexer.peek() == '0') {
            int r = -1;

            switch (lexer.peek(1)) {
                case 'b':
                    r = 2;
                    break;

                case 'o':
                    r = 8;
                    break;

                case 'x':
                    r = 16;
                    break;
            }

            if (r != -1) {
                lexer.advance();
                lexer.advance();

                try {
                    num = Integer.parseInt(lexer.readDigits(r), r);
                } catch (NumberFormatException e) {
                    throw new SyntaxError("Invalid number", lexer.row, lexer.col);
                }
            } else {
                // fallback to decimal base
                try {
                    // TODO: Handle #.
                    num = Integer.parseInt(lexer.readDigits(10));
                } catch (NumberFormatException e) {
                    throw new SyntaxError("Invalid number", lexer.row, lexer.col);
                }
            }
        } else if (Character.isDigit(lexer.peek())) {
            try {
                num = Integer.parseInt(lexer.readDigits(10));
            } catch (NumberFormatException e) {
                throw new SyntaxError("Invalid number", lexer.row, lexer.col);
            }
        } else {
            throw new SyntaxError("Number expected", lexer.row, lexer.col);
        }

        // number must not be followed by letter or digit
        if (Character.isLetterOrDigit(lexer.peek())) {
            throw new SyntaxError(String.format("invalid digit '%c'", lexer.peek()), lexer.row, lexer.col);
        }

        // check range
        if (num < lo || num > hi) {
            throw new SyntaxError(String.format("Number '%d' out of range [%d..%d]", num, lo, hi),
                                  lexer.row, lexer.col);
        }

        return num;
    }

    public byte[] parseData() throws SyntaxError {
        // C'chars' or X'hex' or number
        if (lexer.advanceIf('C')) {
            lexer.advance('\'');

            return lexer.readTo('\'').getBytes();
        } else if (lexer.advanceIf('X')) {
            lexer.advance('\'');
            String s = lexer.readTo('\'');
            byte[] data = new byte[s.length() / 2];

            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
            }

            return data;
        } else if (Character.isDigit(lexer.peek())) {
            // number, represented by word
            int num = parseNumber(0, Code.MAX_WORD);
            byte[] data = new byte[3];
            data[2] = (byte) num;
            data[1] = (byte) (num >> 8);
            data[0] = (byte) (num >> 16);

            return data;
        }

        throw new SyntaxError(String.format("Invalid storage specifier '%s'", lexer.peek()), lexer.row,
                              lexer.col);
    }

    // instruction parser
    public Node parseInstruction() throws SyntaxError {
        // check for comment
        if (lexer.col == 1 && lexer.peek() == '.') {
            return new Comment(lexer.readTo('\n'));
        }

        // check for label
        String label = parseLabel();

        // skip whitespace: if EOL and without label then continue (i.e. skip empty lines)
        if (lexer.skipWhitespace() && label == null) {
            lexer.advance();  // skip EOL
            return null;
        }

        // parse mnemonic name
        Mnemonic mnemonic = parseMnemonic();
        // skip whitespace
        lexer.skipWhitespace();
        // parse any operands and obtain instruction
        Node node = mnemonic.parse(this);
        // set label and comment
        node.setLabel(label);
        node.setComment(lexer.readTo('\n'));

        return node;
    }

    public Code parseCode() throws SyntaxError {
        Code code = new Code();

        while (lexer.peek() > 0) {
            // skip comments / skip to the beginning of line
            while (lexer.peek() > 0 && lexer.col > 1) {
                lexer.readTo('\n');
            }

            // parse instruction
            Node instruction = parseInstruction();
            if (instruction != null) {
                code.append(instruction);
            }
        }

        return code;
    }

    public Code parse(String input) throws SyntaxError, SemanticError {
        lexer = new Lexer(input);
        return parseCode();
    }

    Mnemonic get(String name) {
        if (mnemonics.containsKey(name)) {
            return mnemonics.get(name);
        }

        return null;
    }

    void put(Mnemonic mnemonic) {
        mnemonics.put(mnemonic.name, mnemonic);
    }

    void put34(String name, int opcode, String hint, String description) {
        put(new MnemonicF3m(name, opcode, hint, description));
        put(new MnemonicF4m("+" + name, opcode, hint, description));
    }

    void initMnemonics() {
        this.mnemonics = new HashMap<>();

        // Directives
        put(new MnemonicD("NOBASE", Directive.NOBASE, "directive", "Unset base register."));
        put(new MnemonicD("LTORG", Directive.LTORG, "directive", "Flush literals."));
        put(new MnemonicDn("START", Directive.START, "directive", "Set code start address."));
        put(new MnemonicDn("END", Directive.END, "directive", "End code."));
        put(new MnemonicDn("BASE", Directive.BASE, "directive", "Set base register."));
        put(new MnemonicDn("EQU", Directive.EQU, "directive", "Equate symbol to expression."));
        put(new MnemonicDn("ORG", Directive.ORG, "directive", "Set location counter."));

        // Storage directives
        put(new MnemonicSn("RESB", Storage.RESB, "storage\t", "Reserve bytes."));
        put(new MnemonicSn("RESW", Storage.RESW, "storage\t", "Reserve words."));
        put(new MnemonicSd("BYTE", Storage.BYTE, "storage\t", "Initialize bytes."));
        put(new MnemonicSd("WORD", Storage.WORD, "storage\t", "Initialize words."));

        // Format 1 mnemonics, no operand
        put(new MnemonicF1("FIX", Opcode.FIX, "A<-int(F)", "Convert to fixed point number."));
        put(new MnemonicF1("FLOAT", Opcode.FLOAT, "F<-float (A)", "Convert to floating point number."));
        put(new MnemonicF1("NORM", Opcode.NORM, "F<-norm(F)", "Normalize"));
        put(new MnemonicF1("SIO", Opcode.SIO, "Start S, A", "Start program S of I/O channel A."));
        put(new MnemonicF1("HIO", Opcode.HIO, "Halt A\t", "Halt IO channel (A)"));
        put(new MnemonicF1("TIO", Opcode.TIO, "Test A\t", "Test IO channel (A)"));

        // Format 2 mnemonics, one or two operands
        put(new MnemonicF2n("SVC", Opcode.SVC, "Interrupt n", "Generate SVC interrupt n"));
        put(new MnemonicF2rn("SHIFTL", Opcode.SHIFTL, "(r1)<-(r1)<<n", "Shift left n bits"));
        put(new MnemonicF2rn("SHIFTR", Opcode.SHIFTR, "(r1)<-(r1)>>n", "Shift right n bits"));
        put(new MnemonicF2rr("ADDR", Opcode.ADDR, "r2<-(r2)+(r1)", "Add registers"));
        put(new MnemonicF2rr("SUBR", Opcode.SUBR, "r2<-(r2)-(r1)", "Subtract registers"));
        put(new MnemonicF2rr("MULR", Opcode.MULR, "r2<-(r2)*(r1)", "Multiply registers"));
        put(new MnemonicF2rr("DIVR", Opcode.DIVR, "r2<-(r2)/(r1)", "Divide registers"));
        put(new MnemonicF2rr("COMPR", Opcode.COMPR, "(r1):(r2)", "Compare registers"));
        put(new MnemonicF2rr("RMO", Opcode.RMO, "(r2)<-(r1)", "Move register"));
        put(new MnemonicF2r("CLEAR", Opcode.CLEAR, "r<-0\t", "Clear register"));
        put(new MnemonicF2r("TIXR", Opcode.TIXR, "X<-(X)+1;(X):(r)", "Increment and compare index register"));

        // Load and store
        put34("LDA", Opcode.LDA, "A<-(m..m+2)", "Load register A from address m");
        put34("LDCH", Opcode.LDCH, "A.1<-(m)", "Load byte to register A from address m");
        put34("LDB", Opcode.LDB, "B<-(m..m+2)", "Load register B from address m");
        put34("LDF", Opcode.LDF, "F<-(m..m+5)", "Load register F from address m");
        put34("LDL", Opcode.LDL, "L<-(m..m+2)", "Load register L from address m");
        put34("LDS", Opcode.LDS, "S<-(m..m+2)", "Load register S from address m");
        put34("LDT", Opcode.LDT, "T<-(m..m+2)", "Load register T from address m");
        put34("LDX", Opcode.LDX, "X<-(m..m+2)", "Load register X from address m");
        put34("LPS", Opcode.LPS, "PS->(m..2)", "Load processor status from address m");
        put34("STA", Opcode.STA, "m..m+2<-(A)", "Store register A to address m");
        put34("STCH", Opcode.STCH, "m<-(A.1)", "Store byte from register A to address m");
        put34("STB", Opcode.STB, "m..m+2<-(B)", "Store register B to address m");
        put34("STF", Opcode.STF, "m..m+5<-(F)", "Store register F to address m");
        put34("STL", Opcode.STL, "m..m+2<-(L)", "Store register L to address m");
        put34("STS", Opcode.STS, "m..m+2<-(S)", "Store register S to address m");
        put34("STT", Opcode.STT, "m..m+2<-(T)", "Store register T to address m");
        put34("STX", Opcode.STX, "m..m+2<-(X)", "Store register X to address m");
        put34("STI", Opcode.STI, "timer<-(m..m+2)", "Set interval timer");
        put34("STSW", Opcode.STS, "m..m+2<-(SW)", "Store processor status word to address m");

        // fixed point operations, register-memory
        put34("ADD", Opcode.ADD, "A<-(A)+(m..m+2)", "Add to accumulator");
        put34("SUB", Opcode.SUB, "A<-(A)-(m..m+2)", "Subtract from accumulator");
        put34("MUL", Opcode.MUL, "A<-(A)*(m..m+2)", "Multiply with accumulator");
        put34("DIV", Opcode.DIV, "A<-(A)/(m..m+2)", "Divide accumulator");
        put34("COMP", Opcode.COMP, "A<-(A):(m..m+2)", "Compare accumulator");
        put34("AND", Opcode.AND, "A<-(A)&(m..m+2)", "Bitwise and accumulator");
        put34("OR", Opcode.OR, "A<-(A)|(m..m+2)", "Bitwise or accumulator");
        put34("TIX", Opcode.TIX, "X<-(X)+1;(X):(m..m+2)", "Increment and compare index register");

        // floating point arithmetic
        put34("ADDF", Opcode.ADDF, "F<-(F)+(m..m+2)", "Floating point addition");
        put34("SUBF", Opcode.SUBF, "F<-(F)-(m..m+2)", "Floating point subtraction");
        put34("MULF", Opcode.MULF, "F<-(F)*(m..m+2)", "Floating point multiplication");
        put34("DIVF", Opcode.DIVF, "F<-(F)/(m..m+2)", "Floating point division");
        put34("COMPF", Opcode.COMPF, "F<-(F):(m..m+5)", "Floating point comparison");

        // jumps
        put34("J", Opcode.J, "PC<-m\t", "Unconditional jump");
        put34("JEQ", Opcode.JEQ, "PC<-m if CC is =", "Jump if equal");
        put34("JGT", Opcode.JGT, "PC<-m if CC is >", "Jump if greater than");
        put34("JLT", Opcode.JLT, "PC<-m if CC is <", "Jump if lower than");
        put34("JSUB", Opcode.JSUB, "L<-(PC);PC<-m", "Jump to subroutine");
        put(new MnemonicF3("RSUB", Opcode.RSUB, "PC<-(L)", "Return from subroutine."));

        // IO
        put34("RD", Opcode.RD, "A.1<-readdev (m)", "Read from device");
        put34("WD", Opcode.WD, "writedev(m),A.1", "Write to device");
        put34("TD", Opcode.TD, "testdev(m)", "Test device");

        // System
        put34("SSK", Opcode.SSK, "m<-(A)\t", "Protection key for address");
    }
}
