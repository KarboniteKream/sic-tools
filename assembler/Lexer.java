public class Lexer {
    private final String input;
    private int pos;
    public int row;
    public int col;

    private int start;

    public Lexer(String input) {
        this.input = input;
        pos = 0;
        row = col = 1;
    }

    public void mark() {
        start = pos;
    }

    public String extract(int ofs) {
        return input.substring(start, pos + ofs);
    }

    public String extract() {
        return extract(0);
    }

    public char peek(int ahead) {
        if (pos + ahead < input.length()) {
            return input.charAt(pos + ahead);
        }

        return 0;
    }

    public char peek() {
        return peek(0);
    }

    public char advance() {
        // advance to then next char
        char ch = peek();
        pos++;

        // update location
        if (ch == '\n') {
            row++;
            col = 1;
        } else if (ch == '\t') {
            col = ((col - 1) / 4) * 4 + 5;
        } else {
            col++;
        }

        return ch;
    }

    public boolean advanceIf(char ch) {
        if (this.peek() != ch) {
            return false;
        }

        advance();
        return true;
    }

    public void advance(char ch) throws SyntaxError {
        if (!advanceIf(ch)) {
            throw new SyntaxError("',' expected", row, col);
        }
    }

    // Skip whitespace (space and tab), and return true if EOL.
    public boolean skipWhitespace() {
        while (peek() == ' ' || peek() == '\t') {
            advance();
        }

        return peek() == '\n' || peek() == 0;
    }

    public String readTo(char delimiter) {
        mark();

        while (peek() > 0 && peek() != delimiter) {
            advance();
        }

        advance();
        return extract(-1);
    }

    public String readAlphanumeric() {
        mark();

        while (Character.isLetterOrDigit(peek()) || peek() == '_') {
            advance();
        }

        return extract();
    }

    protected String readDigits(int radix) {
        mark();

        while (Character.digit(peek(), radix) != -1) {
            advance();
        }

        return extract();
    }
}
