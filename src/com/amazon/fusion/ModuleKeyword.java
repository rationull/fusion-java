// Copyright (c) 2012 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.ion.util.IonTextUtils.printQuotedSymbol;
import static java.lang.Boolean.TRUE;

import com.amazon.fusion.ModuleInstance.ModuleBinding;
import com.amazon.fusion.Namespace.NsBinding;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @see <a href="http://docs.racket-lang.org/reference/module.html">Racket
 * modules</a>
 */
final class ModuleKeyword
    extends KeywordValue
{
    private final DynamicParameter myCurrentModuleDeclareName;
    private final ModuleNameResolver myModuleNameResolver;

    ModuleKeyword(ModuleNameResolver moduleNameResolver,
                  DynamicParameter currentModuleDeclareName,
                  Namespace kernelNamespace)
    {
        //    "                                                                               |
        super("NAME BODY ...+",
              "Declares a module containing the given body. The NAME must be a symbol.");

        myCurrentModuleDeclareName = currentModuleDeclareName;
        myModuleNameResolver = moduleNameResolver;
    }


    private Binding stopBinding(ModuleInstance kernel,
                                Map<Binding, Object> stops,
                                String name)
    {
        Binding b = kernel.resolveProvidedName(name);
        assert b != null;
        stops.put(b, TRUE);
        return b;
    }


    @Override
    SyntaxValue prepare(Evaluator eval,
                        Environment envOutsideModule,
                        SyntaxSexp source)
        throws SyntaxFailure
    {
        SyntaxChecker check = new SyntaxChecker(getInferredName(), source);

        ModuleInstance kernel = eval.findKernel();

        // TODO precompute this?
        IdentityHashMap<Binding, Object> stops =
            new IdentityHashMap<Binding, Object>();
        Binding defineBinding       = stopBinding(kernel, stops, "define");
        Binding defineSyntaxBinding = stopBinding(kernel, stops, "define_syntax");
        Binding useSyntaxBinding    = stopBinding(kernel, stops, "use");

        SyntaxSymbol moduleNameSymbol = check.requiredSymbol("module name", 1);
        String declaredName = moduleNameSymbol.stringValue();
        // TODO check null/empty

        SyntaxValue initialBindingsStx =
            check.requiredForm("initial module path", 2);

        ModuleRegistry registry = envOutsideModule.namespace().getRegistry();

        ModuleInstance language;
        try
        {
            ModuleIdentity initialBindingsId =
                myModuleNameResolver.resolve(eval, initialBindingsStx);
            language = registry.lookup(initialBindingsId);
        }
        catch (FusionException e)
        {
            String message =
                "Error installing initial bindings: " + e.getMessage();
            throw check.failure(message);
        }

        // The new namespace shares the registry of current-namespace
        Namespace moduleNamespace = new Namespace(registry, language);

        // Pass 1: locate definitions and install dummy bindings

        ArrayList<SyntaxSexp> provideForms = new ArrayList<SyntaxSexp>();
        ArrayList<SyntaxValue> otherForms = new ArrayList<SyntaxValue>();
        ArrayList<Boolean> preparedFormFlags = new ArrayList<Boolean>();

        for (int i = 3; i < source.size(); i++)
        {
            SyntaxValue form = source.get(i);
            form = moduleNamespace.syntaxIntroduce(form);

            SyntaxSexp provide = formIsProvide(form);
            if (provide != null)
            {
                provideForms.add(provide);
            }
            else
            {
                boolean formIsPrepared = false;
                SyntaxValue expanded;
                if (form instanceof SyntaxSexp)
                {
                    expanded =
                        ((SyntaxSexp)form).partialExpand(eval, moduleNamespace,
                                                         stops);
                    if (expanded instanceof SyntaxSexp)
                    {
                        SyntaxSexp sexp = (SyntaxSexp)expanded;
                        Binding binding = firstBinding(sexp);

                        if (binding == defineBinding)
                        {
                            SyntaxSymbol identifier =
                                DefineKeyword.boundIdentifier(eval,
                                                              moduleNamespace,
                                                              sexp);
                            identifier = identifier.stripImmediateEnvWrap(moduleNamespace);
                            moduleNamespace.predefine(identifier);
                        }
                        else if (binding == defineSyntaxBinding)
                        {
                            try
                            {
                                expanded = expanded.prepare(eval,
                                                            moduleNamespace);
                                eval.eval(moduleNamespace, expanded);
                            }
                            catch (FusionException e)
                            {
                                String message = e.getMessage();
                                throw new SyntaxFailure("define_syntax",
                                                        message, form);
                            }
                            // TODO shouldn't need to keep this for later,
                            // but we throw away all this work at the moment
                            // and do it all again during invoke()
//                          expanded = null;
                            formIsPrepared = true;
                        }
                        else if (binding == useSyntaxBinding)
                        {
                            try
                            {
                                expanded = expanded.prepare(eval,
                                                            moduleNamespace);
                                eval.eval(moduleNamespace, expanded);
                            }
                            catch (FusionException e)
                            {
                                String message = e.getMessage();
                                throw new SyntaxFailure("use",
                                                        message, form);
                            }
                            // TODO shouldn't need to keep this for later,
                            // but we throw away all this work at the moment
                            // and do it all again during invoke()
//                          expanded = null;
                            formIsPrepared = true;
                        }
                    }
                }
                else
                {
                    expanded = form;
                }

                if (expanded != null)
                {
                    otherForms.add(expanded);
                    preparedFormFlags.add(formIsPrepared);
                }
            }
        }

        // Pass 2: Expand the expressions. We also rearrange the forms,
        // but that's not really for any functional reason.

        SyntaxValue[] subforms =
            new SyntaxValue[3 + otherForms.size() + provideForms.size()];
        subforms[0] = source.get(0); // module
        subforms[1] = source.get(1); // name
        subforms[2] = source.get(2); // language
        int i = 3;

        Iterator<Boolean> prepared = preparedFormFlags.iterator();
        for (SyntaxValue stx : otherForms)
        {
            if (! prepared.next())
            {
                stx = stx.prepare(eval, moduleNamespace);
            }
            subforms[i++] = stx;
        }

        for (SyntaxSexp stx : provideForms)
        {
            stx = prepareProvide(stx, moduleNamespace);
            subforms[i++] = stx;
        }

        SyntaxSexp result = SyntaxSexp.make(source.getLocation(), subforms);
        return result;
    }


    Binding firstBinding(SyntaxSexp form)
    {
        if (form.size() != 0)
        {
            SyntaxValue first = form.get(0);
            if (first instanceof SyntaxSymbol)
            {
                Binding binding = ((SyntaxSymbol)first).getBinding();
                return binding;
            }
        }
        return null;
    }


    @Override
    FusionValue invoke(Evaluator eval, Environment env, SyntaxSexp expr)
        throws FusionException
    {
        Namespace ns = env.namespace();
        Namespace moduleNamespace = eval.newEmptyNamespace(ns);

        try
        {
            SyntaxValue initialBindingsStx = expr.get(2);
            ModuleIdentity initialBindingsId =
                myModuleNameResolver.resolve(eval, initialBindingsStx);
            moduleNamespace.use(initialBindingsId);
        }
        catch (FusionException e)
        {
            String message =
                "Error installing initial bindings: " + e.getMessage();
            throw new FusionException(message, e);
        }

        ArrayList<SyntaxSexp> provideForms = new ArrayList<SyntaxSexp>();

        for (int i = 3; i < expr.size(); i++)
        {
            SyntaxValue form = expr.get(i);
            SyntaxSexp provide = formIsProvide(form);
            if (provide != null)
            {
                provideForms.add(provide);
            }
            else
            {
                eval.eval(moduleNamespace, form);
            }
        }

        SyntaxSymbol[] providedIdentifiers = providedSymbols(provideForms);

        String declaredName = ((SyntaxSymbol) expr.get(1)).stringValue();
        ModuleIdentity id = determineIdentity(eval, declaredName);
        ModuleInstance module =
            new ModuleInstance(id, moduleNamespace, providedIdentifiers);
        moduleNamespace.getRegistry().register(module);
        return module;
    }

    private ModuleIdentity determineIdentity(Evaluator eval,
                                             String declaredName)
        throws FusionException
    {
        ModuleIdentity id;
        String current = myCurrentModuleDeclareName.asString(eval);
        if (current != null)
        {
            id = ModuleIdentity.intern(current);
        }
        else
        {
            id = ModuleIdentity.intern(declaredName);
        }
        return id;
    }


    //========================================================================


    private SyntaxSexp formIsProvide(SyntaxValue form)
    {
        if (form.getType() == SyntaxValue.Type.SEXP)
        {
            SyntaxSexp sexp = (SyntaxSexp) form;
            if (sexp.size() != 0)
            {
                SyntaxValue first = sexp.get(0);
                if (first.getType() == SyntaxValue.Type.SYMBOL
                    && "provide".equals(((SyntaxSymbol) first).stringValue()))
                {
                    return sexp;
                }
            }
        }
        return null;
    }

    /**
     * @param provideForms
     * @param moduleNamespace
     * @return
     */
    private SyntaxSexp prepareProvide(SyntaxSexp form,
                                      Namespace moduleNamespace)
        throws SyntaxFailure
    {
        int size = form.size();
        SyntaxChecker check = new SyntaxChecker("provide", form);
        for (int i = 1; i < size; i++)
        {
            check.requiredNonEmptySymbol("bound identifier", i);

            SyntaxSymbol identifier = (SyntaxSymbol) form.get(i);
            String publicName = identifier.stringValue();

            String freeName;
            Binding b = identifier.resolve();
            if (b instanceof NsBinding)
            {
                assert moduleNamespace == ((NsBinding)b).getNamespace();
                freeName = ((NsBinding)b).getIdentifier().stringValue();
            }
            else
            {
                freeName = ((ModuleBinding)b).getName();
            }

            if (! publicName.equals(freeName))
            {
                String message =
                    "cannot export binding since symbolic name " +
                    printQuotedSymbol(publicName) +
                    " differs from resolved name " +
                    printQuotedSymbol(freeName);
                throw check.failure(message);
            }
        }

        return form;
    }


    /**
     * @return not null.
     */
    private SyntaxSymbol[] providedSymbols(ArrayList<SyntaxSexp> provideForms)
        throws SyntaxFailure
    {
        ArrayList<SyntaxSymbol> identifiers = new ArrayList<SyntaxSymbol>();

        for (SyntaxSexp form : provideForms)
        {
            int size = form.size();
            for (int i = 1; i < size; i++)
            {
                SyntaxSymbol identifier = (SyntaxSymbol) form.get(i);
                identifiers.add(identifier);
            }
        }

        return identifiers.toArray(new SyntaxSymbol[identifiers.size()]);
    }

}
