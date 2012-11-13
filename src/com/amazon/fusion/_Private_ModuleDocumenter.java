// Copyright (c) 2012 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.ModuleDocumentation.buildDocTree;
import com.petebevin.markdown.MarkdownProcessor;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * NOT FOR APPLICATION USE
 */
public final class _Private_ModuleDocumenter
{
    private _Private_ModuleDocumenter() {}


    public static void writeHtmlTree(FusionRuntime runtime,
                                     File outputDir,
                                     File repoDir)
        throws IOException, FusionException
    {
        ModuleDocumentation doc = buildDocTree(runtime, repoDir);
        writeHtmlTree(outputDir, doc);
    }


    private static void writeHtmlTree(File outputDir, ModuleDocumentation doc)
        throws IOException
    {
        if (doc.myName != null)
        {
            File outputFile = new File(outputDir, doc.myName + ".html");
            writeHtmlFile(outputFile, doc);
            outputDir = new File(outputDir, doc.myName);
        }

        Collection<ModuleDocumentation> submodules = doc.submodules();
        for (ModuleDocumentation submodule : submodules)
        {
            writeHtmlTree(outputDir, submodule);
        }
    }


    private static void writeHtmlFile(File out, ModuleDocumentation doc)
        throws IOException
    {
        out.getParentFile().mkdirs();

        FileWriter fw = new FileWriter(out);
        try
        {
            renderModule(fw, doc);
        }
        finally
        {
            fw.close();
        }
    }


    private static void renderModule(Appendable out, ModuleDocumentation doc)
        throws IOException
    {
        renderHead(out, doc);

        renderHeader1(out, "Module " + doc.myPath);

        renderSubmoduleLinks(out, doc);

        String[] names = doc.sortedExportedNames();

        renderBindingIndex(out, doc, names);
        renderBindings(out, doc, names);
    }


    private static final String STYLE =
        "<style type='text/css'>" +
        " .binding {" +
        "   display: block; width: 100%;" +
        " }" +
        " .bound {" +
        "   font-size: 1.17em;" +
        "   font-weight: bold;" +
        " }" +
        " .kind {" +
        "   float: right; font-style: italic" +
        " }" +
        "</style>\n";

    private static void renderHead(Appendable out, ModuleDocumentation doc)
        throws IOException
    {
        out.append("<head>");
        out.append("<title>");
        out.append(escape(doc.myPath));
        out.append("</title>\n");
        out.append(STYLE);
        out.append("</head>\n");
    }


    private static void renderSubmoduleLinks(Appendable out,
                                             ModuleDocumentation doc)
        throws IOException
    {
        Map<String, ModuleDocumentation> submodules = doc.submoduleMap();
        if (submodules == null) return;

        renderHeader2(out, "Submodules");

        String superModuleName = escape(doc.myName);

        String[] names = submodules.keySet().toArray(new String[0]);
        Arrays.sort(names);

        out.append("<ul>");
        for (String name : names)
        {
            name = escape(name);
            out.append("<li><a href='");
            out.append(superModuleName);
            out.append('/');
            out.append(name);
            out.append(".html'>");
            out.append(name);
            out.append("</a></li>\n");
        }
        out.append("</ul>\n");
    }


    private static void renderBindingIndex(Appendable out,
                                           ModuleDocumentation doc,
                                           String[] names)
        throws IOException
    {
        if (names.length == 0) return;

        renderHeader2(out, "Index");

        for (String name : names)
        {
            name = escape(name);
            out.append("<a href='#");
            out.append(name);
            out.append("'><code>");
            out.append(name);
            out.append("</code></a>\n");
        }
    }


    private static void renderBindings(Appendable out,
                                       ModuleDocumentation doc,
                                       String[] names)
        throws IOException
    {
        Map<String, BindingDocumentation> bindings = doc.bindingMap();
        if (bindings == null) return;

        renderHeader2(out, "Exported Bindings");

        for (String name : names)
        {
            // May be null:
            BindingDocumentation feature = bindings.get(name);
            renderBinding(out, name, feature);
        }
    }


    private static void renderBinding(Appendable out, String name,
                                      BindingDocumentation doc)
        throws IOException
    {
        name = escape(name);

        out.append("<span class='binding'><span class='bound'><a name='");
        out.append(name);
        out.append("'>");
        out.append(name);
        out.append("</a></span>");   // binding span is still open

        if (doc == null)
        {
            out.append("</span>\n"); // binding
            out.append("<p>No documentation available.<p>\n\n");
        }
        else
        {
            if (doc.myKind != null)
            {
                out.append("<span class='kind'>");
                // Using enum toString() allows display name to be changed
                out.append(doc.myKind.toString().toLowerCase());
                out.append("</span>\n");
            }
            out.append("</span>\n"); // binding

            StringBuilder buf = new StringBuilder();

            if (doc.myUsage != null)
            {
                buf.append("    ");
                buf.append(doc.myUsage);
                buf.append('\n');
            }

            if (doc.myBody != null)
            {
                buf.append('\n');
                buf.append(doc.myBody);
                buf.append('\n');
            }

            out.append(markdown(buf.toString()));
        }
    }


    private static void renderHeader1(Appendable out, String text)
        throws IOException
    {
        out.append("<h1>");
        out.append(escape(text));
        out.append("</h1>\n");
    }

    private static void renderHeader2(Appendable out, String text)
        throws IOException
    {
        out.append("<h2>");
        out.append(escape(text));
        out.append("</h2>\n");
    }


    private static String escape(String text)
    {
        text = text.replace("&", "&amp;");
        text = text.replace("<", "&lt;");
        text = text.replace(">", "&gt;");
        return text;
    }


    private static String markdown(String text)
    {
        return new MarkdownProcessor().markdown(text);
    }
}
