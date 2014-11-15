import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main input.asm");
            System.exit(1);
        }

        String input = readFile(new File(args[0]));
        String filename = args[0].substring(0, args[0].length() - 4);

        Parser parser = new Parser();
        Code code;

        try {
            code = parser.parse(input);
            code.resolve();

            PrintWriter out = new PrintWriter(filename + ".obj");
            out.print(code.emitText());
            out.close();

            out = new PrintWriter(filename + ".lst");
            out.print(code.dumpCode());
            out.close();
        } catch (IOException | SyntaxError | SemanticError e) {
            System.err.println(e);
            System.exit(1);
        }
    }

    public static String readFile(File file) {
        try (InputStream stream = new FileInputStream(file)) {
            return new String(stream.readAllBytes());
        } catch (IOException e) {
            return "";
        }
    }
}
