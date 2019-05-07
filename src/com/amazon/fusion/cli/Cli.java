// Copyright (c) 2012-2019 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion.cli;

import java.io.PrintStream;

/**
 * The Fusion command-line interface, just here to provide a {@link #main} method.
 */
public final class Cli
{
    private Cli() {}


    public static void main(String[] args)
    {
        PrintStream stdout = System.out;
        PrintStream stderr = System.err;

        int errorCode;

        try
        {
            CommandFactory cf = new CommandFactory(stdout, stderr);
            errorCode = cf.executeCommandLine(args);
        }
        catch (Throwable e)
        {
            errorCode = 1;
            e.printStackTrace(stderr);
        }

        stdout.flush();
        stderr.flush();

        if (errorCode != 0)
        {
            System.exit(errorCode);
        }
    }
}
