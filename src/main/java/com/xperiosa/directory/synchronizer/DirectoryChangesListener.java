/*
 * Copyright (c) 2021, Xperiosa <https://github.com/xperiosa/> 
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.xperiosa.directory.synchronizer;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Watch directory for changes, after all file changed events are polled we
 * synchronize the source with target directory
 */
@Slf4j
public class DirectoryChangesListener extends Thread 
{
    // Source directory is where we want to watch for changes
    private final Path sourceDirectory;

    // Target directory is where we want source directory to sync with
    private final Path targetDirectory;

    // The directorySynchronizer(Does the synchronizing between source directory and target directory)
    private final DirectorySynchronizer directorySynchronizer;

    // Watch keys to path map
    private final Map<WatchKey, Path> keys = new HashMap<>();

    /**
     * Constructor
     *
     * @param sourceDirectory, the directory to watch for changes
     * @param targetDirectory, the directory to synchronize with sourceDirectory
     */
    public DirectoryChangesListener(Path sourceDirectory, Path targetDirectory) 
    {
        this.sourceDirectory = sourceDirectory;
        this.targetDirectory = targetDirectory;
        this.directorySynchronizer = new DirectorySynchronizer(sourceDirectory, targetDirectory);
        this.directorySynchronizer.synchronize();
    }

    // Runnable
    @Override
    public void run() 
    {
        this.watchChanges();
    }

    /**
     * Watch source directory for changes and use directorySynchronizer to sync
     * the two directories
     */
    private void watchChanges() 
    {
        try 
        {
            log.debug("Watching directory for changes: {}", sourceDirectory);

            // Create a watch service
            WatchService watchService = FileSystems.getDefault().newWatchService();

            // Register directory and sub-directories to watch service
            registerAll(sourceDirectory, watchService);

            // Poll for events
            for (;;) 
            {
                WatchKey watchKey;
                try 
                {
                    watchKey = watchService.take();
                } 
                catch (InterruptedException ex) 
                {
                    log.error(ex.getLocalizedMessage());
                    return;
                }

                Path dir = keys.get(watchKey);
                if (dir == null) 
                {
                    log.warn("WatchKey not recognized!");
                    continue;
                }

                // Prevent receiving two separate ENTRY_MODIFY events: file modified and timestamp updated
                try 
                {
                    TimeUnit.MILLISECONDS.sleep(500);
                }
                catch (InterruptedException ex) 
                {
                    log.error(ex.getLocalizedMessage());
                }

                // For-each event loop
                watchKey.pollEvents().forEach((event) -> 
                {
                    // Get file name and path from event context
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path fileName = pathEvent.context();
                    Path filePath = dir.resolve(fileName);

                    // Check type of event.
                    WatchEvent.Kind<?> kind = event.kind();

                    // Perform necessary action with the event
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) 
                    {
                        log.debug("A file has been created: {}", fileName);
                    }

                    if (kind == StandardWatchEventKinds.ENTRY_DELETE)
                    {
                        log.debug("A file has been deleted: {}", fileName);
                    }

                    if (kind == StandardWatchEventKinds.ENTRY_MODIFY) 
                    {
                        log.debug("A file has been modified: {}", fileName);
                    }
                });

                // Reset the watch key everytime for continuing to use it for further event polling
                boolean valid = watchKey.reset();
                if (!valid) 
                {
                    keys.remove(watchKey);
                }

                if (keys.isEmpty()) 
                {
                    break;
                }

                // Synchronize directories if all events are polled and not already synchronizing
                if (watchService.poll() == null && !directorySynchronizer.isLock()) 
                {
                    this.directorySynchronizer.synchronize(); // Synchronize directories
                    keys.clear(); // Clear keys
                    watchService.close(); // Close watchService
                    this.watchChanges(); // Watch for changes again
                    break; // Break current loop
                }
            }
        } 
        catch (IOException ex) 
        {
            log.error(ex.getLocalizedMessage());
        }
    }

    /**
     * Register directory and sub-directories to WatchService
     *
     * @param directory, root directory
     * @param watchService
     * @throws IOException
     */
    private void registerAll(Path directory, WatchService watchService) 
    {
        if (!Files.isDirectory(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) 
        {
            return;
        }

        try 
        {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() 
            {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException 
                {
                    WatchKey watchKey = dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                    keys.put(watchKey, dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } 
        catch (IOException ex) 
        {
            log.error(ex.getLocalizedMessage());
        }
    }
}
