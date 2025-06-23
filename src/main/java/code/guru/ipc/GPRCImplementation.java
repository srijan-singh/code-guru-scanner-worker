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

import code.guru.chunks.ChunkData;
import com.intellij.openapi.diagnostic.Logger;

import java.util.List;

/**
 * Google Remote Procedure Call for Ingestion
 */
public class GPRCImplementation implements IngestionIPC {

    private static final Logger log = Logger.getInstance(GPRCImplementation.class);

    @Override
    public void sendChunks(List<ChunkData> chunks) {
        log.info("TODO: Not implemented yet!");

        for(ChunkData chunk : chunks) {
            log.info(chunk.toString());
        }
    }
}
