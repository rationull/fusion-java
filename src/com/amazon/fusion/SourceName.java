// Copyright (c) 2012-2014 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import java.io.File;

/**
 * Identifies a source of Fusion code, a file, URL, <em>etc.</em>
 * The primary purpose of this class is to display a suitable message fragment
 * for error reporting to users.
 */
public class SourceName
{
    private final String myDisplay;

    /**
     * Creates a {@link SourceName} representing a file at the given path.
     *
     * @param path must not be null or empty.
     *
     * @see #forFile(File)
     */
    public static SourceName forFile(String path)
    {
        if (path.length() == 0) {
            throw new IllegalArgumentException("path must not be empty");
        }
        return new FileSourceName(new File(path));
    }

    /**
     * Creates a {@link SourceName} representing a file.
     * The {@link File}'s absolute path will be displayed.
     *
     * @param path must not be null or empty.
     *
     * @see #forFile(File)
     */
    public static SourceName forFile(File path)
    {
        return new FileSourceName(path);
    }

    /**
     * Creates a {@link SourceName} that will simply display the given text.
     * @param display must not be null.
     */
    public static SourceName forDisplay(String display)
    {
        if (display.length() == 0) {
            throw new IllegalArgumentException("display must not be empty");
        }
        return new SourceName(display);
    }


    private SourceName(String display)
    {
        myDisplay = display;
    }


    /** Returns the human-readable source name, for display in messages. */
    String display()
    {
        return myDisplay;
    }


    File getFile()
    {
        return null;
    }


    ModuleIdentity getModuleIdentity()
    {
        return null;
    }


    @Override
    public String toString()
    {
        return myDisplay;
    }


    boolean equals(SourceName other)
    {
        return (other != null && myDisplay.equals(other.myDisplay));
    }

    @Override
    public boolean equals(Object other)
    {
        return (other instanceof SourceName && equals((SourceName) other));
    }

    private static final int HASH_SEED = SourceName.class.hashCode();

    @Override
    public int hashCode()
    {
        int result = HASH_SEED + myDisplay.hashCode();
        result ^= (result << 29) ^ (result >> 3);
        return result;
    }


    //=========================================================================


    private static class FileSourceName
        extends SourceName
    {
        private final File myFile;

        FileSourceName(File file)
        {
            super(file.getAbsolutePath());
            myFile = file;
        }

        @Override
        File getFile() { return myFile; }
    }


    //=========================================================================


    private static class ModuleSourceName
        extends SourceName
    {
        private final ModuleIdentity myId;
        private final File myFile;

        ModuleSourceName(ModuleIdentity id, File file)
        {
            super(id + " (at file:" + file + ")");
            myId   = id;
            myFile = file;
        }

        @Override
        File getFile() { return myFile; }

        @Override
        ModuleIdentity getModuleIdentity() { return myId; }
    }


    static SourceName forModule(ModuleIdentity id, File sourceFile)
    {
        assert sourceFile != null;
        return new ModuleSourceName(id, sourceFile);
    }
}
