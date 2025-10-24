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

import java.util.Date;
import java.util.TimeZone;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.ZoneId;
import java.sql.Types;
import java.text.Normalizer;

public class Util
{
    private static String timezone_;

    public static LocalTime localTime()
    {
        if (timezone_ == null)
        {
            timezone_ = System.getenv("SQLCOMP_TIMEZONE");
            if (timezone_ == null)
            {
                timezone_ = "Europe/Stockholm";
            }
        }
        return  LocalTime.now(ZoneId.of(timezone_));
    }

    public static String secondsToHR(long seconds)
    {
        long hours = seconds/3600;
        long minutes = (seconds-hours*3600)/60;
        seconds = seconds-hours*3600-minutes*60;

        String r = "";
        if (hours > 0) r += hours+"h";
        if (minutes > 0) r += minutes+"m";
        r += seconds+"s";

        return r;
    }

    public static String secondsToHRLeft(long sofar, long left)
    {
        long hours = left/3600;
        long minutes = (left-hours*3600)/60;
        left = left-hours*3600-minutes*60;

        String r = "";
        if (hours > 0) r += hours+"h";
        if (minutes > 0) r += minutes+"m";
        if ((left-sofar) < 180) {
            r += left+"s";
        }

        return r;
    }


    public static String jdbcHost(String url)
    {
        // "jdbc:postgresql://localhost/fromcomp"
        int a = url.indexOf("//");
        int b = url.indexOf("/", a+2);
        if (a > 0 && b > a)
        {
            return url.substring(a+2, b);
        }
        return null;
    }

    public static String timestamp()
    {
        localTime();
        TimeZone tz = TimeZone.getTimeZone(timezone_);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'_'HH:mm");
        df.setTimeZone(tz);
        return df.format(new Date());
    }

    public static String rightPad(String s, int width, char pad)
    {
        StringBuilder out = new StringBuilder();
        out.append(s);
        width -= s.length();
        while (width > 0)
        {
            out.append(pad);
            width--;
        }
        return out.toString();
    }

    public static String sizeHR(long s)
    {
        double ds = s;
        double gib = 1024*1024*1024;
        double mib = 1024*1024;
        double kib = 1024;

        if (ds > gib) return String.format("%.2f GiB", ds/gib);
        if (ds > mib) return String.format("%.2f MiB", ds/mib);
        if (ds > kib) return String.format("%.2f KiB", ds/mib);
        return ""+s;
    }

    public static String[] shiftLeft(String[] args)
    {
        if (args.length <= 1) return new String[0];

	String[] tmp = new String[args.length-1];
        for (int i=0; i<args.length-1; ++i)
	{
            tmp[i] = args[i+1];
        }
        return tmp;
    }

    public static boolean defaultNeedsQuotes(int type)
    {
        switch (type) {
        case Types.CHAR:
        case Types.DATE:
        case Types.LONGNVARCHAR:
        case Types.LONGVARCHAR:
        case Types.NCHAR:
        case Types.NVARCHAR:
        case Types.OTHER:
        case Types.SQLXML:
        case Types.TIME:
        case Types.TIME_WITH_TIMEZONE:
        case Types.TIMESTAMP:
        case Types.TIMESTAMP_WITH_TIMEZONE:
        case Types.VARCHAR:
            return true;
        }
        return false;
    }

    static String doubleApostrophes(String s)
    {
        if (s == null) return s;
        StringBuilder out = new StringBuilder();

        char p = 0;
        char c = 0;
        for (int i=0; i < s.length(); ++i)
        {
            p = c;
            c = s.charAt(i);

            if (c == '\'')
            {
                out.append("''");
                continue;
            }
            else if (c == '\\')
            {
                out.append("\\\\");
                continue;
            }
            else if (c > 1000)
            {
                // Ship zero width space. U+200B
                continue;
            }
            else if (c == '¨' || c == 776)
            {
                if (p == 'a') out.append('ä');
                else if (p == 'o') out.append('ö');
                else if (p == 'A') out.append('Ä');
                else if (p == 'O') out.append('Ö');
                else if (p == 'u') out.append('ü');
                else if (p == 'U') out.append('Ü');
                else out.append("?");
                continue;
            }
            else if (c == '°')
            {
                if (p == 'a') {
                    out.append('å');
                    continue;
                }
                // Allow for the use of ° as degrees....  Taklutning 2,5°
            }
            // SQL server cannot store čΩ it changes these into c and O.
            out.append(c);
        }
        s = Normalizer.normalize(s, Normalizer.Form.NFC);

        return out.toString();
    }

}
