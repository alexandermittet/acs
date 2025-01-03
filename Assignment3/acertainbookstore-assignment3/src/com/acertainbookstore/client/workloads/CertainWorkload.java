/**
 * 
 */
package com.acertainbookstore.client.workloads;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Set;

import com.acertainbookstore.business.CertainBookStore;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;
import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;

/**
 * 
 * CertainWorkload class runs the workloads by different workers concurrently.
 * It configures the environment for the workers using WorkloadConfiguration
 * objects and reports the metrics
 * 
 */
public class CertainWorkload {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		int numConcurrentWorkloadThreads = 10;
		String serverAddress = "http://localhost:8081";
		boolean localTest = true;
		List<WorkerRunResult> workerRunResults = new ArrayList<WorkerRunResult>();
		List<Future<WorkerRunResult>> runResults = new ArrayList<Future<WorkerRunResult>>();

		// Initialize the RPC interfaces if its not a localTest, the variable is
		// overriden if the property is set
		String localTestProperty = System
				.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
		localTest = (localTestProperty != null) ? Boolean
				.parseBoolean(localTestProperty) : localTest;

		BookStore bookStore = null;
		StockManager stockManager = null;
		if (localTest) {
			CertainBookStore store = new CertainBookStore();
			bookStore = store;
			stockManager = store;
		} else {
			stockManager = new StockManagerHTTPProxy(serverAddress + "/stock");
			bookStore = new BookStoreHTTPProxy(serverAddress);
		}

		// Generate data in the bookstore before running the workload
		initializeBookStoreData(bookStore, stockManager);

		ExecutorService exec = Executors
				.newFixedThreadPool(numConcurrentWorkloadThreads);

		for (int i = 0; i < numConcurrentWorkloadThreads; i++) {
			WorkloadConfiguration config = new WorkloadConfiguration(bookStore,
					stockManager);
			Worker workerTask = new Worker(config);
			// Keep the futures to wait for the result from the thread
			runResults.add(exec.submit(workerTask));
		}

		// Get the results from the threads using the futures returned
		for (Future<WorkerRunResult> futureRunResult : runResults) {
			WorkerRunResult runResult = futureRunResult.get(); // blocking call
			workerRunResults.add(runResult);
		}

		exec.shutdownNow(); // shutdown the executor

		// Finished initialization, stop the clients if not localTest
		if (!localTest) {
			((BookStoreHTTPProxy) bookStore).stop();
			((StockManagerHTTPProxy) stockManager).stop();
		}

		reportMetric(workerRunResults);
	}

	/**
	 * Computes the metrics and prints them
	 * 
	 * @param workerRunResults
	 */
	public static void reportMetric(List<WorkerRunResult> workerRunResults) {
		// Aggregate metrics
		long totalSuccessfulInteractions = 0;
		long totalInteractions = 0;
		long totalSuccessfulClientInteractions = 0;
		long totalClientInteractions = 0;
		long totalTimeInNanoSecs = 0;
		
		// Collect data from all workers
		for (WorkerRunResult result : workerRunResults) {
			totalSuccessfulInteractions += result.getSuccessfulInteractions();
			totalInteractions += result.getTotalRuns();
			totalSuccessfulClientInteractions += result.getSuccessfulFrequentBookStoreInteractionRuns();
			totalClientInteractions += result.getTotalFrequentBookStoreInteractionRuns();
			totalTimeInNanoSecs += result.getElapsedTimeInNanoSecs();
		}
		
		// Calculate metrics
		double averageTimeInSeconds = totalTimeInNanoSecs / (1000000000.0 * workerRunResults.size());
		
		// Calculate throughput (successful client interactions per second)
		double throughput = totalSuccessfulClientInteractions / averageTimeInSeconds;
		
		// Calculate average latency in milliseconds
		double latency = (totalTimeInNanoSecs / 1000000.0) / totalSuccessfulClientInteractions;
		
		// Calculate percentages
		double successRate = (totalSuccessfulInteractions * 100.0) / totalInteractions;
		double clientInteractionPercentage = (totalClientInteractions * 100.0) / totalInteractions;
		
		// Print detailed results
		System.out.println("\n=== Performance Metrics ===");
		System.out.println("Total Workers: " + workerRunResults.size());
		System.out.printf("Success Rate: %.2f%%%n", successRate);
		System.out.printf("Client Interaction Percentage: %.2f%% (target: 60%%)%n", 
				clientInteractionPercentage);
		System.out.printf("Throughput: %.2f successful client interactions/second%n", throughput);
		System.out.printf("Average Latency: %.2f ms%n", latency);
		
		// Print warning if metrics are outside desired ranges
		if (successRate < 99.0) {
			System.out.println("\nWARNING: Success rate is below 99% - too many failed interactions");
		}
		if (Math.abs(clientInteractionPercentage - 60.0) > 5.0) {
			System.out.println("\nWARNING: Client interaction percentage significantly differs from 60%");
		}
	}

	/**
	 * Generate the data in bookstore before the workload interactions are run
	 * 
	 * Ignores the serverAddress if its a localTest
	 * 
	 */
	public static void initializeBookStoreData(BookStore bookStore,
			StockManager stockManager) throws BookStoreException {
		
		// TODO: You should initialize data for your bookstore here
		
		BookSetGenerator generator = new BookSetGenerator();
		
		// Initialize with 100 books
		int initializeNumBooks = 100;
		
		// Generate initial set of books
		Set<StockBook> booksToAdd = generator.nextSetOfStockBooks(initializeNumBooks);
		
		// Add the books to the store
		stockManager.addBooks(booksToAdd);
	}
}
