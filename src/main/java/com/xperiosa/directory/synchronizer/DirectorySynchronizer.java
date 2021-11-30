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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

/**
 * Synchronize two directories
 */
@Slf4j
public class DirectorySynchronizer 
{
    private final Path sourceDirectory;
    private final Path targetDirectory;

    @Getter(AccessLevel.PUBLIC)
    private volatile boolean lock;

    public DirectorySynchronizer(Path sourceDirectory, Path targetDirectory) 
    {
        this.sourceDirectory = sourceDirectory;
        this.targetDirectory = targetDirectory;
    }

    /**
     * Synchronize source directory with target directory
     *
     */
    public void synchronize() 
    {
        log.debug("Synchronizing..");
        this.lock = true;
        try 
        {
            // Compare directories and files from sourceDirectory to targetDirectory
            Files.walkFileTree(sourceDirectory, new SimpleFileVisitor<Path>() 
            {
                @Override
                public FileVisitResult preVisitDirectory(Path sourceDir, BasicFileAttributes attrs) throws IOException 
                {
                    FileVisitResult result = super.preVisitDirectory(sourceDirectory, attrs);

                    // get the relative sourceDir path
                    Path relativize = sourceDirectory.relativize(sourceDir);

                    // construct the path for the counterpart directory
                    Path targetDir = targetDirectory.resolve(relativize);

                    log.debug("Comparing directory: {} -> {}", sourceDir, targetDir);

                    // Copy directory from sourceDirectory to targetDirectory if it doesn't exist in target directory
                    if (!targetDir.toFile().exists()) 
                    {
                        FileUtils.copyDirectory(sourceDir.toFile(), targetDir.toFile());
                        log.debug("Copying directories: {} -> {}", sourceDir, targetDir);
                    }
                    return result;
                }

                @Override
                public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attrs) throws IOException 
                {
                    FileVisitResult result = super.visitFile(sourceFile, attrs);

                    // get the relative file name from sourceDirectory path
                    Path relativize = sourceDirectory.relativize(sourceFile);

                    // construct the path for the counterpart file in targetDirectory path
                    Path targetFile = targetDirectory.resolve(relativize);

                    log.debug("comparing file: {} -> {}", sourceFile, targetFile);

                    // Copy file from sourceDirectory to targetDirectory if it doesn't exist in target directory
                    if (!targetFile.toFile().exists()) 
                    {
                        FileUtils.copyFile(sourceFile.toFile(), targetFile.toFile(), StandardCopyOption.COPY_ATTRIBUTES);
                        log.debug("Copying file: {} -> {}", sourceFile, targetFile);
                    }

                    byte[] sourceFileBytes = Files.readAllBytes(sourceFile);
                    byte[] targetFileBytes = Files.readAllBytes(targetFile);
                    if (!Arrays.equals(sourceFileBytes, targetFileBytes)) 
                    {
                        log.debug("File mismatch: {} != {}", sourceFile, targetFile);
                        FileUtils.copyFile(sourceFile.toFile(), targetFile.toFile(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    return result;
                }

                @Override
                public FileVisitResult visitFileFailed(Path sourceFile, IOException ex) 
                {
                    log.error(ex.getLocalizedMessage());
                    return FileVisitResult.CONTINUE;
                }
            });

            // Delete directories and files from targetDirectory if it doesn't exist in sourceDirectory
            Files.walkFileTree(targetDirectory, new SimpleFileVisitor<Path>() 
            {
                @Override
                public FileVisitResult preVisitDirectory(Path targetDir, BasicFileAttributes attrs) throws IOException 
                {
                    FileVisitResult result = super.preVisitDirectory(targetDirectory, attrs);

                    // get the relative targetDir path
                    Path relativize = targetDirectory.relativize(targetDir);

                    // construct the path for the counterpart directory
                    Path sourceDir = sourceDirectory.resolve(relativize);

                    if (!sourceDir.toFile().exists()) 
                    {
                        FileUtils.deleteDirectory(targetDir.toFile());
                        log.debug("Deleted directory: {}", targetDir);
                    }
                    return result;
                }

                @Override
                public FileVisitResult visitFile(Path targetFile, BasicFileAttributes attrs) throws IOException 
                {
                    FileVisitResult result = super.visitFile(targetFile, attrs);

                    // get the relative file name from sourceDirectory path
                    Path relativize = targetDirectory.relativize(targetFile);

                    // construct the path for the counterpart file in targetDirectory path
                    Path sourceFile = sourceDirectory.resolve(relativize);

                    if (!sourceFile.toFile().exists()) 
                    {
                        FileUtils.delete(targetFile.toFile());
                        log.debug("Deleted file: {}", targetFile);
                    }
                    return result;
                }

                @Override
                public FileVisitResult visitFileFailed(Path sourceFile, IOException ex) 
                {
                    log.error(ex.getLocalizedMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } 
        catch (IOException ex) 
        {
            Logger.getLogger(DirectorySynchronizer.class.getName()).log(Level.SEVERE, null, ex);
        }
        this.lock = false;
    }

}
