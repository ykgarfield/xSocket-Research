/*
 * Copyright (c) xlightweb.org, 2006 - 2010. All rights reserved.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Please refer to the LGPL license at: http://www.gnu.org/copyleft/lesser.txt
 * The latest copy of this software may be found on http://www.xsocket.org/
 */
package org.xsocket;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


/**
 * helper class to collect and print statistics
 * 
 * @author grro
 */
public final class Statistic {

	private static final int SECTION_TIMERANGE = 30 * 1000; 
	
	private final ArrayList<Integer> times = new ArrayList<Integer>();
	private ArrayList<Integer> tempTimes = new ArrayList<Integer>();
	private long lastPrint = System.currentTimeMillis();
	
	private long lastSection = System.currentTimeMillis();

	private Timer timer = new Timer(true);
	private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
	
	private boolean firstPrint = true;
	
	public Statistic(boolean autoprint) {
		
		if (autoprint) {
			try {
				TimerTask timerTask = new TimerTask() {
					@Override
					public void run() {
						System.out.println(print());
					}
				};
				
				timer.schedule(timerTask, 5000, 5000);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private String printHeader() {
		return "time; throughput; min; max; average; median; p90; p95; p99";
	}

	
	
	public synchronized void addValue(int value) {
		times.add(value);
		tempTimes.add(value);
	}
	
	
	@SuppressWarnings("unchecked")
	public String print() {
		StringBuilder sb = new StringBuilder();
		
		ArrayList<Integer> copy = null;
		synchronized (this) {
			copy = (ArrayList<Integer>) times.clone();	
		}
		
		Collections.sort(copy);
		
		int sum = 0;
		for (Integer i : copy) {
			sum += i;
		}

		if (firstPrint) {
			firstPrint = false;
			sb.append("\r\n" + printHeader() + "\r\n");
		}
		
		
		sb.append(dateFormat.format(new Date())  + "; ");

		ArrayList<Integer> tempTimesCopy = tempTimes;
		tempTimes = new ArrayList<Integer>();
		long elapsed = System.currentTimeMillis() - lastPrint;
		lastPrint = System.currentTimeMillis();
		sb.append(((tempTimesCopy.size() * 1000) / elapsed) + "; ");
		
		
		if (copy.size() > 0) {
			sb.append(copy.get(0) + "; ");
			sb.append(copy.get(copy.size() - 1) + "; ");
			sb.append((sum / copy.size()) + "; ");
			sb.append(copy.get(copy.size() / 2) + "; ");
			sb.append(copy.get((int) (copy.size() * 0.9)) + "; ");
			sb.append(copy.get((int) (copy.size() * 0.95)) + "; ");
			sb.append(copy.get((int) (copy.size() * 0.99)));
		}

		if (System.currentTimeMillis() > (lastSection + SECTION_TIMERANGE)) {
			lastSection = System.currentTimeMillis();
			times.clear();
			sb.append("\r\n\r\n");
			sb.append(printHeader());
		}
		
		return sb.toString();
	}
	
}

