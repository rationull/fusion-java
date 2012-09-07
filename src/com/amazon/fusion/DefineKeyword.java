// Copyright (c) 2012 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import com.amazon.fusion.Namespace.NsBinding;

final class DefineKeyword
    extends KeywordValue
{
    DefineKeyword()
    {
        //    "                                                                               |
        super("VAR VALUE",
              "Defines a top-level variable VAR with the given VALUE.");
    }


    static SyntaxSymbol boundIdentifier(Evaluator eval, Environment env,
                                        SyntaxSexp source)
        throws SyntaxFailure
    {
        SyntaxChecker check = new SyntaxChecker("define", source);
        check.arityExact(3);

        SyntaxSymbol identifier = check.requiredIdentifier(1);
        return identifier;
    }


    @Override
    SyntaxValue prepare(Evaluator eval, Environment env, SyntaxSexp source)
        throws SyntaxFailure
    {
        SyntaxChecker check = check(source);
        check.arityExact(3);

        SyntaxSymbol identifier = check.requiredIdentifier(1);

        // We need to strip off the module-level wrap that's already been
        // applied to the identifier. Otherwise we'll loop forever trying to
        // resolve it! This is a bit of a hack, really.
        //identifier = identifier.stripImmediateEnvWrap(env);
        SyntaxSymbol stripped = identifier.stripImmediateEnvWrap(env);

        // If at module top-level, this has already been done.
        // TODO we should know the context where this is happening...
        Namespace ns = env.namespace();
        ns.predefine(stripped);

        // Update the identifier with its binding.
        // This is just a way to pass the binding instance through to the
        // runtime stage so compile() below can reuse it.
        identifier = (SyntaxSymbol) identifier.prepare(eval, env);

        SyntaxValue valueStx = source.get(2);
        valueStx = valueStx.prepare(eval, env);

        source = SyntaxSexp.make(source.getLocation(),
                                 source.get(0), identifier, valueStx);
        return source;
    }


    //========================================================================


    @Override
    CompiledForm compile(Evaluator eval, Environment env, SyntaxSexp source)
        throws FusionException
    {
        SyntaxValue valueSource = source.get(2);
        CompiledForm valueForm = eval.compile(env, valueSource);

        SyntaxSymbol identifier = (SyntaxSymbol) source.get(1);
        NsBinding binding = (NsBinding) identifier.getBinding();
        assert binding != null;

        return new CompiledDefine(binding, valueForm);
    }


    //========================================================================


    private static final class CompiledDefine
        implements CompiledForm
    {
        private final NsBinding    myBinding;
        private final CompiledForm myValueForm;

        private CompiledDefine(NsBinding binding, CompiledForm valueForm)
        {
            myBinding   = binding;
            myValueForm = valueForm;
        }

        @Override
        public Object doExec(Evaluator eval, Store store)
            throws FusionException
        {
            Object value = eval.exec(store, myValueForm);
            RuntimeNamespace ns = store.runtimeNamespace();
            ns.bindPredefined(myBinding, value);
            return value;
        }
    }
}
