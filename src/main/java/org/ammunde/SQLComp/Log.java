/* sqlcomp - Copyright (C) 2025 Fredrik Öhrström (spdx: MIT)

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

*/

package org.ammunde.SQLComp;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;

public class Log
{
    static boolean timestamp_enabled_;
    static boolean debug_enabled_;
    static boolean verbose_enabled_;
    static boolean prefix_nl_;
    static String  sync_error_file_ = "sync_errors.log";
    static boolean sync_errors_found_ = false;
    static int num_sync_errors_ = 0;

    public static String[] parseArgs(String[] args)
    {
        while (true)
        {
            if (args.length == 0) break;
            String s = args[0];
            if (s.equals("--verbose") || s.equals("-v"))
            {
                verbose_enabled_ = true;
                args = Util.shiftLeft(args);
            }
            else if (s.equals("--debug") || s.equals("-d"))
            {
                debug_enabled_ = true;
                verbose_enabled_ = true;
                args = Util.shiftLeft(args);
            }
            else if (s.equals("--timestamp") || s.equals("-t"))
            {
                timestamp_enabled_ = true;
                args = Util.shiftLeft(args);
            }
            else if (s.startsWith("--log-sync-errors-to="))
            {
                sync_error_file_ = s.substring(21);
                Log.info("(sqlcomp) storing sync errors in: "+sync_error_file_+"\n");
                args = Util.shiftLeft(args);
            }
            else break;
        }
        return args;
    }

    public static void enableDebug()
    {
        debug_enabled_ = true;
        verbose_enabled_ = true;
    }

    public static void enableVerbose()
    {
        verbose_enabled_ = true;
    }

    public static void addTimestamp()
    {
        timestamp_enabled_ = true;
    }

    public static String timestamp()
    {
        if (timestamp_enabled_) return "["+Util.timestamp()+"] ";
        return "";
    }

    public static void prefixNewline(boolean y)
    {
        prefix_nl_ = y;
    }

    public static void verbose(String s)
    {
        if (verbose_enabled_)
        {
            if (prefix_nl_) System.err.println();
            System.err.print(timestamp());
            System.err.print(s);
        }
    }

    public static void debug(String s)
    {
        if (debug_enabled_)
        {
            if (prefix_nl_) System.err.println();
            System.err.print(timestamp());
            System.err.print(s);
        }
    }

    public static void warning(String s)
    {
        if (prefix_nl_) System.err.println();
        System.err.print(timestamp());
        System.err.print("\u001B[31mWARNING!\u001B[0m ");
        System.err.print(s);
    }

    public static void info(String s)
    {
        if (prefix_nl_) System.out.println();
        System.out.print(timestamp());
        System.out.print(s);
    }

    public static void error(String s)
    {
        if (prefix_nl_) System.err.println();
        System.err.print(timestamp());
        System.err.println(s);
    }

    public static void usageError(String s)
    {
        if (prefix_nl_) System.err.println();
        System.err.print(timestamp());
        System.err.print(s);
    }

    public static void clearSyncErrorWarning()
    {
        sync_errors_found_ = false;
        num_sync_errors_ = 0;
    }

    public static void syncError(String s)
    {
        if (!sync_errors_found_)
        {
            sync_errors_found_ = true;
            warnIfSyncErrorsFound();
        }
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(sync_error_file_, true), "UTF-8"))))
        {
            pw.println(s);
            num_sync_errors_++;
        }
        catch (Exception e)
        {
            e.printStackTrace(System.out);
            info(s);
            info("\n");
        }
    }

    public static int numSyncErrors()
    {
        return num_sync_errors_;
    }

    public static void warnIfSyncErrorsFound()
    {
        if (sync_errors_found_)
        {
            Log.warning("WARNING! Sync errors found check log in "+sync_error_file_+"\n");
        }
    }

    public static void status(String s)
    {
        System.err.print("\33[2K\r");
        System.err.print(s);
    }

    public static void statusFinal(String s)
    {
        System.err.print("\33[2K\r");
        System.err.println(s);
    }
}
