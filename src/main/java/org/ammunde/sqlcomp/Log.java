/*
 sqlcomp - Copyright (C) 2025 Fredrik Öhrström (gpl-3.0-or-later)

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.ammunde.sqlcomp;

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
        System.err.print(s);
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
