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
