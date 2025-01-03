/**
 * 
 */
package com.acertainbookstore.client.workloads;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;

import com.acertainbookstore.utils.BookStoreException;
import com.acertainbookstore.business.Book;
import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.StockBook;

/**
 * 
 * Worker represents the workload runner which runs the workloads with
 * parameters using WorkloadConfiguration and then reports the results
 * 
 */
public class Worker implements Callable<WorkerRunResult> {
    private WorkloadConfiguration configuration = null;
    private int numSuccessfulFrequentBookStoreInteraction = 0;
    private int numTotalFrequentBookStoreInteraction = 0;

    public Worker(WorkloadConfiguration config) {
	configuration = config;
    }

    /**
     * Run the appropriate interaction while trying to maintain the configured
     * distributions
     * 
     * Updates the counts of total runs and successful runs for customer
     * interaction
     * 
     * @param chooseInteraction
     * @return
     */
    private boolean runInteraction(float chooseInteraction) {
	try {
	    float percentRareStockManagerInteraction = configuration.getPercentRareStockManagerInteraction();
	    float percentFrequentStockManagerInteraction = configuration.getPercentFrequentStockManagerInteraction();

	    if (chooseInteraction < percentRareStockManagerInteraction) {
		runRareStockManagerInteraction();
	    } else if (chooseInteraction < percentRareStockManagerInteraction
		    + percentFrequentStockManagerInteraction) {
		runFrequentStockManagerInteraction();
	    } else {
		numTotalFrequentBookStoreInteraction++;
		runFrequentBookStoreInteraction();
		numSuccessfulFrequentBookStoreInteraction++;
	    }
	} catch (BookStoreException ex) {
	    return false;
	}
	return true;
    }

    /**
     * Run the workloads trying to respect the distributions of the interactions
     * and return result in the end
     */
    public WorkerRunResult call() throws Exception {
	int count = 1;
	long startTimeInNanoSecs = 0;
	long endTimeInNanoSecs = 0;
	int successfulInteractions = 0;
	long timeForRunsInNanoSecs = 0;

	Random rand = new Random();
	float chooseInteraction;

	// Perform the warmup runs
	while (count++ <= configuration.getWarmUpRuns()) {
	    chooseInteraction = rand.nextFloat() * 100f;
	    runInteraction(chooseInteraction);
	}

	count = 1;
	numTotalFrequentBookStoreInteraction = 0;
	numSuccessfulFrequentBookStoreInteraction = 0;

	// Perform the actual runs
	startTimeInNanoSecs = System.nanoTime();
	while (count++ <= configuration.getNumActualRuns()) {
	    chooseInteraction = rand.nextFloat() * 100f;
	    if (runInteraction(chooseInteraction)) {
		successfulInteractions++;
	    }
	}
	endTimeInNanoSecs = System.nanoTime();
	timeForRunsInNanoSecs += (endTimeInNanoSecs - startTimeInNanoSecs);
	return new WorkerRunResult(successfulInteractions, timeForRunsInNanoSecs, configuration.getNumActualRuns(),
		numSuccessfulFrequentBookStoreInteraction, numTotalFrequentBookStoreInteraction);
    }

    /**
     * Runs the new stock acquisition interaction
     * 
     * @throws BookStoreException
     */
    private void runRareStockManagerInteraction() throws BookStoreException {
	// Get the list of all books in the store
	List<StockBook> currentBooks = configuration.getStockManager().getBooks();
	
	// Get ISBNs of current books
	Set<Integer> currentISBNs = new HashSet<>();
	for (StockBook book : currentBooks) {
	    currentISBNs.add(book.getISBN());
	}
	
	// Generate a set of new books
	Set<StockBook> candidateBooks = configuration.getBookSetGenerator()
		.nextSetOfStockBooks(configuration.getNumBooksToAdd());
	
	// Filter out books that already exist
	Set<StockBook> booksToAdd = new HashSet<>();
	for (StockBook book : candidateBooks) {
	    if (!currentISBNs.contains(book.getISBN())) {
		booksToAdd.add(book);
	    }
	}
	
	// Add the new books if any were found
	if (!booksToAdd.isEmpty()) {
	    configuration.getStockManager().addBooks(booksToAdd);
	}
    }

    /**
     * Runs the stock replenishment interaction
     * 
     * @throws BookStoreException
     */
    private void runFrequentStockManagerInteraction() throws BookStoreException {
	// Get all books
	List<StockBook> books = configuration.getStockManager().getBooks();
	
	// Sort by number of copies (ascending)
	Collections.sort(books, (a, b) -> Integer.compare(a.getNumCopies(), b.getNumCopies()));
	
	// Take k books with least copies
	int k = configuration.getNumBooksWithLeastCopies();
	k = Math.min(k, books.size());
	List<StockBook> booksToReplenish = books.subList(0, k);
	
	// Create the book copies to add
	Set<BookCopy> bookCopiesToAdd = new HashSet<>();
	for (StockBook book : booksToReplenish) {
	    bookCopiesToAdd.add(new BookCopy(book.getISBN(), configuration.getNumAddCopies()));
	}
	
	// Add the copies
	if (!bookCopiesToAdd.isEmpty()) {
	    configuration.getStockManager().addCopies(bookCopiesToAdd);
	}
    }

    /**
     * Runs the customer interaction
     * 
     * @throws BookStoreException
     */
    private void runFrequentBookStoreInteraction() throws BookStoreException {
	// Get editor picks (returns List<Book>)
	List<Book> editorPicks = configuration.getBookStore().getEditorPicks(
		configuration.getNumEditorPicksToGet());
	
	// Convert to Set for ISBN sampling
	Set<Integer> editorPickISBNs = new HashSet<>();
	for (Book book : editorPicks) {
	    editorPickISBNs.add(book.getISBN());
	}
	
	// Sample some books to buy
	Set<Integer> isbnsToBuy = configuration.getBookSetGenerator()
		.sampleFromSetOfISBNs(editorPickISBNs, configuration.getNumBooksToBuy());
	
	// Create the purchase list
	Set<BookCopy> booksToBuy = new HashSet<>();
	for (Integer isbn : isbnsToBuy) {
	    booksToBuy.add(new BookCopy(isbn, configuration.getNumBookCopiesToBuy()));
	}
	
	// Buy the books
	if (!booksToBuy.isEmpty()) {
	    configuration.getBookStore().buyBooks(booksToBuy);
	}
    }

}
