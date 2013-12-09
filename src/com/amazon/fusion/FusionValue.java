// Copyright (c) 2012-2013 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionBool.isBool;
import static com.amazon.fusion.FusionBool.unsafeBoolIsTrue;
import static com.amazon.fusion.FusionIo.safeWriteToString;
import static com.amazon.fusion.FusionUtils.EMPTY_STRING_ARRAY;
import static com.amazon.fusion.FusionVoid.isVoid;
import com.amazon.ion.IonException;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonWriter;
import com.amazon.ion.ValueFactory;
import com.amazon.ion.system.IonTextWriterBuilder;
import com.amazon.ion.util.IonTextUtils;
import java.io.IOException;

/**
 * The core features of a Fusion run-time value.  Note that the set of Fusion
 * values is a superset of the Ion values, so not all {@link FusionValue}s are
 * Ion data values.
 * <p>
 * <b>WARNING:</b> This class should not be extended by
 * code outside of this library.
 */
public abstract class FusionValue
{
    private static final class Undef
        extends FusionValue
    {
        @Override
        void write(Evaluator eval, Appendable out) throws IOException
        {
            out.append("{{{undefined}}}");
        }
    }


    /** The singular {@code undef} value. */
    static final FusionValue UNDEF = new Undef();


    //========================================================================

    /** Not for application use. */
    FusionValue()
    {
    }

    static Evaluator evaluator(TopLevel top)
    {
        return ((StandardTopLevel) top).getEvaluator();
    }


    String[] annotationsAsJavaStrings()
    {
        return EMPTY_STRING_ARRAY;
    }


    boolean isAnyNull()
    {
        return false;
    }


    public static boolean isAnyNull(TopLevel top, Object value)
        throws FusionException
    {
        return isAnyNull(evaluator(top), value);
    }

    static boolean isAnyNull(Evaluator eval, Object value)
        throws FusionException
    {
        if (value instanceof FusionValue)
        {
            return ((FusionValue) value).isAnyNull();
        }

        return false;
    }


    /**
     * Determines whether a given Fusion value is "truthy".
     * Fusion defines truthiness as follows:
     * <ul>
     *   <li>
     *     Every value is truthy except for {@code false}, void, and any
     *     variant of {@code null}.
     *   </li>
     * </ul>
     * This definition is more lax (and hopefully more convenient) than Java,
     * but less lenient (and hopefully less error-prone) than C or C++.
     * See <a Null and Void for more explanation.
     *
     * @see <a href="{@docRoot}/../nullvoid.html">Null and Void</a>
     * @see FusionBool#isTrue(TopLevel, Object)
     */
    public static boolean isTruthy(TopLevel top, Object value)
        throws FusionException
    {
        return isTruthy(evaluator(top), value);
    }

    static boolean isTruthy(Evaluator eval, Object value)
        throws FusionException
    {
        if (isVoid(eval, value)) return false;

        if (isAnyNull(eval, value)) return false;

        if (isBool(eval, value))
        {
            return unsafeBoolIsTrue(eval, value);
        }

        return true;
    }


    /**
     * Gets the annotations on a Fusion value as Java strings.
     *
     * @return not null, but possibly empty.
     */
    static String[] annotationsAsJavaStrings(Evaluator eval, Object value)
        throws FusionException
    {
        String[] anns;

        if (value instanceof Annotated)
        {
            anns = ((Annotated) value).annotationsAsJavaStrings();
        }
        else
        {
            anns = EMPTY_STRING_ARRAY;
        }

        return anns;
    }


    //========================================================================
    // Output methods


    /**
     * Builder for temporary IonWriters needed for writing scalars.
     * <p>
     * TODO WORKAROUND ION-398
     * <p>
     * TODO FUSION-247 Write output without building an IonWriter.
     *
     * @deprecated Try to avoid this.
     */
    @Deprecated
    static final IonTextWriterBuilder WRITER_BUILDER =
        IonTextWriterBuilder.minimal().immutable();


    /** Helper method for subclasses. */
    void writeAnnotations(Appendable out, String[] annotations)
        throws IOException
    {
        for (String ann : annotations)
        {
            IonTextUtils.printSymbol(out, ann);
            out.append("::");
        }
    }


    /**
     * Writes an Ion representation of a value.
     * An exception is thrown if the value contains any non-Ion data
     * like closures.
     *
     * @param eval may be null, in which case output may fall back to default
     * format of some kind.
     * @param out the output stream; not null.
     *
     * @throws IOException Propagated from the output stream.
     * @throws IonizeFailure if the data cannot be ionized.
     */
    void ionize(Evaluator eval, IonWriter out)
        throws IOException, IonException, FusionException, IonizeFailure
    {
        throw new IonizeFailure(this);
    }


    /**
     * Writes a representation of this value, following Ion syntax where
     * possible.
     * <p>
     * Most code shouldn't call this method, and should prefer
     * {@link FusionIo#write(Evaluator, Appendable, Object)}.
     *
     * @param eval may be null!
     * @param out the output stream; not null.
     *
     * @throws IOException Propagated from the output stream.
     * @throws FusionException
     */
    abstract void write(Evaluator eval, Appendable out)
        throws IOException, FusionException;


    /**
     * Returns a representation of this value for debugging and diagnostics.
     * Currently, it behaves like {@link FusionIo#write} but the behavior may
     * change at any time.
     */
    @Override
    public final String toString()
    {
        return safeWriteToString(null, this);
    }


    /**
     * Prints a representation of this value for human consumption, generally
     * translating character/string data to it's content without using Ion
     * quotes or escapes. Non-character data is output as per
     * {@link #write(Evaluator, Appendable)}.
     *
     * @param out the output stream; not null.
     *
     * @throws IOException Propagated from the output stream.
     */
    void display(Evaluator eval, Appendable out)
        throws IOException, FusionException
    {
        write(eval, out);
    }


    /**
     * Returns the documentation of this value.
     * <p>
     * <b>Implementations are expected to return the same object instance on
     * every call, in order to preserve proper documentation indexing.</b>
     *
     * @return the documentation model, or null if there's no documentation.
     */
    BindingDoc document()
    {
        return null;
    }


    //========================================================================
    // Static IonValue methods


    /**
     * Returns a new {@link IonValue} representation of a Fusion value,
     * if its type falls within the Ion type system.
     * The {@link IonValue} will use the given factory and will not have a
     * container.
     *
     * @param factory must not be null.
     *
     * @return a fresh instance, without a container, or null if the value is
     * not handled by the default ionization strategy.
     *
     * @throws FusionException if something goes wrong during ionization.
     *
     * @see FusionRuntime#ionizeMaybe(Object, ValueFactory)
     */
    static IonValue copyToIonValueMaybe(Object value, ValueFactory factory)
        throws FusionException
    {
        return copyToIonValue(value, factory, false);
    }


    /**
     * Returns a new {@link IonValue} representation of a Fusion value,
     * if its type falls within the Ion type system.
     * The {@link IonValue} will use the given factory and will not have a
     * container.
     *
     * @param factory must not be null.
     *
     * @throws FusionException if the value cannot be converted to Ion.
     */
    static IonValue copyToIonValue(Object value, ValueFactory factory)
        throws FusionException
    {
        return copyToIonValue(value, factory, true);
    }


    /**
     * Returns a new {@link IonValue} representation of a Fusion value,
     * if its type falls within the Ion type system.
     * The {@link IonValue} will use the given factory and will not have a
     * container.
     *
     * @param value may be an {@link IonValue}, in which case it is cloned.
     * @param factory must not be null.
     *
     * @throws FusionException if the value cannot be converted to Ion.
     *
     * @see FusionRuntime#ionize(Object, ValueFactory)
     */
    static IonValue copyToIonValue(Object value, ValueFactory factory,
                                   boolean throwOnConversionFailure)
        throws FusionException, IonizeFailure
    {
        if (value instanceof FusionValue)
        {
            FusionValue fv = (FusionValue)value;
            return fv.copyToIonValue(factory, throwOnConversionFailure);
        }

        if (throwOnConversionFailure)
        {
            throw new IonizeFailure(value);
        }

        return null;
    }


    /**
     * @throws IonizeFailure (when {@code throwOnConversionFailure})
     * if this value cannot be ionized.
     */
    IonValue copyToIonValue(ValueFactory factory,
                            boolean throwOnConversionFailure)
        throws FusionException, IonizeFailure
    {
        if (throwOnConversionFailure)
        {
            throw new IonizeFailure(this);
        }

        return null;
    }
}
