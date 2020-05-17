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

public class SolverStatistics
{
	// Get class name with package path but separated with forward slashes instead of dots
	private static final String CLASSNAME = SolverStatistics.class.getName().replaceAll("\\.", "/");

	public static class MetricsData {
		private long dyn_ls_count;
		private long dyn_alloc_count;
		private long dyn_condbranch_count;
		private long dyn_other_instr_count;

		private void clear() {
			dyn_ls_count = 0;
			dyn_alloc_count = 0;
			dyn_condbranch_count = 0;
			dyn_other_instr_count = 0;
		}

		public long computeCostAndClear() {
			long cost = computeCost();
			clear();
			return cost;
		}

		private long computeCost() {
			return 1000 * dyn_alloc_count +
				5 * dyn_ls_count +
				4 * dyn_condbranch_count +
				3 * dyn_other_instr_count;
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
					InstructionArray instructions = routine.getInstructionArray();

					for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {

						BasicBlock bb = (BasicBlock) b.nextElement();

						int lsCount = 0;
						int allocCount = 0;
						int condCount = 0;
						int otherCount = 0;

						int startAddress = bb.getStartAddress();
						int endAddress = bb.getEndAddress();

						Instruction instr = (Instruction) instructions.elementAt(endAddress);
						if (isConditionalInstr(instr)) {
							condCount = 1;
						}

						for (int addr = startAddress; addr < endAddress; addr++) {
							Instruction inner = (Instruction) instructions.elementAt(addr);
							if (isAllocInstr(inner)) {
								allocCount++;
							} else if (isLoadStoreInstr(inner)) {
								lsCount++;
							}
						}

						otherCount = bb.size() - (allocCount + lsCount + condCount);

						if (allocCount > 0)
							bb.addBefore(CLASSNAME, "dynAllocCount", Integer.valueOf(allocCount));
						if (lsCount > 0)
							bb.addBefore(CLASSNAME, "dynLoadStoreCount", Integer.valueOf(lsCount));
						if (condCount == 1)
							bb.addBefore(CLASSNAME, "dynCondBranchCount", Integer.valueOf(0));
						if (otherCount > 0)
							bb.addBefore(CLASSNAME, "dynOtherInstrCount", Integer.valueOf(otherCount));

					}

				}
				ci.write(out_filename);
			}
		}
	}

	public static boolean isLoadStoreInstr(Instruction instr) {
		int opcode = instr.getOpcode();
		short type = InstructionTable.InstructionTypeTable[opcode];
		return (opcode == InstructionTable.getfield) ||
			(opcode == InstructionTable.putfield) ||
			(type == InstructionTable.LOAD_INSTRUCTION) ||
			(type == InstructionTable.STORE_INSTRUCTION);

	}

	public static boolean isAllocInstr(Instruction instr) {
		int opcode = instr.getOpcode();
		return (opcode == InstructionTable.NEW) ||
			(opcode == InstructionTable.newarray) ||
			(opcode == InstructionTable.anewarray) ||
			(opcode == InstructionTable.multianewarray);
	}

	public static boolean isConditionalInstr(Instruction instr) {
		short type = InstructionTable.InstructionTypeTable[instr.getOpcode()];
		return type == InstructionTable.CONDITIONAL_INSTRUCTION;
	}

	public static void dynAllocCount(int count) {
		getMetrics().dyn_alloc_count += count;
	}

	public static void dynLoadStoreCount(int count) {
		getMetrics().dyn_ls_count += count;
	}

	public static void dynCondBranchCount(int count) {
		getMetrics().dyn_condbranch_count++;
	}

	public static void dynOtherInstrCount(int count) {
		getMetrics().dyn_other_instr_count += count;
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
