//
// StatisticsTool.java
//
// This program measures and instruments to obtain different statistics
// about Java programs.
//
// Copyright (c) 1998 by Han B. Lee (hanlee@cs.colorado.edu).
// ALL RIGHTS RESERVED.
//
// Permission to use, copy, modify, and distribute this software and its
// documentation for non-commercial purposes is hereby granted provided
// that this copyright notice appears in all copies.
//
// This software is provided "as is".  The licensor makes no warrenties, either
// expressed or implied, about its correctness or performance.  The licensor
// shall not be liable for any damages suffered as a result of using
// and modifying this software.

package pt.ulisboa.tecnico.cnv.instrumentation;

import BIT.highBIT.*;
import java.io.*;
import java.util.Enumeration;
import java.util.Vector;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class SolverStatistics
{
	// Get class name with package path but separated with forward slashes instead of dots
	private static final String CLASSNAME = SolverStatistics.class.getName().replaceAll("\\.", "/");

	public static class MetricsData {
		// Metrics for instrumentClassFiles()
		long dyn_method_count = 0;
		long dyn_bb_count = 0;
		long dyn_instr_count = 0;

		public void clear() {
			dyn_method_count = 0;
			dyn_bb_count = 0;
			dyn_instr_count = 0;
		}

		public String toString() {
			double instr_per_bb = (double) dyn_instr_count / dyn_bb_count;
			double instr_per_method = (double) dyn_instr_count / dyn_method_count;
			double bb_per_method = (double) dyn_bb_count / dyn_method_count;

			return "Number of methods:      " + dyn_method_count +
				"\nNumber of basic blocks: " + dyn_bb_count +
				"\nNumber of instructions: " + dyn_instr_count +
				"\nAverage number of instructions per basic block: " + instr_per_bb +
				"\nAverage number of instructions per method:      " + instr_per_method +
				"\nAverage number of basic blocks per method:      " + bb_per_method;
		}
	}

	private static final class ThreadLocalMetrics extends ThreadLocal<MetricsData> {
		@Override
		protected MetricsData initialValue() {
			return new MetricsData();
		}
	}

	private static final ThreadLocalMetrics threadLocal = new ThreadLocalMetrics();

	private static void instrumentClassFiles(File in_dir, File out_dir){
		String filelist[] = in_dir.list();

		for (int i = 0; i < filelist.length; i++) {
			String filename = filelist[i];
			if (filename.endsWith(".class")) {
				String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
				String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
				ClassInfo ci = new ClassInfo(in_filename);
				for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
					Routine routine = (Routine) e.nextElement();
					routine.addBefore(CLASSNAME, "dynMethodCount", Integer.valueOf(0));

					for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
						BasicBlock bb = (BasicBlock) b.nextElement();
						bb.addBefore(CLASSNAME, "dynInstrCount", Integer.valueOf(bb.size()));
					}
				}
					ci.write(out_filename);
			}
		}
	}

	public static void dynInstrCount(int incr)
	{
		MetricsData metrics = getMetrics();
		metrics.dyn_instr_count += incr;
		metrics.dyn_bb_count++;
	}

	public static void dynMethodCount(int incr)
	{
		getMetrics().dyn_method_count++;
	}

	public static MetricsData getMetrics() {
		return threadLocal.get();
	}

	private static void printUsage()
	{
		System.out.println("Syntax: java SolverStatistics in_path out_path");
		System.out.println("        in_path:  directory from which the class files are read");
		System.out.println("        out_path: directory to which the class files are written");
		System.exit(-1);
	}

	public static void main(String argv[])
	{
		if (argv.length != 2 ) {
			printUsage();
		}

		File in_dir = new File(argv[0]);
		File out_dir = new File(argv[1]);

		if (in_dir.isDirectory() && out_dir.isDirectory()) {
			instrumentClassFiles(in_dir, out_dir);
		} else {
			printUsage();
		}

	}
}
