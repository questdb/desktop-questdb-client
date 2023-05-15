/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2023 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.desktop.metadata;

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
        try (
                RandomAccessFile raf = new RandomAccessFile(file, "r");
                FileChannel channel = raf.getChannel()
        ) {
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
