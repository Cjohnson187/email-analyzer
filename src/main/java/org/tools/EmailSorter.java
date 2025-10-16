package org.tools;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Session;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
public class EmailSorter implements CommandLineRunner {

    private static final String MBOX_FILE_PATH = "Inbox.mbox";
    private static final int TOP_COUNT = 10;

    public static void main(String[] args) {
        SpringApplication.run(EmailSorter.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println("--- Starting MBOX Sender Analysis ---");
        System.out.println("Expected MBOX file location: " + new File(MBOX_FILE_PATH).getAbsolutePath());

        try {
            Map<String, Integer> senderCounts = analyzeMboxSenders(MBOX_FILE_PATH);

            if (senderCounts.isEmpty()) {
                System.out.println("No senders found or the MBOX file is empty.");
                return;
            }

            // Sort the senders by count in descending order
            List<Map.Entry<String, Integer>> sortedSenders = senderCounts.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .limit(TOP_COUNT)
                    .collect(Collectors.toList());

            // --- Output Results ---
            System.out.println("\n--- Top " + TOP_COUNT + " Senders by Email Count ---");
            for (int i = 0; i < sortedSenders.size(); i++) {
                Map.Entry<String, Integer> entry = sortedSenders.get(i);
                System.out.printf("%2d. Count: %6d | Sender: %s\n", (i + 1), entry.getValue(), entry.getKey());
            }

        } catch (FileNotFoundException e) {
            System.err.println("Error: MBOX file not found. Please ensure 'Inbox.mbox' is in the project root.");
        } catch (Exception e) {
            System.err.println("\nAn unexpected error occurred during analysis:");
            e.printStackTrace();
        }

        System.out.println("\n--- Analysis Complete ---");
    }

    /**
     * Manually reads the MBOX file, segments it by the 'From ' delimiter,
     * and extracts the sender address from each segment using Jakarta Mail.
     */
    private Map<String, Integer> analyzeMboxSenders(String filePath) throws Exception {
        Map<String, Integer> senderCounts = new HashMap<>();
        File mboxFile = new File(filePath);

        // Standard MBOX files use 'From ' (with a trailing space) as a message separator
        try (BufferedReader reader = new BufferedReader(new FileReader(mboxFile))) {
            StringBuilder messageBuffer = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                // Check for the MBOX message delimiter
                if (line.startsWith("From ")) {
                    // Process the previous message if the buffer is not empty
                    if (messageBuffer.length() > 0) {
                        processMessageBuffer(messageBuffer.toString(), senderCounts);
                    }
                    // Start a new message buffer
                    messageBuffer.setLength(0);
                }
                messageBuffer.append(line).append("\n");
            }

            // Process the last message in the file
            if (messageBuffer.length() > 0) {
                processMessageBuffer(messageBuffer.toString(), senderCounts);
            }
        }
        return senderCounts;
    }

    /**
     * Takes a raw message string, parses it using MimeMessage, and updates the sender count.
     */
    private void processMessageBuffer(String rawMessage, Map<String, Integer> senderCounts) {
        try {
            // Use a clean session property set to avoid system-wide config issues
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props);

            // Convert the message string to an input stream
            InputStream is = new ByteArrayInputStream(rawMessage.getBytes());
            MimeMessage message = new MimeMessage(session, is);

            // Extract the "From" address
            jakarta.mail.Address[] fromAddresses = message.getFrom();

            if (fromAddresses != null && fromAddresses.length > 0) {
                jakarta.mail.Address sender = fromAddresses[0];

                if (sender instanceof InternetAddress internetAddress) {
                    // Use the email address as the unique identifier and convert to lowercase
                    String emailAddress = internetAddress.getAddress().toLowerCase();
                    senderCounts.put(emailAddress, senderCounts.getOrDefault(emailAddress, 0) + 1);
                }
            }
        } catch (Exception e) {
            // Catch parsing errors for corrupted or malformed messages and skip them
            // System.err.println("Skipping malformed message: " + e.getMessage());
        }
    }
}