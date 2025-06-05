# CSV-MIDI-Remapper
### A simple Java utility for splitting and remapping MIDI files based on CSV file rules.

## How it works:
- You create a simple Comma Separated Values (.csv) file based on program change numbers within a MIDI that you want to remap.
- You run the java GUI class directly, select MIDI file(s) relevant to the CSV mapping you just crafted.
- You select an output folder for the remapped MIDI file(s) to be written out to.
- You select the CSV file you just created with your mappings, then process the MIDI file(s). All selected MIDI files are then proessed based on your CSV rules and adjusted + split by program changes so that each and every track in the sequence corresponds with a specific instrument.

### CSV Structure:
- Column 1, Track Name (Optional, for organization)
- Column 2, Original Program Change # (Use values over 127 to indicate a different bank e.g. Bank LSB 1 for program 128).
- Column 3, Program Change # Remapped (This is the program change you want to adjust the original one to).
- Column 4, Original Note # (Use -1 to indicate ALL notes, -999 to indicate NO notes only the program change)
- Column 5, Remapped Note # (Use -999 to indicate program change only, or if original note # is -1, this shifts all notes by specific amount indicated here).
- Column 6, Layered Notes? (TRUE or FALSE, indicates that the remapped notes should be added to the original notes or not).
- Column 7, DRUM or MELODIC track (Important indicator for non-drum or non-melodic segments that are incorrectly placed).

### In the event that you need to remap instruments AND notes simultaneously, make sure you call the program change event in addition to the note remapping event. Pay close attention to ordering of rules, as they *do* affect the output.
### Example (Derived from Pokémon HeartGold/SoulSilver's Basic Bank Remapping):
- Reverse Cymbal,39,119,-999,-999,FALSE,MELODIC <- this first sets the program from 39 to 119.
- Reverse Cymbal,39,119,31,55,FALSE,MELODIC <- this rule gets read next, now remaps all instances of note 31 to note 55.

## Why?
#### I created this simple utility to help automate many of the MIDI correction functions that are done manually, potentially reducing risk of human error.
### This program is not perfect, however, for more complex MIDI files, you may need to review tracks after remapping, if it sounds off.