package org.reprap.graphicio;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Stack;

final class RfoXmlRenderer {
    private final PrintStream XMLStream;
    private final Stack<String> stack = new Stack<String>();

    /**
     * Create an XML file called LegendFile starting with XML entry start.
     * 
     * @throws FileNotFoundException
     */
    RfoXmlRenderer(final File legendFile, final String start) throws FileNotFoundException {
        final FileOutputStream fileStream = new FileOutputStream(legendFile);
        XMLStream = new PrintStream(fileStream);
        push(start);
    }

    /**
     * Start item s
     */
    void push(final String s) {
        for (int i = 0; i < stack.size(); i++) {
            XMLStream.print(" ");
        }
        XMLStream.println("<" + s + ">");
        final int end = s.indexOf(" ");
        if (end < 0) {
            stack.push(s);
        } else {
            stack.push(s.substring(0, end));
        }
    }

    /**
     * Output a complete item s all in one go.
     */
    void write(final String s) {
        for (int i = 0; i < stack.size(); i++) {
            XMLStream.print(" ");
        }
        XMLStream.println("<" + s + "/>");
    }

    /**
     * End the current item.
     */
    void pop() {
        final String element = stack.pop();
        for (int i = 0; i < stack.size(); i++) {
            XMLStream.print(" ");
        }
        XMLStream.println("</" + element + ">");
    }

    /**
     * Wind it up.
     */
    void close() {
        while (stack.size() > 0) {
            pop();
        }
        XMLStream.close();
    }
}