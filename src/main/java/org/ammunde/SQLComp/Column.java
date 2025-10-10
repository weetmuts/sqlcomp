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

    String toTypeName(Database to)
    {
        return Translate.type(type_, type_name_, table_.database().db().dbType(), to.db().dbType());
    }

    public void printDefinition(StringBuilder out, Database to)
    {
        // As used by create table and alter table add, produces:
        // "age" INT NOT NULL DEFAULT 0
        out.append(quotedName()+" "+toTypeName(to));
        if (Table.useColSize(type()) && column_size_ > 0)
        {
            out.append("("+column_size_+")");
        }
        if (not_null_) out.append(" NOT NULL");
        // The default string encodes single quotes, "'hello'" if necessary

        if (default_value_ != null)
        {
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
