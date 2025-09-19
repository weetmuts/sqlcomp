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

import java.io.PrintWriter;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class Status
{
    String prefix_;
    Map<String,Table> tables_ = new TreeMap<>();
    Map<String,Stats> table_stats_ = new TreeMap<>();
    Map<String,String> table_monitor_ = new TreeMap<>();
    String last_batch_done_ = "";

    public Status(String p)
    {
        prefix_ = p;
    }

    public synchronized Stats get(Table table)
    {
        Stats s = table_stats_.get(table.lcName());
        if (s == null)
        {
            s = new Stats();
            tables_.put(table.lcName(), table);
            table_stats_.put(table.lcName(), s);
            if (table_monitor_.get(table.lcName()) == null)
            {
                table_monitor_.put(table.lcName(), "");
            }
        }
        return s;
    }

    public void monitor(Table table, String monitor)
    {
        int c = monitor.indexOf(":");
        if (c != -1) monitor = monitor.substring(c+1);
        get(table);
        table_monitor_.put(table.lcName(), monitor);
    }

    public synchronized void clearStats()
    {
        Map<String,Stats> table_stats = new TreeMap<>();
    }

    public synchronized void batchDone()
    {
        table_stats_ = new TreeMap<>();
        last_batch_done_ = Util.timestamp();
    }

    public synchronized void incInserts(Table table)
    {
        get(table).inserts++;
    }

    public synchronized void incUpdates(Table table)
    {
        get(table).updates++;
    }

    public synchronized void incDeletes(Table table)
    {
        get(table).deletes++;
    }

    public synchronized void incFailures(Table table)
    {
        get(table).failures++;
    }

    public void writeStatus()
    {
        String file = System.getenv("SQLCOMP_STATUS_HTML");
        if (file == null || file.trim().equals("")) return ;

        int p = file.lastIndexOf("/");

        if (p == -1)
        {
            file = prefix_+"-"+file;
        }
        else
        {
            file = file.substring(0, p+1)+prefix_+"-"+file.substring(p+1);
        }

        Log.debug("(status) writing "+file+"\n");

        try
        {
            StringBuilder html = new StringBuilder();
            writeHeader(html);
            writeInfo(html);
            writeFooter(html);

            PrintWriter out = new PrintWriter(file);
            out.println(html.toString());
            out.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    public void writeInfo(StringBuilder html)
    {
        LocalTime now = Util.localTime();
        String last_hour = String.format("%02d", now.getHour());
        int hour = now.getHour();
        int minute = now.getMinute();
        int second = now.getSecond();

        html.append("["+hour+":"+minute+":"+second+"]<p>\n");
        html.append("<table>\n");
        html.append("<tr>");
        html.append("<th>Name</td>");
        html.append("<th>&nbsp;&nbsp;today&nbsp;&nbsp;</td>");
        html.append("<th>night batch done "+last_batch_done_+"</td>");
        html.append("</tr>\n");

        List<String> sorted = new ArrayList<>(tables_.keySet());
        Collections.sort(sorted);

        for (String s : sorted)
        {
            Table t = tables_.get(s);
            html.append("<tr>");
            html.append("<td>"+t.lcName()+"</td>");
            html.append("<td>");
            html.append(table_stats_.get(t.lcName()).toHTML());
            html.append("</td>");
            html.append("<td>");
            html.append(table_monitor_.get(t.lcName()));
            html.append("</td>");
            html.append("</tr>\n");
        }
    }


    public void writeHeader(StringBuilder html)
    {
        html.append(
            """
            <!DOCTYPEh html>
            <html>
            <style>
            body { font-family: "Courier New", Courier, "Lucida Sans Typewriter", "Lucida Typewriter", monospace; font-size: 16px; }
            table { border-collapse: collapse; border:none; }
            td, th { border: solid 1px #eee; padding: 4px 8px;}
            tr:nth-child(odd) { background-color:#eee; }
            tr:nth-child(even) { background-color:#fff; }
            th { background-color:#afa; }
            </style>
            <body>
            """);
    }

    public void writeFooter(StringBuilder html)
    {
        html.append(
            """
            </body>
            </html>
            """);
    }

}
