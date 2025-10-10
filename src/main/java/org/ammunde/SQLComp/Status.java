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
    String last_stream_ = "";
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
        last_stream_ = "";
        last_batch_done_ = Util.timestamp();
    }

    public synchronized void incInserts(Table table)
    {
        get(table).inserts++;
        last_stream_ = Util.timestamp();
    }

    public synchronized void incUpdates(Table table)
    {
        get(table).updates++;
        last_stream_ = Util.timestamp();
    }

    public synchronized void incDeletes(Table table)
    {
        get(table).deletes++;
        last_stream_ = Util.timestamp();
    }

    public synchronized void incFailures(Table table)
    {
        get(table).failures++;
        last_stream_ = Util.timestamp();
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
        html.append("<th>&nbsp;&nbsp;today "+last_stream_+"&nbsp;&nbsp;</td>");
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
            Stats st = table_stats_.get(t.lcName());
            if (st != null) html.append(st.toHTML());
            html.append("</td>");
            html.append("<td>");
            String m = table_monitor_.get(t.lcName());
            if (m != null) html.append(m);
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
