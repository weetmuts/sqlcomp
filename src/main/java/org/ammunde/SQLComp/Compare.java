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

import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Collections;

public class Compare
{
    public static boolean same(List<String> a, List<String> b)
    {
        if (a.size() !=  b.size()) return false;
        Iterator<String> j = b.iterator();
        for (String x : a)
        {
            String y = j.next();
            if (x == null && y != null) return false;
            if (x != null && y == null) return false;
            if (x != null && !x.equals(y)) return false;
        }
        return true;
    }

    public static boolean compareTables(Database from, Database to)
    {
        boolean change_detected = false;

        Set<String> f = new HashSet<>();
        for (String s : from.tableNames()) f.add(s.toLowerCase());
        f.addAll(from.tableNames());
        Set<String> t = new HashSet<>();
        for (String s : to.tableNames()) t.add(s.toLowerCase());

        for (String s : to.tableNames())
        {
            if (!f.contains(s.toLowerCase()))
            {
                Log.info("DROP TABLE "+to.db().schemaPrefix()+s+";\n");
                change_detected = true;
            }
        }
        for (String s : from.tableNames())
        {
            if (!t.contains(s.toLowerCase()))
            {
                Table table = from.table(s);
                table.printCreate();
                change_detected = true;
            }
        }
        return change_detected;
    }

    public static List<String> inBoth(List<String> from, List<String> to)
    {
        List<String> rs = new LinkedList<>();
        Set<String> f = new HashSet<>();
        f.addAll(from);

        for (String s : to)
        {
            if (f.contains(s)) rs.add(s);
        }

        return rs;
    }

    public static boolean tableDefinition(Table from, Table to)
    {
        boolean change_detected = false;

        Set<String> from_colums = new HashSet<>();
        Set<String> to_colums = new HashSet<>();

        from_colums.addAll(from.columnNames());
        to_colums.addAll(to.columnNames());

        if (!from_colums.equals(to_colums))
        {
            Log.info("SOURCE "+from.name()+" "+from.print()+"\n");
            Log.info("SINK   "+to.name()+" "+to.print()+"\n");
            change_detected = true;
        }

        return change_detected;
    }
}
