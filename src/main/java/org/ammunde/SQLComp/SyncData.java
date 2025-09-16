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

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Collections;

public class SyncData
{
    String table_;
    long start_;
    long total_rows_;
    long count_rows_;
    double rows_per_s_;
    int count_inserts_;
    int count_updates_;
    int count_deletes_;
    int last_primary_key_;
    String info_;
    int max_table_name_length_;

    synchronized String renderStatus()
    {
        // sync: 50% (150000/300000) 200 rows/s (123u 2000i 3d) | 2m33s/8m
        // sync: 100% (7/7) 200 rows/s (123u 2000i 3d) | 7m59s done.
        int p = (int)(100.0*(float)(count_rows_)/(float)total_rows_);

        long now = System.currentTimeMillis();
        long millis = now - start_;
        long sofar_s = millis/1000;
        rows_per_s_ = ((double)count_rows_)/(double)(sofar_s);
        long left_s = (long)(((double)total_rows_)/rows_per_s_);
        String sofar = Util.secondsToHR(sofar_s);
        String left = Util.secondsToHRLeft(sofar_s,left_s);

        String info = "";
        if (count_inserts_ > 0) info += count_inserts_+"i ";
        if (count_updates_ > 0) info += count_updates_+"u ";
        if (count_deletes_ > 0) info += count_deletes_+"d ";
        if (Log.numSyncErrors() > 0) info += "\u001B[31m!errors!\u001B[0m ";
        info = info.trim();
        if (info.length() > 0) info = "("+info+")";
        else
        {
            if (count_rows_ == total_rows_)
            {
                info = "(no changes)";
            }
        }

        String speed = ""+((int)rows_per_s_)+" rows/s ";
        if (sofar_s < 10) speed = "";

        String time = sofar;
        if (sofar_s >= 60 && count_rows_ < total_rows_)
        {
            time += "/"+left;
        }

        if (count_rows_ < total_rows_)
        {
            return info_+Util.rightPad(table_, max_table_name_length_, ' ')+": "+p+"% ("+count_rows_+"/"+total_rows_+" pk\""+last_primary_key_+"\") "+speed+info+" | "+time;
        }
        else
        {
            return info_+Util.rightPad(table_, max_table_name_length_, ' ')+": "+p+"% ("+count_rows_+"/"+total_rows_+") "+speed+info+" | "+time;
        }
    }

    synchronized void addCounts(int r, int i, int u, int d, int lpk)
    {
        count_rows_    += r;
        count_inserts_ += i;
        count_updates_ += u;
        count_deletes_ += d;
        last_primary_key_ = lpk;
    }

    public void syncData(Database from,
                         Database to,
                         String table,
                         String info,
                         boolean dryrun)
    {
        info_ = info;
        table_ = table;
        start_ = System.currentTimeMillis();
        count_rows_ = 0;
        count_inserts_ = 0;
        count_updates_ = 0;
        count_deletes_ = 0;

        Table ft = from.table(table);
        Table tt = to.table(table);

        max_table_name_length_ = from.maxTableNameLength();

        if (ft == null)
        {
            Log.error("(sync-data) tab >"+table+"< does not exist in source!");
            System.exit(1);
        }
        if (tt == null)
        {
            Log.warning("(sync-data) table "+table+" does not exist in sink! Please create!\n");
            return;
        }

        total_rows_ = ft.exactNumRows();

        Log.clearSyncErrorWarning();
        Monitor monitor = new Monitor(this::renderStatus);

        List<PK> chunks = ft.chunkPrimaryKeys(100);

        // We start with the highest numbered keys.
        // Why? Assuming they are the most recent updated/created objects, then we sync those first.
        // Older keys are less likely to change.
        Collections.reverse(chunks);

        for (PK chunk : chunks)
        {
            syncChunk(ft, tt, chunk, false, dryrun);
        }

        monitor.stop();
        Log.warnIfSyncErrorsFound();
    }

    void syncChunk(Table ft, Table tt, PK chunk, boolean stream, boolean dryrun)
    {
        StringBuilder inserts = new StringBuilder();
        inserts.append("INSERT INTO "+tt.schemaPrefix()+tt.quotedName()+" ("+ft.columnsForSelect()+") VALUES ");

        StringBuilder deletes = new StringBuilder();
        deletes.append("DELETE FROM "+tt.schemaPrefix()+tt.quotedName()+" WHERE "+ft.quotedPrimaryKey()+" in (");

        StringBuilder updates = new StringBuilder();

        int num_inserts = 0;
        int num_oks = 0;
        int num_deletes = 0;
        int num_updates = 0;

        ArrayList<Row> from_rows = ft.rows(chunk, ft);
        ArrayList<Row> to_rows = tt.rows(chunk, ft); // Note that we use the source as reference for column names.

        int i = 0;
        int j = 0;
        while (true)
        {
            Row f = null;
            if (i < from_rows.size()) f = from_rows.get(i);
            Row t = null;
            if (j < to_rows.size()) t = to_rows.get(j);

            if (f != null && t != null)
            {
                if (f.pk() == t.pk())
                {
                    if (f.cols().size() != t.cols().size())
                    {
                        Log.warning("(sync-data) mismatch table definition "+ft.name()+" skipping update\n"
                                    +f.commaCols()+"\n"
                                    +t.commaCols()+"\n");
                    }
                    else if (!Compare.same(f.cols(), t.cols()))
                    {
                        StringBuilder update = new StringBuilder();
                        update.append("UPDATE "+tt.schemaPrefix()+
                                      tt.quotedName()+" SET ");

                        Iterator<String> cn = ft.columnNames().iterator();
                        Iterator<String> x = f.cols().iterator();
                        Iterator<String> y = t.cols().iterator();
                        int num_cols = 0;
                        while (true)
                        {
                            if (!x.hasNext()) break;
                            String n = cn.next();
                            String a = x.next();
                            String b = y.next();

                            if ((a == null && b != null) ||
                                (a != null && b == null) ||
                                (a != null && b != null && !a.equals(b)))
                            {
                                if (num_cols > 0) update.append(",");
                                num_cols++;
                                update.append("\""+n+"\"="+a);
                            }
                        }
                        if (num_cols == 0)
                        {
                            Log.error("INTERNAL ERROR:\n"+f.commaCols()+"\n"+t.commaCols());
                            System.exit(1);
                        }
                        update.append(" WHERE "+ft.quotedPrimaryKey()+"="+f.pk());
                        String u = update.toString();
                        if (updates.length() > 0) updates.append(";");
                        updates.append(u);
                        if (dryrun) Log.info(u+"\n");
                        else if (stream) Log.verbose("(stream-data) "+u+"\n");
                        else Log.verbose("sync-data) "+u+"\n");
                        num_updates++;
                    }
                    else
                    {
                        num_oks++;
                    }
                    i++;
                    j++;
                }
                else if (f.pk() > t.pk())
                {
                    if (num_deletes > 0) deletes.append(",");
                    deletes.append(t.pk());
                    num_deletes++;
                    j++;
                }
                else if (f.pk() < t.pk())
                {
                    if (num_inserts > 0) inserts.append(",");
                    num_inserts++;
                    inserts.append("("+f.commaCols()+")");
                    i++;
                }
            }
            else if (f != null && t == null)
            {
                if (num_inserts > 0) inserts.append(",");
                num_inserts++;

                inserts.append("("+f.commaCols()+")");
                i++;
            }
            else if (f == null && t != null)
            {
                if (num_deletes > 0) deletes.append(",");
                deletes.append(t.pk());
                num_deletes++;
                j++;
            }
            else
            {
                // Both are null we are done.
                break;
            }
        }
        if (num_inserts > 0)
        {
            String ins = inserts.toString();
            if (dryrun) Log.info(ins+"\n");
            else if (stream) Log.verbose("(stream-data) "+ins);
            // Do not print batch inserts, too many of them.
            if (!dryrun) tt.db().performSyncUpdate(ins);
        }

        if (num_updates > 0)
        {
            if (!dryrun) tt.db().performSyncUpdate(updates.toString());
        }

        if (num_deletes > 0)
        {
            deletes.append(")");
            String d = deletes.toString();
            if (dryrun) Log.info(d);
            else if (stream) Log.verbose("(stream-data) "+d);
            else Log.verbose("(sync-data) "+d);
            if (!dryrun) tt.db().performSyncUpdate(d);
        }

        addCounts(from_rows.size(), num_inserts, num_updates, num_deletes, chunk.from());
    }
}
