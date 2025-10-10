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

    public static boolean findCreatesAndDrops(Database from, Database to, StringBuilder out)
    {
        boolean change_detected = false;
        boolean drop_found = false;

        Set<String> f = new HashSet<>();
        for (String s : from.tableNames())
        {
            f.add(s.toLowerCase());
        }

        Set<String> t = new HashSet<>();
        for (String s : to.tableNames())
        {
            t.add(s.toLowerCase());
        }

        for (String s : to.tableNames())
        {
            if (!f.contains(s.toLowerCase()))
            {
                if (!drop_found)
                {
                    out.append("-- Warning! Dropping table! Note that a table rename becomes a drop+create!\n");
                    drop_found = true;
                }
                out.append("DROP TABLE "+to.db().schemaPrefix()+s+";\n");
                change_detected = true;
            }
        }

        for (String s : from.tableNames())
        {
            if (!t.contains(s.toLowerCase()))
            {
                Table table = from.table(s);
                table.printCreate(out, to);
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

    public static boolean tableDefinition(Table from, Table to, StringBuilder out)
    {
        boolean change_detected = false;

        Set<String> from_colums = new HashSet<>();
        Set<String> to_colums = new HashSet<>();

        from_colums.addAll(from.columnNames());
        to_colums.addAll(to.columnNames());

        for (String s : from.columnNames())
        {
            if (!to_colums.contains(s))
            {
                Column c = from.column(s);
                out.append("ALTER TABLE "+to.quotedName()+" "+to.addColumn()+" ");
                c.printDefinition(out, to.database());
                out.append(";\n");
            }
        }
        return change_detected;
    }
}
