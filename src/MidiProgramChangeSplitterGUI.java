import javax.sound.midi.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A Java GUI application to parse MIDI sequences, remap program changes and notes
 * based on rules in a unified CSV file, and then split each sequence into separate tracks
 * in a single output MIDI file. Each new track corresponds to a distinct
 * program change "stem" AND its original MIDI channel.
 * Melodic splits are kept on the original channel; DRUM segments are rechanneled to channel 9.
 * <p>
 * CSV Format (7 columns):
 * "TrackName,OriginalProgramChange,RemappedProgramChange,OriginalNote,RemappedNote,LayeredNotes,ChannelType"
 */
public class MidiProgramChangeSplitterGUI extends JFrame {

    // GUI Components
    private JTextField midiFilesField;
    private JButton browseMidiButton;
    private JTextField outputFolderField;
    private JButton browseOutputButton;
    private JTextField csvFileField;
    private JButton browseCsvButton;
    private JButton processButton;
    private static JTextArea statusArea;
    private JScrollPane statusScrollPane;

    private final List<File> selectedMidiFiles = new ArrayList<>();
    private File selectedOutputFolder;
    private File selectedCsvFile;

    private static final int DRUM_CHANNEL = 9; // MIDI channel 10 (0-indexed)
    private static final int MAX_MIDI_CHANNEL = 15; // MIDI channels 0-15

    private int[] currentBankMSB = new int[16]; // Stores MSB for each of 16 channels
    private int[] currentBankLSB = new int[16]; // Stores LSB for each of 16 channels

    private static final String[] generalMidiDrumKits = new String[128];

    static {

        generalMidiDrumKits[0] = "Standard Drum Kit";

        for (int i = 1; i <= 6; i++) {
            generalMidiDrumKits[i] = "Drum Kit";
        }

        generalMidiDrumKits[7] = "Room Drum Kit";

        for (int i = 8; i <= 14; i++) {
            generalMidiDrumKits[i] = "Drum Kit";
        }

        generalMidiDrumKits[15] = "Power Drum Kit";

        for (int i = 16; i <= 22; i++) {
            generalMidiDrumKits[i] = "Drum Kit";
        }

        generalMidiDrumKits[23] = "Electronic Drum Kit";

        generalMidiDrumKits[24] = "Analog Drum Kit (TR-808)"; // Often referred to as "Rap" or "TR-808"

        for (int i = 25; i <= 30; i++) {
            generalMidiDrumKits[i] = "Drum Kit";
        }

        generalMidiDrumKits[31] = "Jazz Drum Kit";

        for (int i = 32; i <= 38; i++) {
            generalMidiDrumKits[i] = "Drum Kit";
        }

        generalMidiDrumKits[39] = "Brush Kit";

        for (int i = 40; i <= 46; i++) {
            generalMidiDrumKits[i] = "Drum Kit";
        }

        generalMidiDrumKits[47] = "Orchestral Drum Kit";

        for (int i = 48; i <= 54; i++) {
            generalMidiDrumKits[i] = "Drum Kit";
        }

        generalMidiDrumKits[55] = "SFX Drum Kit";

        for (int i = 56; i <= 127; i++) {
            generalMidiDrumKits[i] = "Drum Kit";
        }

    }

    String[] generalMidiInstrumentNames = {
            // Piano (0-7)
            "Acoustic Grand Piano", "Bright Acoustic Piano", "Electric Grand Piano", "Honky-tonk Piano",
            "Electric Piano 1", "Electric Piano 2", "Harpsichord", "Clavinet",

            // Chromatic Percussion (8-15)
            "Celesta", "Glockenspiel", "Music Box", "Vibraphone",
            "Marimba", "Xylophone", "Tubular Bells", "Dulcimer",

            // Organ (16-23)
            "Drawbar Organ", "Percussive Organ", "Rock Organ", "Church Organ",
            "Reed Organ", "Accordion", "Harmonica", "Tango Accordion",

            // Guitar (24-31)
            "Acoustic Guitar (nylon)", "Acoustic Guitar (steel)", "Electric Guitar (jazz)", "Electric Guitar (clean)",
            "Electric Guitar (muted)", "Overdriven Guitar", "Distortion Guitar", "Guitar Harmonics",

            // Bass (32-39)
            "Acoustic Bass", "Electric Bass (finger)", "Electric Bass (pick)", "Fretless Bass",
            "Slap Bass 1", "Slap Bass 2", "Synth Bass 1", "Synth Bass 2",

            // Strings (40-47)
            "Violin", "Viola", "Cello", "Contrabass",
            "Tremolo Strings", "Pizzicato Strings", "Orchestral Harp", "Timpani",

            // Ensemble (48-55)
            "String Ensemble 1", "String Ensemble 2", "SynthStrings 1", "SynthStrings 2",
            "Choir Aahs", "Voice Oohs", "Synth Voice", "Orchestra Hit",

            // Brass (56-63)
            "Trumpet", "Trombone", "Tuba", "Muted Trumpet",
            "French Horn", "Brass Section", "SynthBrass 1", "SynthBrass 2",

            // Reed (64-71)
            "Soprano Sax", "Alto Sax", "Tenor Sax", "Baritone Sax",
            "Oboe", "English Horn", "Bassoon", "Clarinet",

            // Pipe (72-79)
            "Piccolo", "Flute", "Recorder", "Pan Flute",
            "Blown Bottle", "Shakuhachi", "Whistle", "Ocarina",

            // Synth Lead (80-87)
            "Lead 1 (square)", "Lead 2 (sawtooth)", "Lead 3 (calliope)", "Lead 4 (chiff)",
            "Lead 5 (charang)", "Lead 6 (voice)", "Lead 7 (fifths)", "Lead 8 (bass+lead)",

            // Synth Pad (88-95)
            "Pad 1 (new age)", "Pad 2 (warm)", "Pad 3 (polysynth)", "Pad 4 (choir)",
            "Pad 5 (bowed)", "Pad 6 (metallic)", "Pad 7 (halo)", "Pad 8 (sweep)",

            // Synth Effects (96-103)
            "FX 1 (rain)", "FX 2 (soundtrack)", "FX 3 (crystal)", "FX 4 (atmosphere)",
            "FX 5 (brightness)", "FX 6 (goblin)", "FX 7 (echoes)", "FX 8 (sci-fi)",

            // Ethnic (104-111)
            "Sitar", "Banjo", "Shamisen", "Koto",
            "Kalimba", "Didgeridoo", "Bagpipe", "Fiddle",

            // Percussive (112-119)
            "Tinkle Bell", "Agogo", "Steel Drums",
            "Woodblock", "Taiko Drum", "Melodic Tom", "Synth Drum",

            // Sound Effects (120-127)
            "Reverse Cymbal", "Guitar Fret Noise", "Breath Noise", "Seashore",
            "Bird Tweet", "Telephone Ring", "Helicopter", "Applause", "Gunshot"
    };

    /**
     * Represents a unique key for an output track. This key now differentiates tracks
     * not only by remapped program and original channel/segment but also by
     * the effective channel (the one the event is placed on) and its determined type (DRUM/MELODIC),
     * allowing for fine-grained splitting.
     */
    private static class ProgramChannelKey {
        int remappedProgramForSegment; // The remapped program that defines the segment context
        int effectiveChannel;          // The actual channel the MIDI event will be placed on (finalOutputChannel for notes)
        String type;                   // The determined type for this content (DRUM or MELODIC or GLOBAL)
        int segmentIndex;              // Index to differentiate segments based on original program changes

        public ProgramChannelKey(int remappedProgramForSegment, int effectiveChannel, String type, int segmentIndex) {
            this.remappedProgramForSegment = remappedProgramForSegment;
            this.effectiveChannel = effectiveChannel;
            this.type = type;
            this.segmentIndex = segmentIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProgramChannelKey that = (ProgramChannelKey) o;
            return remappedProgramForSegment == that.remappedProgramForSegment &&
                    effectiveChannel == that.effectiveChannel &&
                    segmentIndex == that.segmentIndex &&
                    Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(remappedProgramForSegment, effectiveChannel, type, segmentIndex);
        }
    }

    /**
     * Stores information about a program change remapping, including the original program
     * that set this context, the remapped program, the determined channel type (DRUM/MELODIC),
     * and a track name.
     */
    private static class ProgramRemapInfo {
        int originalProgramThatSetThisContext; // -1 if no PC has set the context yet
        int remappedProgram;
        String channelType; // "DRUM" or "MELODIC"
        String trackName; // Can be null if no specific name from rule

        public ProgramRemapInfo(int originalProgramThatSetThisContext, int remappedProgram, String channelType, String trackName) {
            this.originalProgramThatSetThisContext = originalProgramThatSetThisContext;
            this.remappedProgram = remappedProgram;
            this.channelType = channelType;
            this.trackName = trackName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProgramRemapInfo that = (ProgramRemapInfo) o;
            return originalProgramThatSetThisContext == that.originalProgramThatSetThisContext &&
                    remappedProgram == that.remappedProgram &&
                    Objects.equals(channelType, that.channelType) &&
                    Objects.equals(trackName, that.trackName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(originalProgramThatSetThisContext, remappedProgram, channelType, trackName);
        }
    }

    /**
     * Represents a single unified remapping rule from the CSV file, which can be
     * either a program change remapping rule or a note manipulation rule.
     */
    private static class UnifiedRemapRule {
        String trackName; // This field will now always be null as per user request
        int originalProgram;
        int remappedProgram;
        int originalNote; // -999 for program change rule, -1 for all notes, 0-127 for specific note
        int remappedNoteOrOffset; // -999 for program change rule, offset for all notes, specific note for specific note
        boolean isLayered; // True if this rule creates a layered note
        String channelType; // "DRUM" or "MELODIC"

        public UnifiedRemapRule(String trackName, int originalProgram, int remappedProgram,
                                int originalNote, int remappedNoteOrOffset, boolean isLayered,
                                String channelType) {
            this.trackName = trackName; // Will be passed as null from loadRemappingRules
            this.originalProgram = originalProgram;
            this.remappedProgram = remappedProgram;
            this.originalNote = originalNote;
            this.remappedNoteOrOffset = remappedNoteOrOffset;
            this.isLayered = isLayered;
            this.channelType = channelType;
        }

        /**
         * Determines if this rule is a program change remapping rule.
         * A program change rule is identified by specific values for originalNote and remappedNoteOrOffset.
         */
        public boolean isProgramChangeRule() {
            return (originalNote == -999 && remappedNoteOrOffset == -999);
        }

        /**
         * Determines if this rule is a note manipulation rule (remapping, shifting, or layering).
         */
        public boolean isNoteManipulationRule() {
            return !isProgramChangeRule();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UnifiedRemapRule that = (UnifiedRemapRule) o;
            return originalProgram == that.originalProgram &&
                    remappedProgram == that.remappedProgram &&
                    originalNote == that.originalNote &&
                    remappedNoteOrOffset == that.remappedNoteOrOffset &&
                    isLayered == that.isLayered &&
                    Objects.equals(trackName, that.trackName) &&
                    Objects.equals(channelType, that.channelType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(trackName, originalProgram, remappedProgram, originalNote, remappedNoteOrOffset, isLayered, channelType);
        }
    }

    // Static maps to store loaded remapping rules
    private static List<UnifiedRemapRule> allRemapRules = new ArrayList<>();
    private static Map<Integer, Map<Integer, ProgramRemapInfo>> programChangeRemap = new HashMap<>(); // Not explicitly used in current logic, but kept for potential future use or debugging
    private static Map<Integer, List<UnifiedRemapRule>> drumNoteManipulationRulesByOriginalProgram = new HashMap<>();
    private static Map<Integer, List<UnifiedRemapRule>> melodicNoteManipulationRulesByOriginalProgram = new HashMap<>();
    private static Map<Integer, String> remappedProgramDefaultChannelType = new HashMap<>(); // Stores default channel type for a remapped program
    private static Map<Integer, String> originalProgramDefaultChannelType = new HashMap<>(); // Stores default channel type for an original program

    public MidiProgramChangeSplitterGUI() {
        super("MIDI Splitter & Remapper");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        initComponents();
        addListeners();
        layoutComponents();
    }

    /**
     * Initializes the GUI components.
     */
    private void initComponents() {
        midiFilesField = new JTextField("No MIDI files selected...", 40);
        midiFilesField.setEditable(false);
        browseMidiButton = new JButton("Browse MIDI File(s)");

        outputFolderField = new JTextField("No output folder selected...", 40);
        outputFolderField.setEditable(false);
        browseOutputButton = new JButton("Browse Output Folder");

        csvFileField = new JTextField("No CSV file selected...", 40);
        csvFileField.setEditable(false);
        browseCsvButton = new JButton("Browse CSV File");

        processButton = new JButton("Process MIDI Files");
        statusArea = new JTextArea(15, 60);
        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusScrollPane = new JScrollPane(statusArea);
    }

    /**
     * Adds action listeners to the GUI buttons.
     */
    private void addListeners() {
        browseMidiButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setMultiSelectionEnabled(true);
                fileChooser.setFileFilter(new FileNameExtensionFilter("MIDI Files (*.mid, *.midi)", "mid", "midi"));
                int returnValue = fileChooser.showOpenDialog(MidiProgramChangeSplitterGUI.this);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    File[] files = fileChooser.getSelectedFiles();
                    selectedMidiFiles.clear();
                    StringBuilder sb = new StringBuilder();
                    for (File file : files) {
                        selectedMidiFiles.add(file);
                        sb.append(file.getName()).append("; ");
                    }
                    midiFilesField.setText(sb.toString());
                }
            }
        });

        browseOutputButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int returnValue = fileChooser.showOpenDialog(MidiProgramChangeSplitterGUI.this);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    selectedOutputFolder = fileChooser.getSelectedFile();
                    outputFolderField.setText(selectedOutputFolder.getAbsolutePath());
                }
            }
        });

        browseCsvButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
                int returnValue = fileChooser.showOpenDialog(MidiProgramChangeSplitterGUI.this);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    selectedCsvFile = fileChooser.getSelectedFile();
                    csvFileField.setText(selectedCsvFile.getAbsolutePath());
                }
            }
        });

        processButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                processMidi();
            }
        });
    }

    /**
     * Lays out the GUI components using GridBagLayout.
     */
    private void layoutComponents() {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        add(new JLabel("MIDI File(s):"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(midiFilesField, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        add(browseMidiButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        add(new JLabel("Output Folder:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(outputFolderField, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        add(browseOutputButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        add(new JLabel("Remapping CSV:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(csvFileField, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        add(browseCsvButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        add(processButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        add(statusScrollPane, gbc);
    }

    /**
     * Appends a message to the status area.
     * @param message The message to log.
     */
    private static void logStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            statusArea.append(message + "\n");
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
    }

    /**
     * Logs a warning message to the console and status area.
     * @param message The warning message.
     */
    private static void logWarning(String message) {
        System.err.println("WARNING: " + message);
        logStatus("Warning: " + message);
    }

    /**
     * Logs an error message to the console and status area, including stack trace if an exception is provided.
     * @param message The error message.
     * @param e The exception, or null if no exception.
     */
    private static void logError(String message, Throwable e) {
        System.err.println("ERROR: " + message);
        if (e != null) {
            e.printStackTrace(System.err);
        }
        logStatus("Error: " + message);
    }

    /**
     * Initiates the MIDI processing in a separate thread.
     * Handles file selection validation and error reporting.
     */
    private void processMidi() {

        statusArea.setText(""); // Clear previous status
        logStatus("Starting MIDI processing...");

        if (selectedMidiFiles.isEmpty()) {
            logError("No MIDI files selected.", null);
            return;
        }
        if (selectedOutputFolder == null) {
            logError("No output folder selected.", null);
            return;
        }
        if (selectedCsvFile == null) {
            logError("No remapping CSV file selected.", null);
            return;
        }

        setGuiEnabled(false); // Disable GUI during processing

        new Thread(() -> {
            try {
                loadRemappingRules(selectedCsvFile);
                logStatus("Loaded " + allRemapRules.size() + " unified remapping rules.");
                logStatus("Loaded " + drumNoteManipulationRulesByOriginalProgram.size() + " drum note manipulation rule sets by original program.");
                logStatus("Loaded " + melodicNoteManipulationRulesByOriginalProgram.size() + " melodic note manipulation rule sets by original program.");
                logStatus("Loaded " + remappedProgramDefaultChannelType.size() + " remapped program default channel types.");
                logStatus("Loaded " + originalProgramDefaultChannelType.size() + " original program default channel types.");

                if (!selectedOutputFolder.exists()) {
                    if (!selectedOutputFolder.mkdirs()) {
                        logError("Could not create output directory: " + selectedOutputFolder.getAbsolutePath(), null);
                        return;
                    }
                }

                for (File inputMidiFile : selectedMidiFiles) {
                    processSingleMidiFile(inputMidiFile, selectedOutputFolder);
                }

                logStatus("\nAll MIDI files processed successfully!");

            } catch (IOException | NumberFormatException e) {
                logError("Error loading remapping rules: " + e.getMessage(), e);
            }
            catch (Exception e) {
                logError("An unexpected error occurred during processing: " + e.getMessage(), e);
            } finally {
                setGuiEnabled(true); // Re-enable GUI after processing
            }
        }).start();
    }

    /**
     * Enables or disables the GUI input components.
     * @param enabled True to enable, false to disable.
     */
    private void setGuiEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            browseMidiButton.setEnabled(enabled);
            browseOutputButton.setEnabled(enabled);
            browseCsvButton.setEnabled(enabled);
            processButton.setEnabled(enabled);
        });
    }

    /**
     * Processes a single MIDI file: reads its events, applies remapping rules,
     * splits events into new tracks based on program change segments and channel types,
     * and saves the resulting MIDI sequence to an output file.
     * @param inputMidiFile The MIDI file to process.
     * @param outputDirectory The directory to save the output file.
     * @throws InvalidMidiDataException If the MIDI data is invalid.
     * @throws IOException If an I/O error occurs.
     */
    private void processSingleMidiFile(File inputMidiFile, File outputDirectory) throws InvalidMidiDataException, IOException {
        logStatus("\nProcessing MIDI file: " + inputMidiFile.getName() + "...");

        Arrays.fill(currentBankMSB, 0);
        Arrays.fill(currentBankLSB, 0);

        currentBankLSB[9] = 128;

        Sequence originalSequence = MidiSystem.getSequence(inputMidiFile);
        logStatus("  Original sequence has " + originalSequence.getTracks().length + " tracks.");
        logStatus("  Tick resolution: " + originalSequence.getResolution() + " ticks per " +
                (originalSequence.getDivisionType() == Sequence.PPQ ? "quarter note" : "frame"));

        // Create a new sequence to hold the combined and split tracks
        Sequence combinedSequence = new Sequence(originalSequence.getDivisionType(), originalSequence.getResolution());

        // Map to hold output tracks, keyed by a combination of remapped program, effective channel, determined type, and segment index.
        Map<ProgramChannelKey, Track> programTracks = new HashMap<>();

        // Map to keep track of the current program remapping info for each original MIDI channel.
        Map<Integer, ProgramRemapInfo> currentProgramRemapInfoByOriginalChannel = new HashMap<>();

        // Map to keep track of the current segment index for each original MIDI channel.
        Map<Integer, Integer> currentSegmentIndexByOriginalChannel = new HashMap<>();

        // Set to keep track of MIDI channels (0-15, excluding DRUM_CHANNEL) that are currently assigned
        // to remapped melodic content.
        Set<Integer> assignedMelodicChannels = new HashSet<>();

        // Counter for errors encountered during event processing for logging purposes.
        AtomicInteger eventProcessingErrors = new AtomicInteger();

        // Pre-scan to find the first explicit program change for each channel
        Map<Integer, Integer> firstExplicitProgramChangePerChannel = new HashMap<>();
        for (int trackIndex = 0; trackIndex < originalSequence.getTracks().length; trackIndex++) {
            Track originalTrack = originalSequence.getTracks()[trackIndex];
            for (int i = 0; i < originalTrack.size(); i++) {
                MidiEvent event = originalTrack.get(i);
                MidiMessage message = event.getMessage();
                if (message instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) message;
                    if (sm.getCommand() == ShortMessage.PROGRAM_CHANGE) {
                        int originalChannel = sm.getChannel();
                        if (!firstExplicitProgramChangePerChannel.containsKey(originalChannel)) {
                            firstExplicitProgramChangePerChannel.put(originalChannel, sm.getData1());
                        }
                    }
                }
            }
        }

        // Iterate through each track in the original MIDI sequence
        for (int trackIndex = 0; trackIndex < originalSequence.getTracks().length; trackIndex++) {
            Track originalTrack = originalSequence.getTracks()[trackIndex];
            logStatus("  Processing original track " + (trackIndex + 1) + " of " + originalSequence.getTracks().length + "...");

            // Iterate through each MIDI event in the current original track
            for (int i = 0; i < originalTrack.size(); i++) {
                MidiEvent event = originalTrack.get(i);
                MidiMessage message = event.getMessage();
                MidiEvent eventToProcess = event;
                List<MidiEvent> eventsToAdd = new ArrayList<>(); // For layered notes

                // Log progress periodically
                if (i % 1000 == 0 && i > 0) {
                    logStatus("    Processed " + i + " events in current track...");
                }

                // Process ShortMessages (Note On/Off, Program Change, etc.)
                if (message instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) message;
                    int command = sm.getCommand();
                    int originalChannel = sm.getChannel();

                    if (command == ShortMessage.CONTROL_CHANGE) {
                        int controller = sm.getData1();
                        int value = sm.getData2();
                        if (controller == 0) {
                            currentBankMSB[originalChannel] = value;
                        }
                        else if (controller == 32) {
                            currentBankLSB[originalChannel] = value * 128;
                        }
                    }

                    int segmentIndexForCurrentEvent;
                    ProgramRemapInfo currentRemapInfoForOriginalChannel;
                    int currentProgramForOriginalChannel; // This is the remapped program for the segment context

                    // Initialize program remapping info for a channel if it's the first event for that channel
                    if (!currentProgramRemapInfoByOriginalChannel.containsKey(originalChannel)) {
                        segmentIndexForCurrentEvent = 0;
                        currentSegmentIndexByOriginalChannel.put(originalChannel, 0);

                        ProgramRemapInfo initialRemapInfo = null;
                        int determinedInitialOriginalProgram = -1;

                        if (firstExplicitProgramChangePerChannel.containsKey(originalChannel)) {
                            determinedInitialOriginalProgram = firstExplicitProgramChangePerChannel.get(originalChannel);
                            logStatus("    First explicit PC for Original Ch " + (originalChannel + 1) + " is P" + determinedInitialOriginalProgram + ".");
                        } else {
                            determinedInitialOriginalProgram = 0; // MIDI default
                            logStatus("    No explicit PC found for Original Ch " + (originalChannel + 1) + ". Initializing to MIDI Default Program 0.");
                        }

                        // Find a matching program change remapping rule
                        for (UnifiedRemapRule rule : allRemapRules) {
                            if (rule.isProgramChangeRule() && rule.originalProgram == determinedInitialOriginalProgram) {
                                initialRemapInfo = new ProgramRemapInfo(rule.originalProgram, rule.remappedProgram, rule.channelType, null);
                                break;
                            }
                        }

                        // If no specific rule found for the determined initial program, default to remapping to itself
                        if (initialRemapInfo == null) {
                            String defaultType = originalProgramDefaultChannelType.getOrDefault(determinedInitialOriginalProgram, "MELODIC");
                            initialRemapInfo = new ProgramRemapInfo(determinedInitialOriginalProgram, determinedInitialOriginalProgram, defaultType, null);
                        }

                        currentRemapInfoForOriginalChannel = initialRemapInfo;
                        currentProgramRemapInfoByOriginalChannel.put(originalChannel, currentRemapInfoForOriginalChannel);
                        currentProgramForOriginalChannel = currentRemapInfoForOriginalChannel.remappedProgram;
                        logStatus("    First event on Original Ch " + (originalChannel + 1) + ". Initial segment context: Orig P" + currentRemapInfoForOriginalChannel.originalProgramThatSetThisContext + ", Remap P" + currentProgramForOriginalChannel + ", Type: " + currentRemapInfoForOriginalChannel.channelType + ".");

                    } else {
                        segmentIndexForCurrentEvent = currentSegmentIndexByOriginalChannel.get(originalChannel);
                        currentRemapInfoForOriginalChannel = currentProgramRemapInfoByOriginalChannel.get(originalChannel);
                        currentProgramForOriginalChannel = currentRemapInfoForOriginalChannel.remappedProgram;
                    }

                    // --- Apply Program Change Remapping ---
                    if (command == ShortMessage.PROGRAM_CHANGE) {
                        int originalProgramNumber = sm.getData1();
                        ProgramRemapInfo newRemapInfoForChannel = null;

                        int currentBank = currentBankLSB[originalChannel];

                        int patchNumber = currentBank + originalProgramNumber;

                        // Find a matching program change remapping rule
                        for (UnifiedRemapRule rule : allRemapRules) {
                            if (rule.isProgramChangeRule() && rule.originalProgram == patchNumber) {
                                newRemapInfoForChannel = new ProgramRemapInfo(rule.originalProgram, rule.remappedProgram, rule.channelType, null);
                                break;
                            }
                        }
                        // If no specific rule found, default to remapping to itself with its original type
                        if (newRemapInfoForChannel == null) {
                            String defaultType = originalProgramDefaultChannelType.getOrDefault(originalProgramNumber, "MELODIC");
                            newRemapInfoForChannel = new ProgramRemapInfo(originalProgramNumber, originalProgramNumber, defaultType, null);
                        }

                        // Check if this program change triggers a new segment
                        boolean shouldTriggerNewSegment = !newRemapInfoForChannel.equals(currentRemapInfoForOriginalChannel);

                        if (shouldTriggerNewSegment) {
                            segmentIndexForCurrentEvent++;
                            currentSegmentIndexByOriginalChannel.put(originalChannel, segmentIndexForCurrentEvent);
                            logStatus("    PC Event at tick " + event.getTick() + " on Original Ch " + (originalChannel + 1) + " triggers new segment. New segment index: " + segmentIndexForCurrentEvent);
                        }

                        // Update the current remapping info for this channel
                        currentProgramRemapInfoByOriginalChannel.put(originalChannel, newRemapInfoForChannel);
                        currentProgramForOriginalChannel = newRemapInfoForChannel.remappedProgram;

                        logStatus("    PC Event: Original Prog " + originalProgramNumber + ", Remapped Prog " + currentProgramForOriginalChannel + ", Determined Type: " +
                                newRemapInfoForChannel.channelType + " at tick " + event.getTick() + " (Rule Applied)");

                        // If program number actually changed, create a new Program Change event
                        if (originalProgramNumber != currentProgramForOriginalChannel) {
                            ShortMessage remappedSm = new ShortMessage();
                            try {
                                remappedSm.setMessage(ShortMessage.PROGRAM_CHANGE, originalChannel, currentProgramForOriginalChannel, 0);
                                eventToProcess = new MidiEvent(remappedSm, event.getTick());
                                logStatus("      Program Change Event Remapped: " + originalProgramNumber + " -> " + currentProgramForOriginalChannel);
                            } catch (InvalidMidiDataException e) {
                                logError("Error remapping program change for event at tick " + event.getTick() + ": " + e.getMessage() + ". Event will retain its original program.", e);
                                eventProcessingErrors.getAndIncrement();
                            }
                        }

                        // For a PC event, the effective channel is the original channel, and type is the segment type
                        ProgramChannelKey logicalTrackKeyForPC = new ProgramChannelKey(
                                currentProgramForOriginalChannel,
                                originalChannel,
                                newRemapInfoForChannel.channelType, // Use the type from the new PC rule
                                segmentIndexForCurrentEvent
                        );

                        Track targetTrack = programTracks.computeIfAbsent(logicalTrackKeyForPC, k -> {
                            Track newTrack = combinedSequence.createTrack();
                            String fullTrackName;
                            fullTrackName = k.effectiveChannel == 9 ? generalMidiDrumKits[k.remappedProgramForSegment] : generalMidiInstrumentNames[k.remappedProgramForSegment];
                            logStatus("      Creating new track for " + fullTrackName);
                            try {
                                MetaMessage trackNameMessage = new MetaMessage();
                                trackNameMessage.setMessage(0x03, fullTrackName.getBytes(), fullTrackName.length());
                                newTrack.add(new MidiEvent(trackNameMessage, 0));
                            } catch (InvalidMidiDataException e) {
                                logError("Error setting track name for " + fullTrackName + ": " + e.getMessage(), e);
                                eventProcessingErrors.getAndIncrement();
                            }
                            return newTrack;
                        });
                        eventsToAdd.add(0, eventToProcess); // Add the main event first
                        for (MidiEvent finalEvent : eventsToAdd) {
                            targetTrack.add(finalEvent);
                        }

                    }
                    // --- Apply Note Remapping, Octave Shifting, Layering ---
                    else if (command == ShortMessage.NOTE_ON || command == ShortMessage.NOTE_OFF) {
                        ShortMessage currentSm = (ShortMessage) eventToProcess.getMessage();
                        int currentNote = currentSm.getData1();
                        int currentVelocity = currentSm.getData2();

                        int finalNote = currentNote;
                        boolean specificRemapApplied = false;
                        // Default the note's channel type to the segment's type
                        String determinedChannelTypeForNote = currentRemapInfoForOriginalChannel.channelType;
                        int programKeyForNoteRules = currentRemapInfoForOriginalChannel.originalProgramThatSetThisContext;

                        // --- Step 1: Find a specific note remapping rule for the current note ---
                        UnifiedRemapRule specificNoteRule = null;

                        // Search in DRUM note manipulation rules first
                        List<UnifiedRemapRule> drumRules = drumNoteManipulationRulesByOriginalProgram.getOrDefault(programKeyForNoteRules, Collections.emptyList());
                        for (UnifiedRemapRule rule : drumRules) {
                            if (rule.isNoteManipulationRule() && rule.originalNote == currentNote) {
                                specificNoteRule = rule;
                                logStatus("          Found specific DRUM note rule for Original Note " + currentNote);
                                break;
                            }
                        }

                        // If not found in DRUM rules, search in MELODIC note manipulation rules
                        if (specificNoteRule == null) {
                            List<UnifiedRemapRule> melodicRules = melodicNoteManipulationRulesByOriginalProgram.getOrDefault(programKeyForNoteRules, Collections.emptyList());
                            for (UnifiedRemapRule rule : melodicRules) {
                                if (rule.isNoteManipulationRule() && rule.originalNote == currentNote) {
                                    specificNoteRule = rule;
                                    logStatus("          Found specific MELODIC note rule for Original Note " + currentNote);
                                    break;
                                }
                            }
                        }

                        if (specificNoteRule != null) {
                            // A specific rule was found, apply its properties
                            determinedChannelTypeForNote = specificNoteRule.channelType; // This is the crucial override
                            if (specificNoteRule.remappedNoteOrOffset >= 0 && specificNoteRule.remappedNoteOrOffset <= 127) {
                                finalNote = specificNoteRule.remappedNoteOrOffset;
                                specificRemapApplied = true;
                                logStatus("          Specific Note Remap Applied: Original " + currentNote + " to " + finalNote + " (Type: " + determinedChannelTypeForNote + ")");
                            } else {
                                logWarning("Specific note remapping for note " + currentNote + " results in out-of-range target note: " + specificNoteRule.remappedNoteOrOffset + ". Rule skipped.");
                            }
                        } else {
                            // No specific note rule found, fall back to "all notes" shift based on segment's determined type
                            logStatus("          No specific note rule found for Original Note " + currentNote + ". Applying general rules based on segment type: " + currentRemapInfoForOriginalChannel.channelType);

                            List<UnifiedRemapRule> rulesToConsiderForGeneralShift = new ArrayList<>();
                            if ("DRUM".equals(currentRemapInfoForOriginalChannel.channelType)) {
                                rulesToConsiderForGeneralShift.addAll(drumNoteManipulationRulesByOriginalProgram.getOrDefault(programKeyForNoteRules, Collections.emptyList()));
                            } else {
                                rulesToConsiderForGeneralShift.addAll(melodicNoteManipulationRulesByOriginalProgram.getOrDefault(programKeyForNoteRules, Collections.emptyList()));
                            }

                            // Apply "all notes" shift if no specific remap was applied
                            if (!specificRemapApplied) {
                                for (UnifiedRemapRule rule : rulesToConsiderForGeneralShift) {
                                    if (rule.isNoteManipulationRule() && rule.originalNote == -1 && !rule.isLayered) {
                                        int calculatedNote = finalNote + rule.remappedNoteOrOffset;
                                        if (calculatedNote >= 0 && calculatedNote <= 127) {
                                            finalNote = calculatedNote;
                                            logStatus("          All Notes Shift Applied: Note shifted to " + finalNote + " (from original " + currentNote + ")");
                                        } else {
                                            logWarning("All notes shift for note " + finalNote + " results in out-of-range note: " + calculatedNote + ". Rule skipped.");
                                        }
                                    }
                                }
                            }
                        }

                        // Apply layering rules (these can create additional events and should consider their own channelType)
                        List<UnifiedRemapRule> allLayeringRulesForProgram = new ArrayList<>();
                        allLayeringRulesForProgram.addAll(drumNoteManipulationRulesByOriginalProgram.getOrDefault(programKeyForNoteRules, Collections.emptyList()));
                        allLayeringRulesForProgram.addAll(melodicNoteManipulationRulesByOriginalProgram.getOrDefault(programKeyForNoteRules, Collections.emptyList()));

                        for (UnifiedRemapRule rule : allLayeringRulesForProgram) {
                            if (rule.isNoteManipulationRule() && rule.originalNote == -1 && rule.isLayered) {
                                int layeredNote = currentNote + rule.remappedNoteOrOffset;
                                if (layeredNote >= 0 && layeredNote <= 127) {
                                    int channelForLayeredNote = originalChannel;

                                    if ("DRUM".equals(rule.channelType)) {
                                        channelForLayeredNote = DRUM_CHANNEL;
                                    } else if ("MELODIC".equals(rule.channelType)) {
                                        if (originalChannel == DRUM_CHANNEL) {
                                            int assignedChannel = -1;
                                            for (int ch = 0; ch < DRUM_CHANNEL; ch++) {
                                                if (!assignedMelodicChannels.contains(ch)) {
                                                    assignedChannel = ch;
                                                    break;
                                                }
                                            }
                                            if (assignedChannel == -1) {
                                                for (int ch = DRUM_CHANNEL + 1; ch <= MAX_MIDI_CHANNEL; ch++) {
                                                    if (!assignedMelodicChannels.contains(ch)) {
                                                        assignedChannel = ch;
                                                        break;
                                                    }
                                                }
                                            }
                                            if (assignedChannel != -1) {
                                                channelForLayeredNote = assignedChannel;
                                                assignedMelodicChannels.add(assignedChannel);
                                            } else {
                                                logWarning("      Layered note remapped to MELODIC, but no available melodic channel found. Keeping on original Ch " + (originalChannel + 1) + ".");
                                            }
                                        }
                                    }

                                    ShortMessage layeredSm = new ShortMessage();
                                    try {
                                        layeredSm.setMessage(command, channelForLayeredNote, layeredNote, currentVelocity);
                                        eventsToAdd.add(new MidiEvent(layeredSm, event.getTick()));
                                        logStatus("          Layering Note: Original " + currentNote + " layered to " + layeredNote + " on channel " + (channelForLayeredNote + 1) + " (type: " + rule.channelType + ")");
                                    } catch (InvalidMidiDataException e) {
                                        logError("Error creating layered note for event at tick " + event.getTick() + ": " + e.getMessage(), e);
                                        eventProcessingErrors.getAndIncrement();
                                    }
                                } else {
                                    logWarning("Layering for note " + currentNote + " results in out-of-range note: " + layeredNote + ". Layering rule skipped.");
                                }
                            }
                        }

                        // Update the event's note if it changed
                        ShortMessage updatedSm = (ShortMessage) eventToProcess.getMessage();
                        if (updatedSm.getData1() != finalNote) {
                            ShortMessage newSm = new ShortMessage();
                            try {
                                newSm.setMessage(command, originalChannel, finalNote, currentVelocity);
                                eventToProcess = new MidiEvent(newSm, event.getTick());
                            } catch (InvalidMidiDataException e) {
                                logError("Error updating note after rule application for event at tick " + event.getTick() + ": " + e.getMessage(), e);
                                eventProcessingErrors.getAndIncrement();
                            }
                        }

                        // --- Determine Final Output Channel based on determinedChannelTypeForNote ---
                        int finalOutputChannel = originalChannel;

                        if ("DRUM".equals(determinedChannelTypeForNote)) {
                            finalOutputChannel = DRUM_CHANNEL;
                        } else if ("MELODIC".equals(determinedChannelTypeForNote)) {
                            if (originalChannel == DRUM_CHANNEL) {
                                int assignedChannel = -1;
                                for (int ch = 0; ch < DRUM_CHANNEL; ch++) {
                                    if (!assignedMelodicChannels.contains(ch)) {
                                        assignedChannel = ch;
                                        break;
                                    }
                                }
                                if (assignedChannel == -1) {
                                    for (int ch = DRUM_CHANNEL + 1; ch <= MAX_MIDI_CHANNEL; ch++) {
                                        if (!assignedMelodicChannels.contains(ch)) {
                                            assignedChannel = ch;
                                            break;
                                        }
                                    }
                                }

                                if (assignedChannel != -1) {
                                    finalOutputChannel = assignedChannel;
                                    assignedMelodicChannels.add(assignedChannel);
                                    logStatus("      Note remapped to MELODIC. Rechanneling from Ch " + (originalChannel + 1) + " to available melodic Ch " + (finalOutputChannel + 1));
                                } else {
                                    finalOutputChannel = originalChannel;
                                    assignedMelodicChannels.add(originalChannel);
                                    logWarning("      Note remapped to MELODIC, but no available melodic channel found. Keeping on original Ch " + (originalChannel + 1) + ".");
                                }
                            } else {
                                finalOutputChannel = originalChannel;
                                assignedMelodicChannels.add(originalChannel);
                            }
                        }

                        // Rechannel the event if the determined final output channel is different
                        ShortMessage finalSm = (ShortMessage) eventToProcess.getMessage();
                        if (finalSm.getChannel() != finalOutputChannel) {
                            ShortMessage newSm = new ShortMessage();
                            try {
                                newSm.setMessage(finalSm.getCommand(), finalOutputChannel, finalSm.getData1(), finalSm.getData2());
                                eventToProcess = new MidiEvent(newSm, eventToProcess.getTick());
                                logStatus("      Event rechanneled from Ch " + (originalChannel + 1) + " to Ch " + (finalOutputChannel + 1) + " based on note type: " + determinedChannelTypeForNote);
                            } catch (InvalidMidiDataException e) {
                                logError("Error rechanneling event to " + (finalOutputChannel + 1) + " at tick " + event.getTick() + ": " + e.getMessage() + ". Event will retain its original channel.", e);
                                eventProcessingErrors.getAndIncrement();
                            }
                        }

                        // Determine the key for the target output track for a NOTE event
                        // This key now includes the effective (final) channel and the determined channel type for the note
                        ProgramChannelKey logicalTrackKeyForNote = new ProgramChannelKey(
                                currentProgramForOriginalChannel, // The remapped program for the segment context
                                finalOutputChannel,               // The actual channel the note event is placed on
                                determinedChannelTypeForNote,     // The specific channel type for this note
                                segmentIndexForCurrentEvent       // Still grouped by segment
                        );

                        Track targetTrack = programTracks.computeIfAbsent(logicalTrackKeyForNote, k -> {
                            Track newTrack = combinedSequence.createTrack();
                            String fullTrackName;
                            // Track name for notes
                            fullTrackName = "Notes P" + k.remappedProgramForSegment + " (Orig P" + currentRemapInfoForOriginalChannel.originalProgramThatSetThisContext + ", Final Ch " + (k.effectiveChannel + 1) + ") [" + k.type + "]";
                            fullTrackName += " Segment " + k.segmentIndex;

                            logStatus("      Creating new track for " + fullTrackName);
                            try {
                                fullTrackName = k.effectiveChannel == 9 ? generalMidiDrumKits[k.remappedProgramForSegment] : generalMidiInstrumentNames[k.remappedProgramForSegment];
                                MetaMessage trackNameMessage = new MetaMessage();
                                trackNameMessage.setMessage(0x03, fullTrackName.getBytes(), fullTrackName.length());
                                newTrack.add(new MidiEvent(trackNameMessage, 0));
                            } catch (InvalidMidiDataException e) {
                                logError("Error setting track name for " + fullTrackName + ": " + e.getMessage(), e);
                                eventProcessingErrors.getAndIncrement();
                            }
                            return newTrack;
                        });

                        // Add the processed event (and any layered events) to the target track
                        eventsToAdd.add(0, eventToProcess); // Add the main event first
                        for (MidiEvent finalEvent : eventsToAdd) {
                            targetTrack.add(finalEvent);
                        }

                    } else { // Handle other ShortMessages (CC, Pitch Bend etc.) and non-ShortMessage events (MetaMessage, SysexMessage)
                        // These will be grouped into tracks based on the original channel's program context and type,
                        // or a general global track for Meta/Sysex messages not specific to a channel.

                        ProgramChannelKey logicalTrackKey;
                        String trackTypeForOtherMessages = currentRemapInfoForOriginalChannel.channelType; // Default to segment type

                        if (message instanceof MetaMessage) {
                            // Global events, not tied to a specific channel's program change
                            logicalTrackKey = new ProgramChannelKey(0, -1, "GLOBAL", 0);
                            MetaMessage metaMessage = (MetaMessage) message;
                            if (metaMessage.getType() == 0x06) {
                                byte[] data = metaMessage.getData();
                                String originalMarkerText = new String(data, StandardCharsets.UTF_8);
                                String newMarkerText = replaceMidiTrackLabels(originalMarkerText);

                                if (!originalMarkerText.equals(newMarkerText)) {
                                    logStatus("    Marker Text Modified: '" + originalMarkerText + "' -> '" + newMarkerText + "' at tick " + event.getTick());
                                    try {
                                        byte[] newMarkerTextBytes = newMarkerText.getBytes(StandardCharsets.UTF_8);
                                        MetaMessage newMetaMessage = new MetaMessage();
                                        // Set the message type back to 0x06 and provide the new bytes
                                        newMetaMessage.setMessage(metaMessage.getType(), newMarkerTextBytes, newMarkerTextBytes.length);
                                        eventToProcess = new MidiEvent(newMetaMessage, event.getTick()); // Update eventToProcess
                                    } catch (InvalidMidiDataException e) {
                                        logError("Error creating new MetaMessage for marker at tick " + event.getTick() + ": " + e.getMessage(), e);
                                        eventProcessingErrors.getAndIncrement();
                                        // Fallback: use original event if modification fails
                                        eventToProcess = event;
                                    }
                                }
                            }

                        } else {
                            logicalTrackKey = new ProgramChannelKey(
                                    currentProgramForOriginalChannel,
                                    originalChannel,
                                    trackTypeForOtherMessages,
                                    segmentIndexForCurrentEvent
                            );
                        }

                        Track targetTrack = programTracks.computeIfAbsent(logicalTrackKey, k -> {
                            Track newTrack = combinedSequence.createTrack();
                            String fullTrackName;
                            if ("GLOBAL".equals(k.type)) {
                                fullTrackName = "Global Events";
                            } else {
                                if (currentRemapInfoForOriginalChannel.originalProgramThatSetThisContext == -1) {
                                    fullTrackName = "Cntrls Ch " + (k.effectiveChannel + 1) + " (Default P" + k.remappedProgramForSegment + ") [" + k.type + "]";
                                } else {
                                    fullTrackName = "Cntrls P" + k.remappedProgramForSegment + " (Orig P" + currentRemapInfoForOriginalChannel.originalProgramThatSetThisContext + ", Ch " + (k.effectiveChannel + 1) + ") [" + k.type + "]";
                                }
                                fullTrackName += " Segment " + k.segmentIndex;
                            }
                            logStatus("      Creating new track for " + fullTrackName);
                            try {
                                fullTrackName = k.effectiveChannel == 9 ? generalMidiDrumKits[k.remappedProgramForSegment] : generalMidiInstrumentNames[k.remappedProgramForSegment];
                                MetaMessage trackNameMessage = new MetaMessage();
                                trackNameMessage.setMessage(0x03, fullTrackName.getBytes(), fullTrackName.length());
                                newTrack.add(new MidiEvent(trackNameMessage, 0));
                            } catch (InvalidMidiDataException e) {
                                logError("Error setting track name for " + fullTrackName + ": " + e.getMessage(), e);
                                eventProcessingErrors.getAndIncrement();
                            }
                            return newTrack;
                        });
                        targetTrack.add(eventToProcess);
                    }
                } else { // Handle non-ShortMessage events (MetaMessage, SysexMessage) if not already caught above
                    ProgramChannelKey globalKey = new ProgramChannelKey(0, -1, "GLOBAL", 0); // A unique key for global events
                    Track targetTrack = programTracks.computeIfAbsent(globalKey, k -> {
                        Track newTrack = combinedSequence.createTrack();
                        String fullTrackName = "Global Events";
                        logStatus("    Creating new track for " + fullTrackName);
                        try {
                            fullTrackName = k.effectiveChannel == 9 ? generalMidiDrumKits[k.remappedProgramForSegment] : generalMidiInstrumentNames[k.remappedProgramForSegment];
                            MetaMessage trackNameMessage = new MetaMessage();
                            trackNameMessage.setMessage(0x03, fullTrackName.getBytes(), fullTrackName.length());
                            newTrack.add(new MidiEvent(trackNameMessage, 0));
                        } catch (InvalidMidiDataException e) {
                            logError("Error setting track name for global track: " + e.getMessage(), e);
                            eventProcessingErrors.getAndIncrement();
                        }
                        return newTrack;
                    });
                    targetTrack.add(eventToProcess);
                }
            }
        }

        if (eventProcessingErrors.get() > 0) {
            logWarning("Encountered " + eventProcessingErrors + " errors during event processing for " + inputMidiFile.getName() + ".");
        }

        // Ensure all output tracks have an End of Track MetaMessage
        for (Track t : combinedSequence.getTracks()) {
            boolean hasEnd = false;
            for (int i = 0; i < t.size(); i++) {
                MidiEvent e = t.get(i);
                if (e.getMessage() instanceof MetaMessage) {
                    MetaMessage mm = (MetaMessage) e.getMessage();
                    if (mm.getType() == 0x2F) { // End of Track meta message
                        hasEnd = true;
                        break;
                    }
                }
            }
            if (!hasEnd) {
                try {
                    MetaMessage end = new MetaMessage();
                    end.setMessage(0x2F, new byte[0], 0); // End of Track message
                    // Add End of Track at the last tick of the track + 1, or tick 1 if empty
                    long lastTick = t.size() > 0 ? t.get(t.size() - 1).getTick() + 1 : 1;
                    t.add(new MidiEvent(end, lastTick));
                } catch (InvalidMidiDataException e) {
                    logError("Failed to add End of Track meta event: " + e.getMessage(), e);
                }
            }
        }

        // Construct output file name and save the sequence
        String outputFileName = inputMidiFile.getName().replace(".mid", "").replace(".midi", "") + "_split_remapped.mid";
        File outputFile = new File(outputDirectory, outputFileName);
        logStatus("  Saving the combined MIDI sequence to: " + outputFile.getAbsolutePath());
        int[] supportedFileTypes = MidiSystem.getMidiFileTypes(combinedSequence);
        if (supportedFileTypes.length > 0) {
            MidiSystem.write(combinedSequence, supportedFileTypes[0], outputFile);
            logStatus("  File generated with " + combinedSequence.getTracks().length + " tracks.");
        } else {
            logError("No supported MIDI file type found for the generated sequence. File not saved.", null);
        }
    }
    /**
     * Replaces specific characters in a MIDI track label.
     * @param trackLabel The original track label string.
     * @return The modified track label string.
     */
    public static String replaceMidiTrackLabels(String trackLabel) {
        if (trackLabel == null || trackLabel.isEmpty()) {
            return trackLabel;
        }
        String replacedLabel = trackLabel.replace("[", "loopStart");
        replacedLabel = replacedLabel.replace("]", "loopEnd");
        return replacedLabel;
    }

    /**
     * Loads remapping rules from the specified CSV file.
     * Clears previous rules before loading.
     * @param csvFile The CSV file containing the remapping rules.
     * @throws IOException If the file cannot be read.
     * @throws NumberFormatException If a number in the CSV is malformed.
     */
    private static void loadRemappingRules(File csvFile) throws IOException, NumberFormatException {
        // Clear all existing rules before loading new ones
        allRemapRules.clear();
        programChangeRemap.clear();
        drumNoteManipulationRulesByOriginalProgram.clear();
        melodicNoteManipulationRulesByOriginalProgram.clear();
        remappedProgramDefaultChannelType.clear();
        originalProgramDefaultChannelType.clear();

        if (!csvFile.exists()) {
            throw new IOException("Remapping CSV file not found at " + csvFile.getAbsolutePath());
        }

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean firstLine = true; // Skip header row
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                String[] parts = line.split(",", -1); // -1 ensures trailing empty strings are included
                if (parts.length < 7) {
                    logWarning("Skipping malformed line in CSV (too few columns, expected 7): " + line);
                    continue;
                }
                try {
                    // Read trackName from CSV but ignore it for rule creation
                    String csvTrackName = parts[0].trim(); // Read to consume the column, but not use it

                    int originalProgram = Integer.parseInt(parts[1].trim());
                    int remappedProgram = Integer.parseInt(parts[2].trim());
                    int originalNote = Integer.parseInt(parts[3].trim());
                    int remappedNoteOrOffset = Integer.parseInt(parts[4].trim());
                    boolean isLayered = Boolean.parseBoolean(parts[5].toLowerCase().trim());
                    String channelType = parts[6].trim().toUpperCase();

                    // Validate MIDI program and note ranges
                    if (originalProgram != -1 && (originalProgram < 0 || originalProgram > 127)) {
                        logWarning("Skipping malformed line in CSV. Invalid OriginalProgramChange (0-127 expected): " + line);
                        continue;
                    }
                    if (remappedProgram != -1 && (remappedProgram < 0 || remappedProgram > 127)) {
                        logWarning("Skipping malformed line in CSV. Invalid RemappedProgramChange (0-127 expected): " + line);
                        continue;
                    }
                    if (originalNote != -1 && originalNote != -999 && (originalNote < 0 || originalNote > 127)) {
                        logWarning("Skipping malformed line in CSV. Invalid OriginalNote (0-127, -1, or -999 expected): " + line);
                        continue;
                    }
                    // Note: remappedNoteOrOffset can be an offset, so range validation is more flexible here.
                    if (originalNote != -999 && remappedNoteOrOffset != -999 && (remappedNoteOrOffset < -127 || remappedNoteOrOffset > 127)) {
                        logWarning("RemappedNoteOrOffset is outside typical range (-127 to 127) for note manipulation: " + line);
                    }

                    // Populate remappedProgramDefaultChannelType map
                    if (remappedProgram >= 0 && remappedProgram <= 127) {
                        String existingDefaultType = remappedProgramDefaultChannelType.get(remappedProgram);
                        if (existingDefaultType == null) {
                            remappedProgramDefaultChannelType.put(remappedProgram, channelType);
                        } else if (existingDefaultType.equals("DRUM") && channelType.equals("MELODIC")) {
                            // If a program was previously marked DRUM, but a new rule marks it MELODIC, prefer MELODIC
                            remappedProgramDefaultChannelType.put(remappedProgram, channelType);
                        }
                    }

                    // Create the unified rule object, passing null for trackName as per user request
                    UnifiedRemapRule rule = new UnifiedRemapRule(null, originalProgram, remappedProgram, originalNote, remappedNoteOrOffset, isLayered, channelType);

                    // Populate originalProgramDefaultChannelType map
                    if (originalProgram >= 0 && originalProgram <= 127) {
                        String existingOriginalType = originalProgramDefaultChannelType.get(originalProgram);
                        if ("DRUM".equals(channelType)) {
                            originalProgramDefaultChannelType.put(originalProgram, "DRUM");
                        } else if ("MELODIC".equals(channelType)) {
                            // If not already marked DRUM, mark as MELODIC
                            if (existingOriginalType == null || !existingOriginalType.equals("DRUM")) {
                                originalProgramDefaultChannelType.put(originalProgram, "MELODIC");
                            }
                        }
                    }

                    // Add note manipulation rules to specific maps based on channel type
                    if (rule.isNoteManipulationRule()) {
                        List<UnifiedRemapRule> targetList = null;
                        if ("DRUM".equals(channelType)) {
                            targetList = drumNoteManipulationRulesByOriginalProgram.computeIfAbsent(originalProgram, k -> new ArrayList<>());
                        } else if ("MELODIC".equals(channelType)) {
                            targetList = melodicNoteManipulationRulesByOriginalProgram.computeIfAbsent(originalProgram, k -> new ArrayList<>());
                        } else {
                            logWarning("Note rule for original program " + originalProgram + " has unknown channel type: '" + channelType + "'. Rule will not be applied for note manipulation. Line: " + line);
                        }
                        if (targetList != null) {
                            if (targetList.contains(rule)) {
                                logWarning("Redundant note manipulation rule found for original program " + originalProgram + ", original note " + originalNote + ". Skipping. Line: " + line);
                            } else {
                                targetList.add(rule);
                            }
                        }
                    }
                    allRemapRules.add(rule); // Add to the comprehensive list of all rules

                } catch (NumberFormatException e) {
                    logWarning("Skipping malformed line in CSV (number format error): " + line + " - " + e.getMessage());
                } catch (ArrayIndexOutOfBoundsException e) {
                    logWarning("Skipping malformed line in CSV (missing expected column): " + line + " - " + e.getMessage());
                }
            }
        }
    }

    /**
     * Main method to run the GUI application.
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new MidiProgramChangeSplitterGUI().setVisible(true);
        });
    }
}
