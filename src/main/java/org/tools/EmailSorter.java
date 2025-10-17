package org.tools;
// Removed: import jakarta.mail.internet.InternetAddress;
import org.apache.james.mime4j.mboxiterator.MboxIterator;
import org.apache.james.mime4j.mboxiterator.CharBufferWrapper; // Required for iterating over raw message content
import org.apache.james.mime4j.message.DefaultMessageBuilder; // Used for native Mime4j parsing
import org.apache.james.mime4j.dom.Header; // Used for accessing message headers
import org.apache.james.mime4j.dom.Message; // Used for Mime4j message object
//import org.apache.james.mime4j. .MimeParseException; // Mime4j parsing exception (corrected package)
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.*;
import java.nio.charset.StandardCharsets; // Added for correct byte conversion
import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
public class EmailSorter implements CommandLineRunner {

    private static final int TOP_COUNT = 10;

    // The path to the MBOX file will be set via command-line argument.
    private String mboxFilePath;

    public static void main(String[] args) {
        SpringApplication.run(EmailSorter.class, args);
    }

    @Override
    public void run(String... args) {
        // --- 1. Argument Validation ---
        if (args.length == 0) {
            System.err.println("\nFATAL ERROR: MBOX file path not provided.");
            System.err.println("Usage Example: java -jar app.jar /path/to/your/3gb/mailData.mbox");
            return;
        }

        this.mboxFilePath = args[0];

        System.out.println("--- Starting MBOX Sender Analysis (Using Apache James Mime4j Native Parser) ---");
        System.out.println("Input MBOX file path: " + this.mboxFilePath);

        try {
            // --- 2. File Validation ---
            File mboxFile = new File(this.mboxFilePath);
            if (!mboxFile.exists()) {
                throw new FileNotFoundException("MBOX file not found at: " + this.mboxFilePath);
            }
            if (!mboxFile.isFile()) {
                throw new IOException("The path specified is not a file: " + this.mboxFilePath);
            }
            // Use long to avoid overflow when calculating size
            long fileSizeMB = mboxFile.length() / (1024 * 1024);
            System.out.println("File confirmed. Size: " + fileSizeMB + " MB. Starting processing...");


            // --- 3. Analysis and Sorting ---
            Map<String, Integer> senderCounts = analyzeMboxSenders(this.mboxFilePath);

            if (senderCounts.isEmpty()) {
                System.out.println("No senders found. The MBOX file may be empty, or message headers might be severely corrupted.");
                return;
            }

            // Sort the senders by count in descending order and limit to TOP_COUNT
            List<Map.Entry<String, Integer>> sortedSenders = senderCounts.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .limit(TOP_COUNT)
                    .collect(Collectors.toList());

            // --- 4. Output Results ---
            System.out.println("\n--- Top " + TOP_COUNT + " Senders by Email Count ---");
            for (int i = 0; i < sortedSenders.size(); i++) {
                Map.Entry<String, Integer> entry = sortedSenders.get(i);
                System.out.printf("%2d. Count: %6d | Sender: %s\n", (i + 1), entry.getValue(), entry.getKey());
            }

        } catch (FileNotFoundException e) {
            System.err.println("FATAL ERROR: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("FATAL ERROR: Could not access file. " + e.getMessage());
        } catch (Exception e) {
            System.err.println("\nAn unexpected error occurred during analysis:");
            e.printStackTrace();
        }

        System.out.println("\n--- Analysis Complete ---");
    }

    /**
     * Uses the Apache James Mime4j MboxIterator to read raw messages and uses
     * Mime4j's native parser to extract headers. It uses pure String manipulation
     * to extract the email address, removing dependency on the Jakarta Mail API.
     */
    private Map<String, Integer> analyzeMboxSenders(String filePath) throws IOException {
        Map<String, Integer> senderCounts = new HashMap<>();
        File mboxFile = new File(filePath);
        int messagesProcessed = 0;

        // Mime4j message builder for parsing raw streams
        DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();

        // MboxIterator iterates over CharBufferWrapper (raw message data)
        try (MboxIterator wrapper = MboxIterator.fromFile(mboxFile).build()) {

            for (CharBufferWrapper messageWrapper : wrapper) {
                String senderAddress = null;
                try {
                    // Convert the raw message content (CharBufferWrapper) to a byte array stream
                    String rawMessage = messageWrapper.toString();

                    // Use try-with-resources to ensure the stream is closed
                    try (InputStream is = new ByteArrayInputStream(rawMessage.getBytes(StandardCharsets.UTF_8))) {

                        // Parse the raw message InputStream into a Mime4j Message object
                        Message mime4jMessage = messageBuilder.parseMessage(is);

                        // --- START SENDER EXTRACTION LOGIC using Mime4j headers ---

                        // Strategy 1: Get 'From' address (most reliable)
                        // This header often contains "Name <email@example.com>"
                        Header fromHeader = mime4jMessage.getHeader().getFields("From").stream().findFirst().orElse(null);

                        if (fromHeader != null) {
                            // Use our robust helper function to extract the email part from the header body.
                            senderAddress = extractEmailFromHeader(fromHeader.getField("") .getBody());
                        }

                        // Strategy 2: Fallback to 'Return-Path'
                        if (senderAddress == null) {
                            Header returnPathHeader = mime4jMessage.getHeader().getField("Return-Path");
                            if (returnPathHeader != null) {
                                senderAddress = extractEmailFromHeader(returnPathHeader.getBody());
                            }
                        }

                        // Strategy 3: Fallback to 'Sender'
                        if (senderAddress == null) {
                            Header senderHeader = mime4jMessage.getHeader().getField("Sender");
                            if (senderHeader != null) {
                                senderAddress = extractEmailFromHeader(senderHeader.getBody());
                            }
                        }

                        if (senderAddress != null && !senderAddress.trim().isEmpty()) {
                            // Use the email address as the unique identifier and convert to lowercase
                            String emailAddress = senderAddress.toLowerCase();
                            senderCounts.put(emailAddress, senderCounts.getOrDefault(emailAddress, 0) + 1);
                        } else {
                            // Skip message and continue
                        }
                    }
                } catch (MimeParseException | IOException e) {
                    // Catch exceptions during Mime4j parsing of an individual message and skip it
                    // System.err.println("Failed to parse message: " + e.getMessage()); // Uncomment for more debugging
                } finally {
                    messagesProcessed++;
                    // Log progress every 1000 messages
                    if (messagesProcessed % 1000 == 0) {
                        System.out.println("Processed " + messagesProcessed + " messages...");
                    }
                }
            }
        } catch (IOException e) {
            // Catch exceptions during MboxIterator construction or iteration
            System.err.println("\nFATAL ERROR: Exception during MBOX iteration or message parsing: " + e.getMessage());
            throw e;
        }

        System.out.println("Total messages processed: " + messagesProcessed);
        return senderCounts;
    }

    /**
     * Helper method to extract an email address from a raw header value (e.g., from Return-Path, From).
     * This handles formats like "Name <email@example.com>" or just "<email@example.com>"
     */
    private String extractEmailFromHeader(String headerValue) {
        String trimmed = headerValue.trim();

        // Find the last '<' and the first '>' after it.
        int startBracket = trimmed.lastIndexOf('<');
        int endBracket = trimmed.indexOf('>', startBracket);

        if (startBracket != -1 && endBracket != -1) {
            // Found a bracketed email, extract content inside brackets
            return trimmed.substring(startBracket + 1, endBracket).trim();
        }

        // If no brackets, attempt to return the trimmed value. This covers simple Return-Path
        // headers that contain only the email address. We assume the content up to the first
        // space is the address if it contains an '@'.
        String potentialEmail = trimmed.split(" ")[0];
        if (potentialEmail.contains("@")) {
            return potentialEmail;
        }

        return trimmed;
    }
}
