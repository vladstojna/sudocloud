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
import java.nio.file.Paths;
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

		long dyn_load_count = 0;
		long dyn_store_count = 0;

		long dyn_arithmetic_count = 0;
		long dyn_constant_count = 0;
		long dyn_stack_count = 0;

		long dyn_new_count = 0;
		long dyn_newarray_count = 0;
		long dyn_anewarray_count = 0;
		long dyn_multianewarray_count = 0;

		public void clear() {
			dyn_method_count = 0;
			dyn_bb_count = 0;
			dyn_instr_count = 0;
			dyn_load_count = 0;
			dyn_store_count = 0;
			dyn_arithmetic_count = 0;
			dyn_constant_count = 0;
			dyn_stack_count = 0;
			dyn_new_count = 0;
			dyn_newarray_count = 0;
			dyn_anewarray_count = 0;
			dyn_multianewarray_count = 0;
		}

		public String toString() {
			double instr_per_bb = (double) dyn_instr_count / dyn_bb_count;
			double instr_per_method = (double) dyn_instr_count / dyn_method_count;
			double bb_per_method = (double) dyn_bb_count / dyn_method_count;

			long total_alloc = dyn_new_count + dyn_newarray_count +
				dyn_anewarray_count + dyn_multianewarray_count;

			return "Methods:      " + dyn_method_count +
				"\nBasic blocks: " + dyn_bb_count +
				"\nInstructions: " + dyn_instr_count +
				"\nLoad instr:   " + dyn_load_count +
				"\nStore instr:  " + dyn_store_count +
				"\nArith instr:  " + dyn_arithmetic_count +
				"\nConst instr:  " + dyn_constant_count +
				"\nStack instr:  " + dyn_stack_count +
				"\nNEW instr:           " + dyn_new_count +
				"\nNew array instr:     " + dyn_newarray_count +
				"\nNew ref array instr: " + dyn_anewarray_count +
				"\nNew md array instr:  " + dyn_multianewarray_count +
				"\nTotal alloc instr:   " + total_alloc +
				"\nAvg instr per basic block:   " + instr_per_bb +
				"\nAvg instr per method:        " + instr_per_method +
				"\nAvg basic blocks per method: " + bb_per_method;
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

				String in_filename = Paths.get(in_dir.getAbsolutePath(), filename).toString();
				String out_filename = Paths.get(out_dir.getAbsolutePath(), filename).toString();

				ClassInfo ci = new ClassInfo(in_filename);

				for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {

					Routine routine = (Routine) e.nextElement();
					routine.addBefore(CLASSNAME, "dynMethodCount", Integer.valueOf(0));

					for (Enumeration instrs = routine.getInstructionArray().elements(); instrs.hasMoreElements(); ) {

						Instruction instr = (Instruction) instrs.nextElement();
						int opcode = instr.getOpcode();

						if (isAllocInstr(opcode)) {
							instr.addBefore(CLASSNAME, "dynAllocCount", Integer.valueOf(opcode));
						}

						short type = InstructionTable.InstructionTypeTable[opcode];
						if (type == InstructionTable.LOAD_INSTRUCTION) {
							instr.addBefore(CLASSNAME, "dynLoadStoreCount", Integer.valueOf(0));
						} else if (type == InstructionTable.STORE_INSTRUCTION) {
							instr.addBefore(CLASSNAME, "dynLoadStoreCount", Integer.valueOf(1));
						} else if (type == InstructionTable.ARITHMETIC_INSTRUCTION) {
							instr.addBefore(CLASSNAME, "dynArithmeticCount", Integer.valueOf(0));
						} else if (type == InstructionTable.CONSTANT_INSTRUCTION) {
							instr.addBefore(CLASSNAME, "dynConstantCount", Integer.valueOf(0));
						} else if (type == InstructionTable.STACK_INSTRUCTION) {
							instr.addBefore(CLASSNAME, "dynStackCount", Integer.valueOf(0));
						}
					}

					for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {

						BasicBlock bb = (BasicBlock) b.nextElement();
						bb.addBefore(CLASSNAME, "dynInstrCount", Integer.valueOf(bb.size()));

					}

				}
					ci.write(out_filename);
			}
		}
	}

	public static boolean isAllocInstr(int opcode) {
		return (opcode == InstructionTable.NEW) ||
			(opcode==InstructionTable.newarray) ||
			(opcode==InstructionTable.anewarray) ||
			(opcode==InstructionTable.multianewarray);
	}

	public static void dynAllocCount(int opcode) {
		MetricsData metrics = getMetrics();
		switch (opcode) {
			case InstructionTable.NEW:
				metrics.dyn_new_count++;
				break;
			case InstructionTable.newarray:
				metrics.dyn_newarray_count++;
				break;
			case InstructionTable.anewarray:
				metrics.dyn_anewarray_count++;
				break;
			case InstructionTable.multianewarray:
				metrics.dyn_multianewarray_count++;
				break;
		}
	}

	public static void dynInstrCount(int incr) {
		MetricsData metrics = getMetrics();
		metrics.dyn_instr_count += incr;
		metrics.dyn_bb_count++;
	}

	public static void dynMethodCount(int incr) {
		getMetrics().dyn_method_count++;
	}

	public static void dynLoadStoreCount(int type) {
		MetricsData metrics = getMetrics();
		if (type == 0) {
			metrics.dyn_load_count++;
		} else {
			metrics.dyn_store_count++;
		}
	}

	public static void dynArithmeticCount(int value) {
		getMetrics().dyn_arithmetic_count++;
	}

	public static void dynConstantCount(int value) {
		getMetrics().dyn_constant_count++;
	}

	public static void dynStackCount(int value) {
		getMetrics().dyn_stack_count++;
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
