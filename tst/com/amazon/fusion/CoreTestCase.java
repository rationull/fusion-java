// Copyright (c) 2012 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static org.junit.Assert.assertEquals;
import com.amazon.ion.IonContainer;
import com.amazon.ion.IonDecimal;
import com.amazon.ion.IonInt;
import com.amazon.ion.IonList;
import com.amazon.ion.IonSequence;
import com.amazon.ion.IonString;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonText;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;
import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

public class CoreTestCase
{
    static final String NONTERMINATING_EXPRESSION =
        "((lambda (x) (x x)) (lambda (x) (x x)))";

    /** Exprs that evaluate to Ion types. */
    private static final String[] ION_EXPRESSIONS =
    {
        "null",
        "null.bool", "true", "false",
        "null.int", "0", "1", "12345",
        "null.decimal", "0.", "0.0", "123.45",
        "null.float", "0e0", "123e45",
        "null.timestamp", "2012-04-20T16:20-07:00",
        "null.string", "\"\"", "\"text\"",
        "(quote null.symbol)", "(quote sym)",
        "null.blob", "{{}}",
        "null.clob", "{{\"\"}}",
        "null.list", "[]",
        "(quote null.sexp)", "(quote ())",
        "null.struct", "{}",
    };

    /** Exprs that evaluate to non-Ion types. */
    private static final String[] NON_ION_EXPRESSIONS =
    {
        "undef",
        "(lambda () 1)",
    };


    //========================================================================


    private IonSystem mySystem;
    private FusionRuntimeBuilder myRuntimeBuilder;
    private FusionRuntime myRuntime;
    private TopLevel myTopLevel;

    @Before
    public void setUp()
        throws Exception
    {
        mySystem = IonSystemBuilder.standard().build();
    }

    @After
    public void tearDown()
        throws Exception
    {
        mySystem = null;
        myRuntimeBuilder = null;
        myRuntime = null;
        myTopLevel = null;
    }

    protected IonSystem system()
    {
        return mySystem;
    }

    protected FusionRuntimeBuilder runtimeBuilder()
    {
        if (myRuntimeBuilder == null)
        {
            myRuntimeBuilder = FusionRuntimeBuilder.standard();
            File userDir = new File(System.getProperty("user.dir"));
            File repoDir = new File(userDir, "repo");
            myRuntimeBuilder.addRepositoryDirectory(repoDir);
        }
        return myRuntimeBuilder;
    }

    protected void useTstRepo()
    {
        File tstRepo = new File("tst-repo");
        runtimeBuilder().addRepositoryDirectory(tstRepo);
    }

    protected FusionRuntime runtime()
    {
        if (myRuntime == null)
        {
            myRuntime = runtimeBuilder().build();
        }
        return myRuntime;
    }

    protected TopLevel topLevel()
        throws FusionException
    {
        if (myTopLevel == null)
        {
            myTopLevel = runtime().getDefaultTopLevel();
        }
        return myTopLevel;
    }


    //========================================================================
    // Basic evaluation

    protected Object eval(String expressionIon)
        throws FusionException
    {
        TopLevel top = topLevel();
        return top.eval(expressionIon);
    }


    protected Object loadFile(String path)
        throws FusionException
    {
        File file = new File(path);
        TopLevel top = topLevel();
        return top.load(file);
    }

    protected IonValue evalToIon(String source)
        throws FusionException
    {
        Object fv = eval(source);
        IonValue iv = runtime().ionizeMaybe(fv, system());
        if (iv == null)
        {
            Assert.fail("Result isn't ion: " + fv + "\nSource: " + source);
        }
        return iv;
    }

    protected Procedure evalToProcedure(String expressionIon)
        throws FusionException
    {
        Object result = eval(expressionIon);
        return (Procedure) result;
    }


    //========================================================================

    List<String> allTypeExpressions()
    {
        ArrayList<String> exprs = new ArrayList<String>();
        Collections.addAll(exprs, ION_EXPRESSIONS);
        Collections.addAll(exprs, NON_ION_EXPRESSIONS);
        assert exprs.size() != 0;
        return exprs;
    }


    List<String> allIonExpressions()
        throws FusionException
    {
        ArrayList<String> exprs = new ArrayList<String>();
        Collections.addAll(exprs, ION_EXPRESSIONS);
        assert exprs.size() != 0;
        return exprs;
    }


    List<String> nonIonExpressions()
        throws FusionException
    {
        ArrayList<String> exprs = new ArrayList<String>();
        Collections.addAll(exprs, NON_ION_EXPRESSIONS);
        assert exprs.size() != 0;
        return exprs;
    }


    <T extends IonValue> List<String> nonIonExpressions(Class<T> klass)
        throws FusionException
    {
        ArrayList<String> exprs = new ArrayList<String>();
        for (String expr : allTypeExpressions())
        {
            Object v = eval(expr);
            IonValue dom = runtime().ionizeMaybe(v, system());
            if (dom == null || ! klass.isInstance(dom))
            {
                exprs.add(expr);
            }
        }
        assert exprs.size() != 0;
        return exprs;
    }


    List<String> nonIntExpressions()
        throws FusionException
    {
        return nonIonExpressions(IonInt.class);
    }

    List<String> nonTextExpressions()
        throws FusionException
    {
        return nonIonExpressions(IonText.class);
    }

    List<String> nonContainerExpressions()
        throws FusionException
    {
        return nonIonExpressions(IonContainer.class);
    }

    List<String> nonSequenceExpressions()
        throws FusionException
    {
        return nonIonExpressions(IonSequence.class);
    }

    List<String> nonListExpressions()
        throws FusionException
    {
        return nonIonExpressions(IonList.class);
    }

    List<String> nonStructExpressions()
        throws FusionException
    {
        return nonIonExpressions(IonStruct.class);
    }


    //========================================================================


    void checkString(String expected, Object actual)
    {
        String actualString = FusionValue.asJavaString(actual);
        assertEquals(expected, actualString);
    }


    //========================================================================


    protected void assertEval(IonValue expected, String source)
        throws FusionException
    {
        IonValue iv = evalToIon(source);
        assertEquals(source, expected, iv);
    }

    protected void assertEval(IonValue expected, IonValue source)
        throws FusionException
    {
        String sourceText = source.toString();
        assertEval(expected, sourceText);
    }

    protected void assertEval(String expectedIon, String sourceIon)
        throws FusionException
    {
        IonValue expected = mySystem.singleValue(expectedIon);
        assertEval(expected, sourceIon);
    }

    protected void assertUndef(String expressionIon)
        throws FusionException
    {
        Object fv = eval(expressionIon);
        if (fv != FusionValue.UNDEF)
        {
            Assert.fail("Result isn't undef: " + fv + "\nSource: " + expressionIon);
        }
    }

    protected void assertEval(boolean expectedBool, String expressionIon)
        throws FusionException
    {
        IonValue expected = mySystem.newBool(expectedBool);
        assertEval(expected, expressionIon);
    }

    protected void assertEval(int expectedInt, String expressionIon)
        throws FusionException
    {
        IonValue expected = mySystem.newInt(expectedInt);
        assertEval(expected, expressionIon);
    }

    protected void assertEval(BigInteger expectedInt, String expressionIon)
        throws FusionException
    {
        Object fv = eval(expressionIon);
        IonValue observed = FusionValue.castToIonValueMaybe(fv);
        if (observed instanceof IonInt)
        {
            IonInt iObsExp = (IonInt)observed;
            BigInteger obsExp = iObsExp.bigIntegerValue();
            if (obsExp.compareTo(expectedInt) == 0)
            {
                return;
            }
            Assert.fail("Discrepency: Observed "+obsExp.toString()+", expected "+expectedInt.toString());
        }
        Assert.fail("Invalid type.");
    }

    protected void assertEval(BigDecimal expected, String expressionIon)
        throws FusionException
    {
        Object fv = eval(expressionIon);
        IonValue observed = FusionValue.castToIonValueMaybe(fv);
        if (observed instanceof IonDecimal)
        {
            IonDecimal iObsExp = (IonDecimal)observed;
            BigDecimal obsExp = iObsExp.bigDecimalValue();
            if (obsExp.compareTo(expected) == 0)
            {
                return;
            }
            Assert.fail("Discrepency: Observed "+obsExp.toString()+", expected "+expected.toString());
        }
        Assert.fail("Invalid type.");
    }

    protected void assertBigInt(int expectedInt, String expressionIon)
        throws FusionException
    {
        BigInteger bExpInt = BigInteger.valueOf(expectedInt);
        assertEval(bExpInt, expressionIon);
    }

    protected void assertString(String expectedString, String expressionIon)
        throws FusionException
    {
        Object fv = eval(expressionIon);
        IonValue iv = FusionValue.castToIonValueMaybe(fv);
        if (iv instanceof IonString)
        {
            IonString is = (IonString)iv;
            String result = is.stringValue();
            assertEquals(expressionIon, expectedString, result);
            return;
        }
        Assert.fail("Input arg is of invalid type.");
    }

    protected void assertSelfEval(String expressionIon)
        throws FusionException
    {
        assertEval(expressionIon, expressionIon);
    }


    //========================================================================

    <T extends FusionException> T expectFailure(Class<T> klass, String expr)
            throws Exception
    {
        try
        {
            eval(expr);
            Assert.fail("Expected exception from " + expr);
            return null; // Dummy for compiler
        }
        catch (Exception e)
        {
            if (klass.isInstance(e))
            {
                return klass.cast(e);
            }
            throw e;
        }
    }

    void expectFusionException(String expr)
        throws Exception
    {
        expectFailure(FusionException.class, expr);
    }

    void expectSyntaxFailure(String expr)
        throws Exception
    {
        expectFailure(SyntaxFailure.class, expr);
    }


    void expectContractFailure(String expr)
        throws Exception
    {
        expectFailure(ContractFailure.class, expr);
    }


    void expectArityFailure(String expr)
        throws Exception
    {
        expectFailure(ArityFailure.class, expr);
    }

    void expectArgTypeFailure(String expr, int badArgNum)
        throws Exception
    {
        ArgTypeFailure e = expectFailure(ArgTypeFailure.class, expr);
        assertEquals("argument #", badArgNum, e.getBadPos());
    }

}
