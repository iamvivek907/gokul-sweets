package com.gokulsweets.restaurant.menuimport;

import com.gokulsweets.restaurant.observability.MethodTiming;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

/** Streaming preflight: no workbook objects are allocated until every ZIP part is bounded. */
final class MenuWorkbookLimits {

    static final int MAX_DATA_ROWS = AppConstant.MENU_WORKBOOK_LIMITS_MAX_DATA_ROWS;

    static final int MAX_COLUMNS = AppConstant.MENU_WORKBOOK_LIMITS_MAX_COLUMNS;

    // Menu_Upload and the template's Reference_Data.
    private static final int MAX_SHEETS = AppConstant.MENU_WORKBOOK_LIMITS_MAX_SHEETS;

    private static final int MAX_CELLS = MAX_SHEETS * (MAX_DATA_ROWS + 1) * MAX_COLUMNS;

    private static final long MAX_ENTRY_BYTES = AppConstant.MENU_WORKBOOK_LIMITS_MAX_ENTRY_BYTES;

    private static final long MAX_EXPANDED_BYTES =
            AppConstant.MENU_WORKBOOK_LIMITS_MAX_EXPANDED_BYTES;

    /**
     * Validates menu workbook limits data.
     *
     * @param input the input supplied to this method
     * @throws XMLStreamException when the method rejects the request with {@code External XML
     *     references are not supported.}
     * @throws IOException if the underlying operation fails
     */
    static void validate(InputStream input) throws IOException {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkbookLimits.class, "validate(InputStream)");
        try {
            var factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            factory.setXMLResolver(
                    (publicId, systemId, baseUri, namespace) -> {
                        throw new XMLStreamException("External XML references are not supported.");
                    });
            var budget = new Budget();
            try (var zip = new ZipInputStream(input)) {
                byte[] buffer = new byte[8192];
                int entries = 0;
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > 1000) reject("The workbook contains too many entries.");
                    var part = new BoundedPart(zip, budget);
                    String name = entry.getName().toLowerCase(Locale.ROOT);
                    // POI follows content types/relationships, not filename extensions. Restrict
                    // menu templates to XML parts so no renamed part can escape XML budgets.
                    // Embedded media, binary parts and arbitrary extensions are unsupported.
                    if (!entry.isDirectory()) {
                        if (!name.endsWith(".xml") && !name.endsWith(".rels"))
                            reject(
                                    "Unsupported workbook part filename. Use the .xlsx menu"
                                            + " template without embedded media or binary parts.");
                        validateXml(factory, part, budget);
                    }
                    while (part.read(buffer) != -1) {
                        /* Count directories and any trailing bytes. */
                    }
                    zip.closeEntry();
                }
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuWorkbookLimits.class, "validate(InputStream)");
        }
    }

    /**
     * Validates xml.
     *
     * @param factory the factory
     * @param input the input
     * @param budget the budget
     */
    private static void validateXml(XMLInputFactory factory, InputStream input, Budget budget) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuWorkbookLimits.class,
                        "validateXml(XMLInputFactory,InputStream,Budget)");
        try {
            try {
                var xml = factory.createXMLStreamReader(input);
                try {
                    int depth = 0;
                    int rows = 0;
                    boolean worksheet = false;
                    while (xml.hasNext()) {
                        int event = xml.next();
                        if (event == XMLStreamConstants.DTD)
                            reject("Workbook XML declarations are not supported.");
                        if (event == XMLStreamConstants.START_ELEMENT) {
                            if (++depth > 32 || ++budget.nodes > 60000)
                                reject(
                                        "The workbook contains too much XML content. Use the menu"
                                                + " template.");
                            String element = xml.getLocalName();
                            if (depth == 1 && element.equals("worksheet")) {
                                worksheet = true;
                                if (++budget.sheets > MAX_SHEETS)
                                    reject(
                                            "Menu upload supports at most two worksheets, including"
                                                    + " Reference_Data.");
                            }
                            if (worksheet && element.equals("row")) {
                                if (++rows > MAX_DATA_ROWS + 1) rejectRows();
                                String reference = xml.getAttributeValue(null, "r");
                                if (reference != null
                                        && positiveNumber(reference) > MAX_DATA_ROWS + 1)
                                    rejectRows();
                            }
                            if (worksheet && element.equals("c")) {
                                if (++budget.cells > MAX_CELLS)
                                    reject(
                                            "The workbook contains too many cells. Use the menu"
                                                    + " template.");
                                String reference = xml.getAttributeValue(null, "r");
                                if (reference == null)
                                    reject("Workbook cells must have Excel references.");
                                int column = 0;
                                int index = 0;
                                while (index < reference.length()
                                        && reference.charAt(index) >= 'A'
                                        && reference.charAt(index) <= 'Z') {
                                    column = column * 26 + reference.charAt(index++) - 'A' + 1;
                                    if (column > MAX_COLUMNS)
                                        reject(
                                                "Menu upload supports only the 17 template columns"
                                                        + " (A–Q).");
                                }
                                if (column == 0 || index == reference.length())
                                    reject("Invalid workbook cell reference.");
                                if (positiveNumber(reference.substring(index)) > MAX_DATA_ROWS + 1)
                                    rejectRows();
                            }
                        } else if (event == XMLStreamConstants.END_ELEMENT) {
                            depth--;
                        } else if (event == XMLStreamConstants.CHARACTERS
                                || event == XMLStreamConstants.CDATA) {
                            budget.text += xml.getTextLength();
                            if (budget.text > 1024 * 1024)
                                reject(
                                        "The workbook contains too much text. Upload a smaller menu"
                                                + " file.");
                        }
                    }
                } finally {
                    xml.close();
                }
            } catch (XMLStreamException failure) {
                // Some StAX implementations wrap a bounded-stream validation exception.
                for (Throwable cause = failure; cause != null; cause = cause.getCause())
                    if (cause instanceof MenuImportValidationException validation) throw validation;
                throw new MenuImportValidationException(
                        "Unable to read the workbook XML. Use a valid menu template.", failure);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkbookLimits.class,
                    "validateXml(XMLInputFactory,InputStream,Budget)");
        }
    }

    /**
     * Positives number.
     *
     * @param value the value
     * @return the positive number result
     */
    private static int positiveNumber(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkbookLimits.class, "positiveNumber(String)");
        try {
            try {
                int number = Integer.parseInt(value);
                if (number < 1) throw new NumberFormatException();
                return number;
            } catch (NumberFormatException failure) {
                throw new MenuImportValidationException("Invalid workbook row reference.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuWorkbookLimits.class, "positiveNumber(String)");
        }
    }

    /** Rejects rows. */
    private static void rejectRows() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkbookLimits.class, "rejectRows()");
        try {
            reject("Menu upload supports at most 500 data rows per worksheet.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuWorkbookLimits.class, "rejectRows()");
        }
    }

    /**
     * Rejects menu workbook limits data.
     *
     * @param message the message supplied to this method
     */
    private static void reject(String message) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkbookLimits.class, "reject(String)");
        try {
            throw new MenuImportValidationException(message);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuWorkbookLimits.class, "reject(String)");
        }
    }

    /** Backend budget contract and implementation. */
    private static final class Budget {

        long bytes;

        int nodes;

        int text;

        int cells;

        int sheets;
    }

    /** Backend bounded part contract and implementation. */
    private static final class BoundedPart extends FilterInputStream {

        private final Budget budget;

        private long bytes;

        /**
         * Creates a bounded part instance.
         *
         * @param input the input
         * @param budget the budget
         */
        BoundedPart(InputStream input, Budget budget) {
            super(input);
            this.budget = budget;
        }

        /**
         * Returns count information for bounded part.
         *
         * @param amount the amount supplied to this method
         */
        private void count(int amount) {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(MenuWorkbookLimits.BoundedPart.class, "count(int)");
            try {
                if (amount < 0) return;
                bytes += amount;
                budget.bytes += amount;
                if (bytes > MAX_ENTRY_BYTES || budget.bytes > MAX_EXPANDED_BYTES)
                    reject("The expanded workbook is too large. Upload a smaller menu file.");
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        MenuWorkbookLimits.BoundedPart.class,
                        "count(int)");
            }
        }

        /**
         * Returns read information for bounded part.
         *
         * @return the value of {@code value}
         * @throws IOException if the underlying operation fails
         */
        @Override
        public int read() throws IOException {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(MenuWorkbookLimits.BoundedPart.class, "read()");
            try {
                int value = in.read();
                if (value >= 0) count(1);
                return value;
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos, MenuWorkbookLimits.BoundedPart.class, "read()");
            }
        }

        /**
         * Returns read information for bounded part.
         *
         * @param bytes the bytes supplied to this method
         * @param offset the offset supplied to this method
         * @param length the length supplied to this method
         * @return the value of {@code count}
         * @throws IOException if the underlying operation fails
         */
        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(
                            MenuWorkbookLimits.BoundedPart.class, "read(byte[],int,int)");
            try {
                int count = in.read(bytes, offset, length);
                count(count);
                return count;
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        MenuWorkbookLimits.BoundedPart.class,
                        "read(byte[],int,int)");
            }
        }

        /** Closes bounded part data. */
        @Override
        public void close() {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(MenuWorkbookLimits.BoundedPart.class, "close()");
            try {
                /* ZIP owner closes the stream after all parts. */
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos, MenuWorkbookLimits.BoundedPart.class, "close()");
            }
        }
    }
}
