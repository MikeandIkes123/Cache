
import java.io.*;
import java.util.*;

public class cachesim {
    // Cache main parameters
    private static int capacity;
    private static int associativity;
    private static int blockSize;

    private static int numBlocks;
    private static int numSets;
    private static int tagSize;
    private static int indexSize;
    private static int offsetBits;

    private static Frame[][] cache;
    private static String[] memory;

    public static void main(String[] args) throws Exception {
        // Parse command-line arguments
        String traceFile = args[0];
        capacity = Integer.parseInt(args[1]);
        associativity = Integer.parseInt(args[2]);
        blockSize = Integer.parseInt(args[3]);

        // Initialize cache parameters
        numBlocks = capacity * 1024 / blockSize;
        numSets = numBlocks / associativity;
        indexSize = log2(numSets);
        offsetBits = log2(blockSize);
        tagSize = 24 - indexSize - offsetBits;

        // Initialize cache and main memory
        cache = new Frame[numSets][associativity];
        for (int a = 0; a < numSets; a++) {
            for (int way = 0; way < associativity; way++) {
                cache[a][way] = new Frame();
            }
        }
        memory = new String[1 << 24];
        Arrays.fill(memory, "00");

        // Read trace file and simulate cache accesses
        try (BufferedReader reader = new BufferedReader(new FileReader(traceFile))) {
            String line;
            int time = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                String operation = parts[0];
                int address = Integer.parseInt(parts[1].substring(2), 16);
                int accessSize = Integer.parseInt(parts[2]);
                String value = parts.length > 3 ? parts[3] : null; // value needed for store, else not needed
                processMemoryAccess(operation, address, accessSize, value, time);
                time++;
            }
        }
        System.exit(0);
    }

    // Process memory access (load or store)
    private static void processMemoryAccess(String operation, int address, int accessSize, String value, int time)
            throws IndexOutOfBoundsException {
        int index = getIndex(address);
        int tag = getTag(address);

        // LRU implementation will be done here as well but I need to make sure my
        // replacement stuff works
        // hit: a hit happens when the tag bit is equal to the current tag and the valid
        // bit is valid
        Frame[] set = cache[index];
        boolean hit = false;
        for (Frame f : set) {
            if (f.getTag() == tag && f.isValid()) { // all under assumption that its a hit
                hit = true;
                // that means its a hit
                if (operation.equals("store")) {
                    f.write(address, value, accessSize, time);
                    System.out.println("store 0x" + Integer.toHexString(address) + " hit");
                } else {
                    // I assume its a load
                    System.out.println(
                            "load 0x" + Integer.toHexString(address) + " hit " + f.read(address, accessSize, time));
                }
                return;
            }
        }
        if (!hit) {
            // assume its a miss
            // I need to check if store miss or load miss
            // 1: invalid data or 2: capacity is full
            for (Frame e : set) {
                if (!e.isValid()) {
                    fetchFromMemory(address, e); // copy the data from memory into an e block
                    if (operation.equals("store")) {
                        e.write(address, value, accessSize, time);
                        System.out.println("store 0x" + Integer.toHexString(address) + " miss");
                        return;
                        // perform store call
                    } else {
                        // I assume its a load
                        System.out.println("load 0x" + Integer.toHexString(address) + " miss "
                                + e.read(address, accessSize, time));
                        return;
                    }
                }
            }

            // replacement implement
            // when to replace: replace when your capacity is full and kick out LRU block,
            // if dirty bit is true then update value to main memory
            Frame LRU = set[0];
            for (Frame d : set) {
                if (d.lastTimeUsed < LRU.lastTimeUsed) {
                    LRU = d;
                }
            } // I found the frame with the least time, now I have to kick out the specific
              // block within it and check if its dirty so I can write back to memory

            int originalAddress = (LRU.getTag() << (24 - tagSize)) + (index << log2(blockSize));
            // I need to reverse get the address and then use that address to get the
            // replacement to see if its dirty or clean
            if (LRU.isDirty()) {
                writeToMemory(LRU, originalAddress); // original address
            }

            // LRU.write(address, value, accessSize, time); Not sure if this is needed, if I
            // fetched again everything should be set right

            System.out.println(
                    "replacement 0x" + Integer.toHexString(originalAddress) + (LRU.isDirty() ? " dirty" : " clean"));

            fetchFromMemory(address, LRU); // after I wrote the dirty stuff into a address, I need to fetch from

            if (operation.equals("store")) {
                LRU.write(address, value, accessSize, time);
                System.out.println("store 0x" + Integer.toHexString(address) + " miss");
                return;
                // perform store call
            } else {
                // I assume its a load
                System.out.println("load 0x" + Integer.toHexString(address) + " miss "
                        + LRU.read(address, accessSize, time));
                return;
            }
        }

    }

    public static void fetchFromMemory(int address, Frame e) {
        e.valid = true;
        // need to find start of block so I need to clear it by right shifting then left
        // shifting
        int blockRightAddress = address >> log2(blockSize);
        address = blockRightAddress << log2(blockSize);
        for (int i = 0; i < blockSize; i++) {

            e.data[i] = memory[address + i];
        }
        e.tag = getTag(address);
        e.dirty = false;
    }

    public static void writeToMemory(Frame e, int originalAddress) {
        // have tag bits and set index
        // shift tag bits over by << 24 - cache tag bits amount, then add(or) set index
        // >> log2(blockSize)
        for (int i = 0; i < blockSize; i++) {
            memory[originalAddress + i] = e.data[i];
        }
    }

    // Get index bits from address
    private static int getIndex(int address) {
        return (address >> offsetBits) & ((1 << indexSize) - 1);
    }

    // Get tag bits from address
    private static int getTag(int address) {
        return address >> (indexSize + offsetBits);
    }

    // Helper method to calculate log base 2
    private static int log2(int num) {
        int log = 0;
        while (num > 1) {
            num >>= 1;
            log++;
        }
        return log;
    }

    // Class representing a cache block
    private static class Frame {
        private int tag;
        private boolean valid;
        private boolean dirty;
        private String[] data;
        private int lastTimeUsed;

        public Frame() {
            this.tag = 0;
            this.valid = false;
            this.dirty = false;
            this.data = new String[blockSize];
        }

        public int getTag() {
            return tag;
        }

        public boolean isValid() {
            return valid;
        }

        public boolean isDirty() {
            return dirty;
        }

        // these are for writing within and reading within the block, there's no need
        // for memory here as it is only within the cache (I think)
        // Inside the Frame class
        public void write(int address, String value, int accessSize, int time) {
            int offset = address & ((1 << offsetBits) - 1);
            for (int i = 0; i < accessSize; i++) {
                if (offset + i < data.length) {
                    data[offset + i] = value.substring(i * 2, (i + 1) * 2); // Assuming 'value' is a hex string
                }
            }
            dirty = true;
            lastTimeUsed = time;
            // for (String ds : data) {
            // System.out.println(ds);
            // }
        }

        public String read(int address, int accessSize, int time) {
            int offset = address & ((1 << offsetBits) - 1);
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < accessSize; i++) {
                if (offset + i < data.length) {
                    result.append(data[offset + i]);
                }
            }
            lastTimeUsed = time;
            return result.toString();
        }

    }
}
