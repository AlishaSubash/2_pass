

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

public class AssemblerGUI {
    private JTextArea inputArea;
    private JTextArea outputArea;
    private JTextArea opcodeArea; // Text area for opcode definitions
    private JButton pass1Button;
    private JButton pass2Button;
    private Map<String, Integer> symbolTable = new HashMap<>();
    private Map<String, String> opcodeMap = new HashMap<>(); // Dynamic opcode map
    private StringBuilder intermediateCode = new StringBuilder();
    private int startAddress = -1; // Initialize with an invalid value
    private String programName = ""; // To store the program name from START

    public AssemblerGUI() {
        JFrame frame = new JFrame("Two-Pass Assembler");
        frame.setSize(600, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        inputArea = new JTextArea(10, 50);
        outputArea = new JTextArea(10, 50);
        opcodeArea = new JTextArea(5, 50); // For opcode definitions
        pass1Button = new JButton("Run Pass 1");
        pass2Button = new JButton("Run Pass 2");
        JPanel panel = new JPanel();
        panel.add(new JScrollPane(opcodeArea)); // Add opcode area
        panel.add(new JLabel("Enter Opcode Table (Format: opcode machine_code):"));
        panel.add(new JScrollPane(inputArea));
        panel.add(pass1Button);
        panel.add(pass2Button);
        panel.add(new JScrollPane(outputArea));
        frame.add(panel);
        frame.setVisible(true);

        pass1Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadOpcodes();
                String input = inputArea.getText();
                String pass1Output = runPass1(input);
                outputArea.setText(pass1Output);
            }
        });

        pass2Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String pass2Output = runPass2(intermediateCode.toString(), startAddress);
                outputArea.setText(pass2Output);
            }
        });
    }

    private void loadOpcodes() {
        opcodeMap.clear();
        String[] lines = opcodeArea.getText().split("\n");
        for (String line : lines) {
            String[] parts = line.split("\\s+");
            if (parts.length == 2) {
                opcodeMap.put(parts[0], parts[1]); // opcode to machine code mapping
            }
        }
    }

    private String runPass1(String input) {
        String[] lines = input.split("\n");
        int locationCounter = 0;
        intermediateCode.setLength(0); // Clear previous intermediate code
        symbolTable.clear(); // Clear previous symbol table

        for (String line : lines) {
            String[] parts = line.split("\\s+");
            if (parts.length == 0) continue;

            String label = parts[0];
            String opcode = parts.length > 1 ? parts[1] : "";
            String operand = parts.length > 2 ? parts[2] : "";

            // Handle START opcode
            if (opcode.equals("START") && !operand.isEmpty()) {
                try {
                    startAddress = Integer.parseInt(operand, 16); // Parse as hexadecimal
                    locationCounter = startAddress; // Set location counter to start address
                    programName = label; // Store the program name from the label
                } catch (NumberFormatException e) {
                    // Ignore invalid start addresses
                }
                continue; // Skip to the next line
            }

            // Handle label
            if (!label.isEmpty() && isValidLabel(label)) {
                symbolTable.put(label, locationCounter);
            }

            // Handle directives and opcode
            if (opcode.equals("RESW")) {
                intermediateCode.append(formatLocationCounter(locationCounter)).append(" ").append(label).append(" ").append(opcode).append(" ").append(operand).append("\n");
                locationCounter += 3 * Integer.parseInt(operand); // 3 bytes for each word
            } else if (opcode.equals("RESB")) {
                intermediateCode.append(formatLocationCounter(locationCounter)).append(" ").append(label).append(" ").append(opcode).append(" ").append(operand).append("\n");
                locationCounter += Integer.parseInt(operand); // Reserve n bytes
            } else if (opcode.equals("BYTE")) {
                intermediateCode.append(formatLocationCounter(locationCounter)).append(" ").append(label).append(" ").append(opcode).append(" ").append(operand).append("\n");
                if (operand.startsWith("C'") && operand.endsWith("'")) {
                    locationCounter += operand.length() - 3; // Character constant length
                } else if (operand.startsWith("X'") && operand.endsWith("'")) {
                    locationCounter += (operand.length() - 2) / 2; // Hexadecimal constant
                }
            } else if (opcode.equals("WORD")) {
                intermediateCode.append(formatLocationCounter(locationCounter)).append(" ").append(label).append(" ").append(opcode).append(" ").append(operand).append("\n");
                locationCounter += 3; // Each word is 3 bytes
            } else if (isValidOpcode(opcode)) {
                intermediateCode.append(formatLocationCounter(locationCounter)).append(" ").append(label).append(" ").append(opcode).append(" ").append(operand).append("\n");
                locationCounter += 3; // Increment for standard opcodes
            }
        }

        // Include the END directive in intermediate code
        intermediateCode.append(formatLocationCounter(locationCounter)).append(" END\n");
        return "Symbol Table:\n" + formatSymbolTable() + "\n\nIntermediate Code:\n" + intermediateCode.toString();
    }

    private String formatLocationCounter(int locationCounter) {
        return String.format("%04X", locationCounter); // Ensure 4-digit hexadecimal formatting
    }

    private String formatSymbolTable() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : symbolTable.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(formatLocationCounter(entry.getValue())).append("\n");
        }
        return sb.toString();
    }

    private boolean isValidOpcode(String opcode) {
        return opcodeMap.containsKey(opcode);
    }

    private boolean isValidLabel(String label) {
        return label.matches("^[a-zA-Z][a-zA-Z0-9_]*$");
    }

    private String runPass2(String intermediateCode, int startAddress) {
        String[] lines = intermediateCode.split("\n");
        StringBuilder objectProgram = new StringBuilder();
        StringBuilder textSection = new StringBuilder();
        int textLength = 0;

        // Ensure the starting address is set
        if (startAddress == -1) {
            return "Error: Start address not defined.";
        }

        // Create the object program header
        objectProgram.append(String.format("H ^ %s ^ %06X\n", programName, startAddress));

        for (String line : lines) {
            String[] parts = line.split("\\s+");
            if (parts.length < 3) continue;

            int address = Integer.parseInt(parts[0], 16); // Read address as hexadecimal
            String opcode = parts[1];
            String operand = parts[2];

            // Convert opcode to machine code
            String machineCode = opcodeMap.get(opcode);
            if (machineCode == null) {
                continue; // Skip invalid opcodes
            }

            // Resolve operand to address or value
            String resolvedOperand = "0000"; // Default to 0 if operand is invalid
            if (symbolTable.containsKey(operand)) {
                resolvedOperand = String.format("%04X", symbolTable.get(operand)); // Get address of the label in hexadecimal
            } else if (operand.matches("\\d+")) {
                resolvedOperand = String.format("%04X", Integer.parseInt(operand)); // Ensure 4-digit hexadecimal formatting
            }

            // Combine opcode and resolved operand for object code
            String combinedValue = machineCode + resolvedOperand;
            textSection.append(combinedValue).append("^");
            textLength += 3; // Assuming each entry is 3 bytes
        }

        // Create the object program text section
        if (textLength > 0) {
            objectProgram.append(String.format("T ^ %06X ^ %02X ^ %s \n", startAddress, textLength, textSection.toString().replaceAll(" \\^$ ", ""))); // Remove last ^
        }

        objectProgram.append(String.format("E ^ %06X\n", startAddress)); // Print start address in hexadecimal

        return "Object Program:\n" + objectProgram.toString();
    }

    public static void main(String[] args) {
        new AssemblerGUI();
    }
}
