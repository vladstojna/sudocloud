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

	public static class MetricsData {
		// Metrics for doDynamic()
		int dyn_method_count = 0;
		int dyn_bb_count = 0;
		int dyn_instr_count = 0;

		public void clear() {
			dyn_method_count = 0;
			dyn_bb_count = 0;
			dyn_instr_count = 0;
		}

		public String toString() {
			double instr_per_bb = (double) dyn_instr_count / dyn_bb_count;
			double instr_per_method = (double) dyn_instr_count / dyn_method_count;
			double bb_per_method = (double) dyn_bb_count / dyn_method_count;

			return "Number of methods:     " + dyn_method_count +
				"\nNumber of basic blocks: " + dyn_bb_count +
				"\nNumber of instructions: " + dyn_instr_count +
				"\nAverage number of instructions per basic block: " + instr_per_bb +
				"\nAverage number of instructions per method:      " + instr_per_method +
				"\nAverage number of basic blocks per method:      " + bb_per_method;
		}
	}

	// threadMapping maps a threadId to its respective local data
	private static ConcurrentHashMap<Long, MetricsData> threadMapping = new ConcurrentHashMap<Long, MetricsData>();


	// FIXME the following is not working since it is infromation
	// collected when instrumenting the class. Which means it won't be
	// acessible when running the instrumented code.

	private static int bb_count; // number of instrumented basic blocks

	public static void printUsage()
	{
		System.out.println("Syntax: java SolverStatistics in_path [out_path]");
		System.out.println("        in_path:  directory from which the class files are read");
		System.out.println("        out_path: directory to which the class files are written");
		System.out.println("        Both in_path and out_path are required unless stat_type is static");
		System.out.println("        in which case only in_path is required");
		System.exit(-1);
	}

	public static void doDynamic(File in_dir, File out_dir){
	String filelist[] = in_dir.list();

	for (int i = 0; i < filelist.length; i++) {
		String filename = filelist[i];
		if (filename.endsWith(".class")) {
			String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
			String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
			ClassInfo ci = new ClassInfo(in_filename);
			for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
				Routine routine = (Routine) e.nextElement();
				routine.addBefore("SolverStatistics", "dynMethodCount", new Integer(1));

				for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
					BasicBlock bb = (BasicBlock) b.nextElement();
					bb.addBefore("SolverStatistics", "dynInstrCount", new Integer(bb.size()));
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
		Long currentThreadId = Thread.currentThread().getId();

		MetricsData metrics = threadMapping.get(currentThreadId);
		if (metrics == null) {
			metrics = new MetricsData();
			threadMapping.put(currentThreadId, metrics);
		}
		return metrics;
	}

	public static MetricsData getMetricsFinal(long threadId) {
		return threadMapping.get(Thread.currentThread().getId());
	}

	/**
	 * Be careful to only call this when you know this thread has been added
	 * otherwise a mighty NullPointerException shall arise
	 */
	public static void clearMetrics(long threadId) {
		threadMapping.get(threadId).clear();
	}

	public static void main(String argv[])
	{
		if (argv.length != 2 ) {
			printUsage();
		}

		try {
			File in_dir = new File(argv[0]);
			File out_dir = new File(argv[1]);

			if (in_dir.isDirectory() && out_dir.isDirectory()) {
				doDynamic(in_dir, out_dir);
			} else {
				printUsage();
			}

		} catch (NullPointerException e) {
			e.printStackTrace();
			printUsage();
		}

	}
}
