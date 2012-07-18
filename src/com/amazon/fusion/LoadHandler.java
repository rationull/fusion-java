// Copyright (c) 2012 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import com.amazon.ion.IonSexp;
import com.amazon.ion.IonSymbol;
import com.amazon.ion.IonValue;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 *
 */
final class LoadHandler
{
    private final DynamicParameter myCurrentLoadRelativeDirectory;
    private final DynamicParameter myCurrentDirectory;

    LoadHandler(DynamicParameter currentLoadRelativeDirectory,
                DynamicParameter currentDirectory)
    {
        myCurrentLoadRelativeDirectory = currentLoadRelativeDirectory;
        myCurrentDirectory = currentDirectory;
    }


    /**
     * Resolve a relative path against the {@code current_directory} param.
     * If file is absolute it is returned as-is.
     */
    private File resolvePath(Evaluator eval, File file)
        throws FusionException
    {
        if (! file.isAbsolute())
        {
            String cdPath = myCurrentDirectory.asString(eval);
            File cdFile = new File(cdPath);
            file = new File(cdFile, file.getPath());
        }
        return file;
    }

    /**
     * Resolve a relative path against the {@code current_directory} param.
     * If the path is absolute it is returned as-is.
     */
    private File resolvePath(Evaluator eval, String path)
        throws FusionException
    {
        File file = new File(path);
        return resolvePath(eval, file);
    }


    FusionValue loadTopLevel(Evaluator eval, Namespace namespace, String path)
        throws FusionException
    {
        File file = resolvePath(eval, path);
        try
        {
            FileInputStream in = new FileInputStream(file);
            try
            {
                FusionValue result = null;

                Iterator<IonValue> i = eval.getSystem().iterate(in);
                while (i.hasNext())
                {
                    result = null;  // Don't hold onto garbage
                    IonValue fileExpr = i.next();
                    result = eval.eval(namespace, fileExpr);
                    // TODO tail call handling
                }

                return result;
            }
            finally
            {
                in.close();
            }
        }
        catch (IOException e)
        {
            throw new FusionException(e);
        }
    }


    private IonValue readSingleTopLevelValue(Evaluator eval, ModuleIdentity id)
        throws FusionException
    {
        try
        {
            InputStream in = id.open();
            try
            {
                Iterator<IonValue> i = eval.getSystem().iterate(in);
                if (! i.hasNext())
                {
                    String message =
                        "Module file has no top-level forms: " + id;
                   throw new FusionException(message);
                }

                IonValue firstTopLevel = i.next();
                if (i.hasNext())
                {
                    String message =
                        "Module file has more than one top-level form: " +
                        id;
                    throw new FusionException(message);
                }

                return firstTopLevel;
            }
            finally
            {
                in.close();
            }
        }
        catch (IOException e)
        {
            throw new FusionException(e);
        }
    }


    private IonSexp readModuleDeclaration(Evaluator eval, ModuleIdentity id)
        throws FusionException
    {
        IonValue topLevel = readSingleTopLevelValue(eval, id);
        try {
            IonSexp moduleDeclaration = (IonSexp) topLevel;
            if (moduleDeclaration.size() > 1)
            {
                IonSymbol moduleSym = (IonSymbol) moduleDeclaration.get(0);
                if ("module".equals(moduleSym.stringValue()))
                {
                    return moduleDeclaration;
                }
            }
        }
        catch (ClassCastException e) { /* fall through */ }

        String message = "Top-level form isn't (module ...): " + id;
        throw new FusionException(message);
    }


    /**
     * @param path may be relative, in which case it is resolved relative to
     * the {@code current_directory} parameter.
     */
    ModuleInstance loadModule(Evaluator eval, ModuleIdentity id)
        throws FusionException
    {
        try
        {
            IonValue moduleDeclaration = readModuleDeclaration(eval, id);

            Evaluator bodyEval = eval;
            String dirPath = id.parentDirectory();
            if (dirPath != null)
            {
                bodyEval =
                    eval.markedContinuation(myCurrentLoadRelativeDirectory,
                                            eval.newString(dirPath));
            }

            // TODO Do we need an Evaluator with no continuation marks?
            // This namespace ensures correct binding for 'module'

            ModuleRegistry reg = eval.getModuleRegistry();
            Namespace namespace = eval.newModuleNamespace(reg);
            FusionValue result = bodyEval.eval(namespace, moduleDeclaration);
            // TODO tail call handling
            return (ModuleInstance) result;
        }
        catch (FusionException e)
        {
            String message =
                "Failure loading module from " + id.identify() +
                ": " + e.getMessage();
            throw new FusionException(message, e);
        }
    }
}
