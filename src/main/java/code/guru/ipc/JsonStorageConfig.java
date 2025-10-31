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
package code.guru.ipc;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for JSON storage settings.
 * This class is a persistent service that stores JSON storage configuration.
 */
@Data
@State(
    name = "JsonStorageConfig",
    storages = {@Storage("code-guru-json-storage.xml")}
)
public class JsonStorageConfig implements PersistentStateComponent<JsonStorageConfig> {

    /**
     * The base directory where JSON files will be stored
     */
    private String baseDirectory = System.getProperty("user.home") + "/.code-guru/chunks";

    /**
     * Check if module directories should be created
     */
    private boolean createModuleDirectories = true;

    /**
     * JSON should be pretty-printed
     */
    private boolean prettyPrint = true;

    /**
     *  Timestamps should be appended to filenames
     */
    private boolean appendTimestamp = false;

    /**
     * Get the file extension for JSON files
     */
    private String fileExtension = ".json";

        /**
     * Get the base directory as a Path object
     * @return The base directory as a Path
     */
    public Path getBaseDirectoryPath() {
        return Paths.get(baseDirectory);
    }

    @Nullable
    @Override
    public JsonStorageConfig getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull JsonStorageConfig state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
