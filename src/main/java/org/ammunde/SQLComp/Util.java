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

import java.util.Date;
import java.util.TimeZone;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class Util
{
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
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
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

}
