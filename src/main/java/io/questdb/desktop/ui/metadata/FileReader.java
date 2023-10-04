package io.questdb.desktop.ui.metadata;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static java.nio.channels.FileChannel.MapMode;

public class FileReader {
    private byte[] lineBuffer = new byte[512];


    public List<String> readLines(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r"); FileChannel channel = raf.getChannel()) {
            long fileSize = raf.length();
            MappedByteBuffer mappedBuffer = channel.map(MapMode.READ_ONLY, 0L, fileSize);
            int lineStartOffset = 0;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < mappedBuffer.limit(); i++) {
                if (mappedBuffer.get(i) == '\n') {
                    if (lineStartOffset != i) {
                        int lineLength = i - lineStartOffset;
                        if (lineLength > lineBuffer.length) {
                            lineBuffer = new byte[(int) Math.ceil(lineLength * 1.5F)];
                        }
                        mappedBuffer.position(lineStartOffset);
                        mappedBuffer.get(lineBuffer, 0, lineLength);
                        if (lineBuffer[lineLength - 1] == '\r') {
                            lineLength--;
                        }
                        lines.add(new String(lineBuffer, 0, lineLength, StandardCharsets.UTF_8));
                    }
                    lineStartOffset = i + 1;
                }
            }
            return lines;
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("cannot access file: " + file, e);
        }
    }
}
