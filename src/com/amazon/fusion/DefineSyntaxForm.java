// Copyright (c) 2012-2013 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.BindingDoc.COLLECT_DOCS_MARK;
import com.amazon.fusion.BindingDoc.Kind;
import com.amazon.fusion.Namespace.TopBinding;

final class DefineSyntaxForm
    extends SyntacticForm
{
    DefineSyntaxForm()
    {
        //    "                                                                               |
        super("id xform",
              "Binds the identifier ID to a syntax transformer XFORM. The transformer must be\n" +
              "a procedure that accepts an syntax object and returns a syntax object.");
    }


    @Override
    SyntaxValue expand(Expander expander, Environment env, SyntaxSexp stx)
        throws FusionException
    {
        SyntaxChecker check = check(stx);
        if (! (expander.isTopLevelContext() || expander.isModuleContext()))
        {
            throw check.failure("Definition must be at top-level or module level");
        }

        int arity = check.arityAtLeast(3);

        SyntaxValue[] children = stx.extract();

        SyntaxSymbol identifier = check.requiredIdentifier(1);

        // WARNING!  This isn't conditional as with 'define' since
        // 'define_syntax' doesn't get predefined by the 'module' expander.
        {
            // We need to strip off the module-level wrap that's already been
            // applied to the identifier. Otherwise we'll loop forever trying
            // to resolve it! This is a bit of a hack, really.
            SyntaxSymbol stripped = identifier.stripImmediateEnvWrap(env);
            Namespace ns = env.namespace();
            ns.predefine(stripped, stx);
        }

        // Update the identifier with its binding.
        // This is just a way to pass the binding instance through to the
        // runtime stage so invoke() below can reuse it.
        children[1] = expander.expand(env, identifier);

        int bodyPos;
        SyntaxValue maybeDoc = children[2];
        if (maybeDoc.getType() == SyntaxValue.Type.STRING && arity > 3)
        {
            bodyPos = 3;
        }
        else
        {
            bodyPos = 2;
        }

        if (bodyPos != arity-1)
        {
            throw check.failure("Too many subforms");
        }

        SyntaxValue valueStx = stx.get(bodyPos);
        children[bodyPos] = expander.expandExpression(env, valueStx);

        stx = SyntaxSexp.make(expander, stx.getLocation(), children);
        return stx;
    }


    //========================================================================


    @Override
    CompiledForm compile(Evaluator eval, Environment env, SyntaxSexp stx)
        throws FusionException
    {
        int arity = stx.size();
        SyntaxValue valueSource = stx.get(arity-1);
        CompiledForm valueForm = eval.compile(env, valueSource);

        SyntaxSymbol identifier = (SyntaxSymbol) stx.get(1);
        TopBinding binding = (TopBinding) identifier.getBinding();
        CompiledForm compiled =
            binding.compileDefineSyntax(eval, env, valueForm);

        if (arity != 3
            && eval.firstContinuationMark(COLLECT_DOCS_MARK) != null)
        {
            // We have documentation. Sort of.
            SyntaxString docString = (SyntaxString) stx.get(2);
            BindingDoc doc = new BindingDoc(identifier.stringValue(),
                                            Kind.SYNTAX,
                                            null, // usage
                                            docString.stringValue());
            env.namespace().setDoc(binding.myAddress, doc);
        }

        return compiled;
    }
}
