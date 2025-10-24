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

public class Translate
{
    public static String type(int jdbc_type, String type, DBType from, DBType to)
    {
        if (from == DBType.POSTGRES && to != DBType.POSTGRES)
        {
            return postgresToSqlServer(jdbc_type, type);
        }
        return type;
    }

    public static String postgresToSqlServer(int jdbc_type, String type)
    {
        switch (type)
        {
        case "int8": return "BIGINT";
        case "int4": return "INT";
        }
        return type;
    }

    public static String defaultValue(String val, DBType from)
    {
        if (val == null) return val;

        if (from == DBType.POSTGRES)
        {
            return stripPostgresTypeCast(val);
        }
        if (from == DBType.SQLSERVER)
        {
            if (val.charAt(0) == '(' && val.charAt(val.length()-1) == ')')
            {
                val = val.substring(1, val.length()-1);
            }
            if (val.charAt(0) == '(' && val.charAt(val.length()-1) == ')')
            {
                val = val.substring(1, val.length()-1);
            }
            else if (val.charAt(0) == '\'' && val.charAt(val.length()-1) == '\'')
            {
                val = val.substring(1, val.length()-1);
            }
        }
        return val;
    }

    public static String stripPostgresTypeCast(String val)
    {
        if (val == null || val.isEmpty()) {
            return val;
        }

        // Remove surrounding parentheses if present
        val = val.trim();
        if (val.startsWith("(") && val.endsWith(")")) {
            val = val.substring(1, val.length() - 1).trim();
        }

        // Split on "::" to remove type cast
        String[] parts = val.split("::");
        String literal = parts[0].trim();

        // Remove surrounding single quotes if present
        if (literal.startsWith("'") && literal.endsWith("'")) {
            literal = literal.substring(1, literal.length() - 1);
        }

        return literal;
    }
}
