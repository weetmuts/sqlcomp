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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.DatabaseMetaData;
import java.sql.Types;
import java.util.List;
import java.util.LinkedList;
import java.util.Collections;

public class Column
{
    private Table table_;
    private String name_;
    private int type_;
    private String type_name_;
    private boolean not_null_;
    private String default_value_;
    private int column_size_;

    public Column(Table table, String name, int type, String type_name, boolean not_null, String default_value, int column_size)
    {
        table_ = table;
        name_ = name;
        type_ = type;
        type_name_ = type_name;
        not_null_ = not_null;
        default_value_ = default_value;
        column_size_ = column_size;
    }

    public Table table()
    {
        return table_;
    }

    public String name()
    {
        return name_;
    }

    public String quotedName()
    {
        return "\""+name_+"\"";
    }

    public int type()
    {
        return type_;
    }

    public String typeName()
    {
        return type_name_;
    }

    public boolean notNull()
    {
        return not_null_;
    }

    public String defaultValue()
    {
        return default_value_;
    }

    public void printDefinition(StringBuilder out)
    {
        // As used by create table and alter table add, produces:
        // "age" INT NOT NULL DEFAULT 0
        out.append(quotedName()+" "+typeName());
        if (Table.useColSize(type()) && column_size_ > 0)
        {
            out.append("("+column_size_+")");
        }
        if (not_null_) out.append(" NOT NULL");
        // The default string encodes single quotes, "'hello'" if necessary
        if (default_value_ != null) {
            if (Util.defaultNeedsQuotes(type()))
            {
                out.append(" DEFAULT '"+defaultValue()+"'");
            }
            else
            {
                out.append(" DEFAULT "+defaultValue());
            }
        }
    }

}
