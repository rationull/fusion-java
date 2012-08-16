// Copyright (c) 2012 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.ModuleIdentity.intern;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonType;


/**
 * The kernel bindings for Fusion, the bare minimum that needs to be done
 * using Java code.  This module isn't for end users.
 */
final class KernelModule
    extends ModuleInstance
{
    static final String NAME = "#%kernel";
    static final ModuleIdentity IDENTITY = intern(NAME);

    private final LoadHandler  myLoadHandler;
    private final KeywordValue myModuleKeyword;
    private final UseKeyword   myUseKeyword;


    KernelModule(IonSystem system, FusionRuntimeBuilder builder, Namespace ns)
        throws FusionException
    {
        super(IDENTITY, ns);
        inferName(NAME);

        FusionValue userDir =
            new DomValue(system.newString(builder.getInitialCurrentDirectory().toString()));
        DynamicParameter currentDirectory =
            new DynamicParameter(userDir);
        DynamicParameter currentLoadRelativeDirectory =
            new DynamicParameter(UNDEF);
        DynamicParameter currentModuleDeclareName =
            new DynamicParameter(UNDEF);
        myLoadHandler =
            new LoadHandler(currentLoadRelativeDirectory, currentDirectory);
        ModuleNameResolver resolver =
            new ModuleNameResolver(myLoadHandler,
                                   currentLoadRelativeDirectory,
                                   currentDirectory,
                                   currentModuleDeclareName,
                                   builder.buildModuleRepositories());

        // These must be bound before 'module' since we need the bindings
        // for the partial-expansion stop-list.
        ns.bind("define", new DefineKeyword());
        ns.bind("define_syntax", new DefineSyntaxKeyword());

        myModuleKeyword =
            new ModuleKeyword(resolver, currentModuleDeclareName, ns);
        myUseKeyword = new UseKeyword(resolver);
        EvalFileKeyword evalFile =
            new EvalFileKeyword(myLoadHandler);

        ns.bind("current_directory", currentDirectory);
        ns.bind("eval_file", evalFile);
        ns.bind("java_new", new JavaNewProc());
        ns.bind("module", myModuleKeyword);
        ns.bind("undef", FusionValue.UNDEF);
        ns.bind("empty_stream", FusionValue.EMPTY_STREAM);
        ns.bind("use", myUseKeyword);

        for (IonType t : IonType.values())
        {
            if (t != IonType.NULL && t != IonType.DATAGRAM)
            {
                String name = "is_" + t.name().toLowerCase();
                ns.bind(name, new IonTypeCheckingProc(t));
            }
        }
    }


    LoadHandler getLoadHandler()
    {
        return myLoadHandler;
    }

    KeywordValue getModuleKeyword()
    {
        return myModuleKeyword;
    }

    UseKeyword getUseKeyword()
    {
        return myUseKeyword;
    }
}
