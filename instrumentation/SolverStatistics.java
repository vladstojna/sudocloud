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

import BIT.highBIT.*;
//import java.io.File;
import java.io.*;
import java.util.Enumeration;
import java.util.Vector;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SolverStatistics
{
    private class MetricsData {
	int dyn_method_count = 0;
	int dyn_bb_count = 0;
	int dyn_instr_count = 0;
    }

    private static int dyn_method_count = 0;
    private static int dyn_bb_count = 0;
    private static int dyn_instr_count = 0;

    private static final int MAX_THREADS = 33; // 32 + main thread

    // threadMapping maps a threadId to its respective local data
    private static MetricsData[] metrics;

    public static List<PrintStream> setMetricsFileToOutput() {
        // Save original System.out
        PrintStream standardOutput = System.out;
        
        PrintStream ps = null;
        try {
            // Create file to redirect output
            File outputFile = new File(System.getProperty("user.home") + "/metrics.txt");
            outputFile.createNewFile(); // if file exists, it will do nothing
            FileOutputStream fileOutputStream = new FileOutputStream(outputFile, true);
            ps = new PrintStream(fileOutputStream);
            
            // Redirect output to file
            System.setOut(ps);
            List<PrintStream> result = new ArrayList<>();
            result.add(standardOutput);
            result.add(ps);
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static void closeMetricsFileToOutput(List<PrintStream> printStreams) {
        if (printStreams != null) {
            // Redirect output to standard output again
            System.setOut(printStreams.get(0));
            // Close PrintStream to file
            if (printStreams.get(1) != null) {
                printStreams.get(1).flush();
                printStreams.get(1).close();
            }
        }
    }
    
    public static void printUsage() 
    {
	System.out.println("Syntax: java SolverStatistics in_path [out_path]");
	System.out.println("        in_path:  directory from which the class files are read");
	System.out.println("        out_path: directory to which the class files are written");
	System.out.println("        Both in_path and out_path are required unless stat_type is static");
	System.out.println("        in which case only in_path is required");                
	System.exit(-1);
    }

    public static void doDynamic(File in_dir, File out_dir) 
    {
	String filelist[] = in_dir.list();
	
	for (int i = 0; i < filelist.length; i++) {
	    String filename = filelist[i];
	    if (filename.endsWith(".class")) {
		String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
		String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
		ClassInfo ci = new ClassInfo(in_filename);
		for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
		    Routine routine = (Routine) e.nextElement();
		    if (routine.getMethodName().equals("solveSudoku")) {
			routine.addBefore("SolverStatistics", "metricsInit", "null");
			routine.addAfter("SolverStatistics", "printDynamic", "null");
		    } else {
			routine.addBefore("SolverStatistics", "dynMethodCount", new Integer(1));
                    
			for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
			    BasicBlock bb = (BasicBlock) b.nextElement();
			    bb.addBefore("SolverStatistics", "dynInstrCount", new Integer(bb.size()));
			}
		    }
		}
		//ci.addAfter("SolverStatistics", "printDynamic", "null");
		ci.write(out_filename);
	    }
	}
    }

   
    public static synchronized void printDynamic(String foo) 
    {
	List<PrintStream> printStreams = setMetricsFileToOutput();
	
	System.out.println("Dynamic information summary:");
	System.out.println("Number of methods:      " + dyn_method_count);
	System.out.println("Number of basic blocks: " + dyn_bb_count);
	System.out.println("Number of instructions: " + dyn_instr_count);
	
	if (dyn_method_count == 0) {
	    closeMetricsFileToOutput(printStreams);
	    return;
	}
	
	float instr_per_bb = (float) dyn_instr_count / (float) dyn_bb_count;
	float instr_per_method = (float) dyn_instr_count / (float) dyn_method_count;
	float bb_per_method = (float) dyn_bb_count / (float) dyn_method_count;
	
	System.out.println("Average number of instructions per basic block: " + instr_per_bb);
	System.out.println("Average number of instructions per method:      " + instr_per_method);
	System.out.println("Average number of basic blocks per method:      " + bb_per_method);
	
	closeMetricsFileToOutput(printStreams);
    }

    // FIXME remove synchronized
    public static synchronized void metricsInit(String foo) {
	if (metrics == null) {
	    metrics = new MetricsData[MAX_THREADS];
	}
    }
    
    
    public static synchronized void dynInstrCount(int incr) 
    {
	dyn_instr_count += incr;
	dyn_bb_count++;
    }
    
    public static synchronized void dynMethodCount(int incr) 
    {
	// FIXME beware unsafe downcasting and index out bounds possible
	metrics[(int) Thread.currentThread().getId()].dyn_method_count++; // FIXME problematic line
	System.out.println("thread ID: " + Thread.currentThread().getId());
	dyn_method_count++;
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
		printUsage();
	}

    }
}
