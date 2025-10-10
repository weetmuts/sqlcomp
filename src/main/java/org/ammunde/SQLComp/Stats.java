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

public class Stats
{
    int updates;
    int inserts;
    int deletes;
    int failures;

    String toHTML()
    {
        String s = "";
        if (inserts > 0) s += " i"+inserts;
        if (updates > 0) s += " u"+updates;
        if (deletes > 0) s += " d"+deletes;
        if (failures > 0) s += " <span class=\"warn\">f"+deletes+"</span>";
        return s.trim();
    }

}
