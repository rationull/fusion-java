// Copyright (c) 2012-2013 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.Syntax.datumToSyntax;
import static com.amazon.ion.util.IonTextUtils.printQuotedSymbol;
import com.amazon.fusion.Namespace.NsBinding;
import java.util.ArrayList;


final class ProvideForm
    extends SyntacticForm
{
    ProvideForm()
    {
        super("provide-clause", "doc");
    }


    /**
     * Note that we expand `provide` after expanding the rest of the module.
     */
    @Override
    SyntaxValue expand(Expander expander, Environment env, SyntaxSexp form)
        throws FusionException
    {
        SyntaxChecker check = new SyntaxChecker("provide", form);

        if (!expander.isModuleContext())
        {
            throw check.failure("May only appear in a module context");
        }


        Namespace moduleNamespace = (Namespace) env;
        Evaluator eval = expander.getEvaluator();

        ArrayList<SyntaxValue> expanded = new ArrayList<>(form.size());
        expanded.add(form.get(0));

        for (int i = 1; i < form.size(); i++)
        {
            SyntaxValue spec = form.get(i);
            switch (spec.getType())
            {
                case SYMBOL:
                {
                    check.requiredIdentifier("bound identifier", i);

                    expandProvideId(moduleNamespace, (SyntaxSymbol) spec,
                                    check, expanded);
                    break;
                }
                case SEXP:
                {
                    expandProvideSexp(eval, moduleNamespace,
                                      (SyntaxSexp) spec,
                                      check.subformSexp("provide-spec", i),
                                      expanded);
                    break;
                }
                default:
                {
                    throw check.failure("expected provide-spec", form.get(i));
                }
            }
        }

        SyntaxValue[] children =
            expanded.toArray(new SyntaxValue[expanded.size()]);
        return SyntaxSexp.make(eval, children, form.getAnnotations(), form.getLocation());
    }

    private void expandProvideId(Namespace moduleNamespace,
                                 SyntaxSymbol identifier,
                                 SyntaxChecker check,
                                 ArrayList<SyntaxValue> expanded)
        throws FusionException
    {
        String publicName = identifier.stringValue();

        // TODO FUSION-139 id.resolve works when the id has the ns in context
        // It doesn't work when the ns isn't in context because the
        // provided binding was macro-introduced.

        Binding b = identifier.resolve();
        // Binding local = moduleNamespace.localResolve(identifier);
        // localResolve isn't right either since it doesn't find imports

        if (b instanceof FreeBinding)
        {
            String message =
                "cannot export " + printQuotedSymbol(publicName) +
                " since it has no definition.";
            throw check.failure(message);
        }

        String freeName = b.getName();
        if (! publicName.equals(freeName))
        {
            String message =
                "cannot export binding since symbolic name " +
                printQuotedSymbol(publicName) +
                " differs from resolved name " +
                printQuotedSymbol(freeName);
            throw check.failure(message);
        }

        expanded.add(identifier);
    }

    private void expandProvideSexp(Evaluator eval,
                                   Namespace moduleNamespace,
                                   SyntaxSexp specForm,
                                   SyntaxChecker check,
                                   ArrayList<SyntaxValue> expanded)
        throws FusionException
    {
        Binding b = specForm.firstBinding();
        if (b == eval.getGlobalState().myKernelAllDefinedOutBinding)
        {
            check.arityExact(1);

            SyntaxSymbol tag = (SyntaxSymbol) specForm.get(0);

            // Filter by lexical context: we shouldn't export identifiers
            // introduced by macros unless this form was also introduced
            // at the same time.
            for (NsBinding binding : moduleNamespace.getBindings())
            {
                // TODO the datum->syntax context should be the whole sexp
                // form `(all_defined_out)` not just `all_defined_out` but
                // we don't currently retain context on SyntaxSexp after
                // it has been pushed down to children.
                SyntaxSymbol localized = (SyntaxSymbol)
                    datumToSyntax(eval,
                                  SyntaxSymbol.make(binding.getName()),
                                  tag);
                localized = localized.copyAndResolveTop();
                Binding localBinding = moduleNamespace.localResolve(localized);
                if (localBinding != null && binding.sameTarget(localBinding))
                {
                    localized = localized.copyReplacingBinding(binding);
                    expanded.add(localized);
                }

                // TODO FUSION-136 handle rename-transformers per Racket
            }
        }
        else
        {
            throw check.failure("invalid provide-spec");
        }
    }


    //========================================================================


    @Override
    CompiledForm compile(Evaluator eval, Environment env, SyntaxSexp stx)
        throws FusionException
    {
        throw new IllegalStateException("Provide shouldn't be compiled");
    }


    //========================================================================


    /**
     * This class primarily provides syntax to bind to all_defined_out so that
     * {@link ProvideForm} can compare bindings, not symbolic names.  This is
     * as specified by Racket.
     */
    static final class AllDefinedOutForm
        extends SyntacticForm
    {
        AllDefinedOutForm()
        {
            super("", "doc");
        }

        @Override
        SyntaxValue expand(Expander expander, Environment env, SyntaxSexp stx)
            throws FusionException
        {
            throw check(stx).failure("must be used inside `provide`");
        }

        @Override
        CompiledForm compile(Evaluator eval, Environment env, SyntaxSexp stx)
            throws FusionException
        {
            throw new IllegalStateException("Shouldn't be compiled");
        }
    }
}
