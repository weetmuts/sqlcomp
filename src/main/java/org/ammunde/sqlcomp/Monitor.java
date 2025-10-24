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

import java.util.function.Supplier;

public class Monitor
{
    private Thread thread_;
    private Supplier<String> update_;
    private boolean running_;

    public Monitor(Supplier<String> update)
    {
        update_ = update;
        running_ = true;
        thread_ = new Thread(this::go);
        Log.prefixNewline(true);
        thread_.start();
    }

    public void stop()
    {
        running_ = false;
        thread_.interrupt();
        Log.prefixNewline(false);

        String l = "";
        try
        {
            l = update_.get();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        Log.statusFinal(l);
    }

    public void go()
    {
        String l = "";
        while (running_)
        {
            try
            {
                l = update_.get();
            }
            catch (Exception e)
            {
                l = ""+e;
                e.printStackTrace();
            }
            Log.status(l);
            try
            {
                Thread.sleep(1000);
            } catch (InterruptedException e)
            {
            }
        }
    }
}
