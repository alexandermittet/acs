package com.acertainbookstore.client.workloads;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;

/**
 * Helper class to generate stockbooks and isbns modelled similar to Random
 * class
 */
public class BookSetGenerator {
	private Random random;

	public BookSetGenerator() {
		// TODO Auto-generated constructor stub
		this.random = new Random();
	}

	/**
	 * Returns num randomly selected isbns from the input set
	 * 
	 * @param num
	 * @return
	 */
	public Set<Integer> sampleFromSetOfISBNs(Set<Integer> isbns, int num) {
		if (num > isbns.size()) {
			return new HashSet<>(isbns);
		}
		
		List<Integer> isbnList = new ArrayList<>(isbns);
		Set<Integer> sampledISBNs = new HashSet<>();
		
		while (sampledISBNs.size() < num) {
			int randomIndex = random.nextInt(isbnList.size());
			sampledISBNs.add(isbnList.get(randomIndex));
		}
		
		return sampledISBNs;
	}

	/**
	 * Return num stock books. For now return an ImmutableStockBook
	 * 
	 * @param num
	 * @return
	 */
	public Set<StockBook> nextSetOfStockBooks(int num) {
		Set<StockBook> books = new HashSet<>();
		
		for (int i = 0; i < num; i++) {
			// Generate ISBN ensuring it's unique (using current time + random number)
			int isbn = Math.abs(random.nextInt(100000) + (int) System.currentTimeMillis() % 100000);
			
			// Generate random book properties
			String title = "Book " + isbn;
			String author = "Author " + random.nextInt(1000);
			float price = 10.0f + random.nextFloat() * 90.0f; // Price between 10 and 100
			int numCopies = 10 + random.nextInt(91); // Copies between 10 and 100
			long saleMisses = 0; // New books start with 0 sale misses
			long timesRated = 0; // New books start with 0 ratings
			long totalRating = 0; // New books start with 0 total rating
			boolean isEditorPick = random.nextBoolean();

			books.add(new ImmutableStockBook(isbn, title, author, price, numCopies,
					saleMisses, timesRated, totalRating, isEditorPick));
		}
		
		return books;
	}
}
