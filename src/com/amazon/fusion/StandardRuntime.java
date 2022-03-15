// Copyright (c) 2012-2022 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.BindingDoc.COLLECT_DOCS_MARK;
import static com.amazon.fusion.ModuleIdentity.forAbsolutePath;
import static com.amazon.fusion.ModuleIdentity.isValidAbsoluteModulePath;
import static java.lang.Boolean.TRUE;
import com.amazon.ion.IonCatalog;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.ValueFactory;
import com.amazon.ion.system.IonSystemBuilder;


final class StandardRuntime
    implements FusionRuntime
{
    private final GlobalState      myGlobalState;
    private final ModuleRegistry   myRegistry;
    private final String           myDefaultLanguage;
    private final StandardTopLevel myTopLevel;


    StandardRuntime(FusionRuntimeBuilder builder)
        throws FusionInterrupt
    {
        IonSystem ionSystem =
            IonSystemBuilder.standard()
                .withCatalog(builder.getDefaultIonCatalog())
                .build();
        myRegistry = new ModuleRegistry();

        myDefaultLanguage = builder.getDefaultLanguage();

        try
        {
            // This is the bootstrap top-level namespace, which starts out
            // empty.  It becomes the initial value of current_namespace
            // during global initialization.
            Namespace topNs = new TopLevelNamespace(myRegistry);

            myGlobalState =
                GlobalState.initialize(ionSystem, builder, myRegistry, topNs);

            if (builder.isDocumenting())
            {
                myTopLevel =
                    new StandardTopLevel(myGlobalState, topNs, myDefaultLanguage,
                                         COLLECT_DOCS_MARK, TRUE);
            }
            else
            {
                myTopLevel =
                    new StandardTopLevel(myGlobalState, topNs, myDefaultLanguage);
            }
        }
        catch (FusionException e)
        {
            throw new RuntimeException("Should not happen", e);
        }
    }


    // Not public
    GlobalState getGlobalState()
    {
        return myGlobalState;
    }

    // Not public
    ModuleRegistry getDefaultRegistry()
    {
        return myRegistry;
    }

    @Override
    public String getDefaultLanguage()
    {
        return myDefaultLanguage;
    }

    @Override
    public IonCatalog getDefaultIonCatalog()
    {
        return myGlobalState.myIonSystem.getCatalog();
    }


    //========================================================================


    @Override
    public StandardTopLevel getDefaultTopLevel()
        throws FusionException
    {
        return myTopLevel;
    }


    @Override
    public TopLevel makeTopLevel(String initialModulePath)
        throws FusionException
    {
        if (! isValidAbsoluteModulePath(initialModulePath))
        {
            String message =
                "Not a valid absolute module path: " + initialModulePath;
            throw new IllegalArgumentException(message);
        }

        try
        {
            return new StandardTopLevel(myGlobalState, myRegistry,
                                        initialModulePath);
        }
        catch (FusionInterrupt e)
        {
            throw new FusionInterruptedException(e);
        }
    }


    @Override
    public TopLevel makeTopLevel()
        throws FusionException
    {
        return makeTopLevel(getDefaultLanguage());
    }


    @Override
    public ModuleBuilder makeModuleBuilder(String absoluteModulePath)
    {
        if (! isValidAbsoluteModulePath(absoluteModulePath))
        {
            String message =
                "Invalid absolute module path: " + absoluteModulePath;
            throw new IllegalArgumentException(message);
        }

        ModuleIdentity id = forAbsolutePath(absoluteModulePath);
        return new ModuleBuilderImpl(myGlobalState.myModuleNameResolver,
                                     myRegistry,
                                     id);
    }


    //========================================================================


    @Override
    public IonValue ionize(Object fusionValue, ValueFactory factory)
        throws FusionException
    {
        return FusionValue.copyToIonValue(fusionValue, factory);
    }


    @Override
    public IonValue ionizeMaybe(Object fusionValue, ValueFactory factory)
        throws FusionException
    {
        return FusionValue.copyToIonValueMaybe(fusionValue, factory);
    }
}
