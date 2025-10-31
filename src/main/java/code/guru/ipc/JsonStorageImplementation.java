/* code-guru-scanner-worker
 * Copyright (C) 2025 Srijan Singh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details:
 *     https://www.gnu.org/licenses/gpl-3.0.txt
 */
// code/guru/ipc/JsonStorageImplementation.java
package code.guru.ipc;

import code.guru.chunks.ChunkData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Implementation of IngestionIPC that stores chunks as a JSON file
 * in the project root directory.
 */
public class JsonStorageImplementation implements IngestionIPC {

    private static final Logger log = Logger.getInstance(JsonStorageImplementation.class);
    private static final String FILE_NAME = "chunks_data.json";

    private final Project project;
    private final ObjectMapper objectMapper;

    public JsonStorageImplementation(@NotNull Project project) {
        this.project = project;
        this.objectMapper = new ObjectMapper();
        // Make the JSON output human-readable (pretty-printed)
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Serializes the list of ChunkData into a JSON file in the project directory.
     *
     * @param chunks The list of chunks to store.
     */
    @Override
    public String sendChunks(List<ChunkData> chunks) {
        if (project.getBasePath() == null) {
            log.error("Project base path is null, cannot save chunks.");
            return "Project base path is null, cannot save chunks.";
        }

        File projectDir = new File(project.getBasePath());
        File outputFile = new File(projectDir, FILE_NAME);

        log.info("Attempting to save %d chunks to: %s".formatted(chunks.size(), outputFile.getAbsolutePath()));

        try {
            // Write the list of ChunkData directly to the file as JSON
            objectMapper.writeValue(outputFile, chunks);
            log.info("Successfully saved chunks to " + outputFile.getName());
            return "Successfully saved chunks to " + outputFile.getName();
        } catch (IOException e) {
            log.error("Failed to save chunks to JSON file: " + outputFile.getAbsolutePath(), e);
        }
        return "Failed to save chunks to JSON file: " + outputFile.getAbsolutePath();
    }
}