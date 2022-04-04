// Copyright (c) 2012-2022 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import com.amazon.fusion.ModuleForm.CompiledModule;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks loaded and instantiated modules available for use by namespaces.
 *
 * "Each namespace has a module registry that maps module names to module
 * declarations. This registry is shared by all phase levels, and it applies
 * both to parsing and to running compiled code."
 *
 * This class must be thread-safe.
 *
 * See https://docs.racket-lang.org/reference/syntax-model.html#%28part._namespace-model%29
 */
final class ModuleRegistry
{
    private final Map<ModuleIdentity,CompiledModule> myDeclarations =
        new HashMap<>();

    // TODO FUSION-32 this should separate module instances by phase.
    // "Even though module declarations are shared for all phase levels, module
    // instances are distinct for each phase."
    private final Map<ModuleIdentity,ModuleInstance> myModules =
        new HashMap<>();


    /**
     * Finds a module instance that's already in this registry.
     *
     * @param identity the desired module
     *
     * @return null if the module doesn't exist in this registry.
     */
    synchronized ModuleInstance lookup(ModuleIdentity identity)
    {
        return myModules.get(identity);
    }

    ModuleInstance lookup(String absolutePath)
    {
        return lookup(ModuleIdentity.forAbsolutePath(absolutePath));
    }

    /**
     * Determines whether a module has been declared or instantiated in this
     * registry.
     */
    synchronized boolean isLoaded(ModuleIdentity identity)
    {
        return myDeclarations.containsKey(identity)
            || myModules.containsKey(identity);
    }


    /**
     * @return null if the module has not been instantiated and has not been
     * declared.
     */
    synchronized ModuleInstance instantiate(Evaluator eval,
                                            ModuleIdentity identity)
        throws FusionException
    {
        ModuleInstance instance = myModules.get(identity);
        if (instance == null)
        {
            CompiledModule decl = myDeclarations.get(identity);
            if (decl != null)
            {
                instance = decl.instantiate(eval);
                myModules.put(identity, instance);
            }
        }
        return instance;
    }


    /**
     * @throws ContractException if a module is already declared with the
     * same {@link ModuleIdentity}.
     */
    synchronized void declare(ModuleNameResolver resolver,
                              ModuleIdentity id,
                              CompiledModule decl)
        throws FusionException, ContractException
    {
        CompiledModule old = myDeclarations.put(id, decl);
        if (old != null && old != decl)
        {
            myDeclarations.put(id, old);
            String message =
                "Registry already has a module declared with identity " + id;
            throw new ContractException(message);
        }

        // "The current module name resolver is called with two arguments by
        // `namespace-attach-module` or `namespace-attach-module-declaration`
        // to notify the resolver that a module declaration was attached to the
        // current namespace (and should not be loaded in the future for the
        // namespace’s module registry).

        resolver.registerDeclaredModule(this, id);
    }


    /**
     * @throws ContractException if a different module instance is already
     * registered with the same {@link ModuleIdentity}.
     */
    synchronized void register(ModuleNameResolver resolver,
                               ModuleInstance instance,
                               CompiledModule decl)
        throws FusionException, ContractException
    {
        ModuleIdentity id = instance.getIdentity();

        ModuleInstance old = myModules.put(id, instance);
        if (old != null && old != instance)
        {
            myModules.put(id, old);
            String message =
                "Registry already has a module with identity " + id;
            throw new ContractException(message);
        }
        declare(resolver, instance.getIdentity(), decl);
    }
}
