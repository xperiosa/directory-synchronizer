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

import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main 
{
    /**
     * Main method
     * 
     * @param args
     * @throws IOException 
     */
    public static void main(String args[]) throws IOException 
    {
        if (args.length != 2) 
        {
            log.debug("Example: java -jar synchronizer.jar /path/to/sourceDir /path/to/targetDir");
            return;
        }
         
        File sourceDirectory = new File(args[0]);
        File targetDirectory = new File(args[1]);
        
        if (!sourceDirectory.exists()) 
        {
            log.error("Source directory does not exist!");
            return;
        }
        
        if (!targetDirectory.exists()) 
        {
            log.error("Target directory does not exist!");
            return;
        }
        
        DirectoryChangesListener directoryChangesListener = new DirectoryChangesListener(sourceDirectory.toPath(), targetDirectory.toPath());
        directoryChangesListener.setName("directory-changes-listener");
        directoryChangesListener.start();
        
    }
}
