package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.acertainbookstore.business.Book;
import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.BookEditorPick;
import com.acertainbookstore.business.SingleLockConcurrentCertainBookStore;
import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.business.TwoLevelLockingConcurrentCertainBookStore;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * {@link BookStoreTest} tests the {@link BookStore} interface.
 * 
 * @see BookStore
 */
public class BookStoreTest {

	/** The Constant TEST_ISBN. */
	private static final int TEST_ISBN = 3044560;

	/** The Constant NUM_COPIES. */
	private static final int NUM_COPIES = 100;

	/** The local test. */
	private static boolean localTest = true;

	/** Single lock test */
	private static boolean singleLock = true;

	
	/** The store manager. */
	private static StockManager storeManager;

	/** The client. */
	private static BookStore client;

	/**
	 * Sets the up before class.
	 */
	@BeforeClass
	public static void setUpBeforeClass() {
		try {
			String localTestProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
			localTest = (localTestProperty != null) ? Boolean.parseBoolean(localTestProperty) : localTest;
			
			String singleLockProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_SINGLE_LOCK);
			singleLock = (singleLockProperty != null) ? Boolean.parseBoolean(singleLockProperty) : singleLock;

			if (localTest) {
				if (singleLock) {
					SingleLockConcurrentCertainBookStore store = new SingleLockConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				} else {
					TwoLevelLockingConcurrentCertainBookStore store = new TwoLevelLockingConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				}
			} else {
				storeManager = new StockManagerHTTPProxy("http://localhost:8081/stock");
				client = new BookStoreHTTPProxy("http://localhost:8081");
			}

			storeManager.removeAllBooks();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Helper method to add some books.
	 *
	 * @param isbn
	 *            the isbn
	 * @param copies
	 *            the copies
	 * @throws BookStoreException
	 *             the book store exception
	 */
	public void addBooks(int isbn, int copies) throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		StockBook book = new ImmutableStockBook(isbn, "Test of Thrones", "George RR Testin'", (float) 10, copies, 0, 0,
				0, false);
		booksToAdd.add(book);
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Helper method to get the default book used by initializeBooks.
	 *
	 * @return the default book
	 */
	public StockBook getDefaultBook() {
		return new ImmutableStockBook(TEST_ISBN, "Harry Potter and JUnit", "JK Unit", (float) 10, NUM_COPIES, 0, 0, 0,
				false);
	}

	/**
	 * Method to add a book, executed before every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Before
	public void initializeBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(getDefaultBook());
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Method to clean up the book store, execute after every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@After
	public void cleanupBooks() throws BookStoreException {
		storeManager.removeAllBooks();
	}

	/**
	 * Tests basic buyBook() functionality.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyAllCopiesDefaultBook() throws BookStoreException {
		// Set of books to buy
		Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES));

		// Try to buy books
		client.buyBooks(booksToBuy);

		List<StockBook> listBooks = storeManager.getBooks();
		assertTrue(listBooks.size() == 1);
		StockBook bookInList = listBooks.get(0);
		StockBook addedBook = getDefaultBook();

		assertTrue(bookInList.getISBN() == addedBook.getISBN() && bookInList.getTitle().equals(addedBook.getTitle())
				&& bookInList.getAuthor().equals(addedBook.getAuthor()) && bookInList.getPrice() == addedBook.getPrice()
				&& bookInList.getNumSaleMisses() == addedBook.getNumSaleMisses()
				&& bookInList.getAverageRating() == addedBook.getAverageRating()
				&& bookInList.getNumTimesRated() == addedBook.getNumTimesRated()
				&& bookInList.getTotalRating() == addedBook.getTotalRating()
				&& bookInList.isEditorPick() == addedBook.isEditorPick());
	}

	/**
	 * Tests that books with invalid ISBNs cannot be bought.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyInvalidISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with invalid ISBN.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(-1, 1)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that books can only be bought if they are in the book store.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNonExistingISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with ISBN which does not exist.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(100000, 10)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy more books than there are copies.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyTooManyBooks() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy more copies than there are in store.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES + 1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy a negative number of books.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNegativeNumberOfBookCopies() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a negative number of copies.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that all books can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetBooks() throws BookStoreException {
		Set<StockBook> booksAdded = new HashSet<StockBook>();
		booksAdded.add(getDefaultBook());

		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		booksAdded.addAll(booksToAdd);

		storeManager.addBooks(booksToAdd);

		// Get books in store.
		List<StockBook> listBooks = storeManager.getBooks();

		// Make sure the lists equal each other.
		assertTrue(listBooks.containsAll(booksAdded) && listBooks.size() == booksAdded.size());
	}

	/**
	 * Tests that a list of books with a certain feature can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetCertainBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		storeManager.addBooks(booksToAdd);

		// Get a list of ISBNs to retrieved.
		Set<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN + 1);
		isbnList.add(TEST_ISBN + 2);

		// Get books with that ISBN.
		List<Book> books = client.getBooks(isbnList);

		// Make sure the lists equal each other
		assertTrue(books.containsAll(booksToAdd) && books.size() == booksToAdd.size());
	}

	/**
	 * Tests that books cannot be retrieved if ISBN is invalid.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetInvalidIsbn() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Make an invalid ISBN.
		HashSet<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN); // valid
		isbnList.add(-1); // invalid

		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.getBooks(isbnList);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Test 1 (Concurrency):
	 * Two clients (C1 and C2) concurrently operate on the same book:
	 * - C1 repeatedly buys 1 copy
	 * - C2 repeatedly adds 1 copy
	 * After a fixed number of operations, the stock should remain the same as initially.
	 */
	@Test
	public void testConcurrentBuyAndAddCopies() throws Exception {
		final int operationsPerThread = 50;
		final CountDownLatch startLatch = new CountDownLatch(1);
		final CountDownLatch doneLatch = new CountDownLatch(2);

		Runnable buyer = () -> {
			try {
				startLatch.await();
				for (int i = 0; i < operationsPerThread; i++) {
					Set<BookCopy> toBuy = new HashSet<>();
					toBuy.add(new BookCopy(TEST_ISBN, 1));
					client.buyBooks(toBuy);
				}
			} catch (Exception e) {
				fail("Buyer thread exception: " + e.getMessage());
			} finally {
				doneLatch.countDown();
			}
		};

		Runnable adder = () -> {
			try {
				startLatch.await();
				for (int i = 0; i < operationsPerThread; i++) {
					Set<BookCopy> toAdd = new HashSet<>();
					toAdd.add(new BookCopy(TEST_ISBN, 1));
					storeManager.addCopies(toAdd);
				}
			} catch (Exception e) {
				fail("Adder thread exception: " + e.getMessage());
			} finally {
				doneLatch.countDown();
			}
		};

		new Thread(buyer).start();
		new Thread(adder).start();

		startLatch.countDown();
		doneLatch.await();

		// After equal number of buys and adds, stock should remain the same
		List<StockBook> finalBooks = storeManager.getBooks();
		StockBook book = finalBooks.get(0);
		assertEquals("Final stock should match initial stock", NUM_COPIES, book.getNumCopies());
	}


	/**
	 * Test 2 (Concurrency):
	 * One thread (C1) cycles the stock of a book between full (NUM_COPIES) and NUM_COPIES-1
	 * by buying and then replenishing.
	 * Another thread (C2) continuously calls getBooks to ensure snapshots are always consistent
	 * (all at NUM_COPIES or all at NUM_COPIES-1, never partial).
	 */
	@Test
	public void testConsistentSnapshotsDuringBuyAndReplenish() throws Exception {
		final int iterations = 50;
		final AtomicBoolean stopFlag = new AtomicBoolean(false);
		final CountDownLatch startLatch = new CountDownLatch(1);
		final CountDownLatch doneLatch = new CountDownLatch(2);

		Runnable stateCycler = () -> {
			try {
				startLatch.await();
				for (int i = 0; i < iterations; i++) {
					Set<BookCopy> toBuy = new HashSet<>();
					toBuy.add(new BookCopy(TEST_ISBN, 1));
					client.buyBooks(toBuy); // now NUM_COPIES - 1

					Set<BookCopy> toAdd = new HashSet<>();
					toAdd.add(new BookCopy(TEST_ISBN, 1));
					storeManager.addCopies(toAdd); // back to NUM_COPIES
				}
			} catch (Exception e) {
				fail("StateCycler failed: " + e.getMessage());
			} finally {
				stopFlag.set(true);
				doneLatch.countDown();
			}
		};

		Runnable checker = () -> {
			try {
				startLatch.await();
				while (!stopFlag.get()) {
					List<StockBook> snapshot = storeManager.getBooks();
					int copies = snapshot.get(0).getNumCopies();
					assertTrue("Copies should be either full or full-1",
							copies == NUM_COPIES || copies == NUM_COPIES - 1);
				}
			} catch (Exception e) {
				fail("Checker failed: " + e.getMessage());
			} finally {
				doneLatch.countDown();
			}
		};

		new Thread(stateCycler).start();
		new Thread(checker).start();

		startLatch.countDown();
		doneLatch.await();
	}

    /**
     * Test 3 (Additional):
     * Multiple readers and writers:
     * Readers continuously call getBooks and getBooksByISBN,
     * Writers alternate between buyBooks and updateEditorPicks.
     */
    @Test
    public void testMultipleReadersAndWriters() throws Exception {
        final int READERS = 3;
        final int WRITERS = 2;
        final int ITERATIONS = 30;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(READERS + WRITERS);

        Set<Integer> isbnSet = new HashSet<>();
        isbnSet.add(TEST_ISBN);

        Runnable reader = () -> {
            try {
                startLatch.await();
                for (int i = 0; i < ITERATIONS; i++) {
                    List<StockBook> allBooks = storeManager.getBooks();
                    assertNotNull(allBooks);

                    List<StockBook> certainBooks = storeManager.getBooksByISBN(isbnSet);
                    assertNotNull(certainBooks);
                    assertFalse(certainBooks.isEmpty());
                }
            } catch (Exception e) {
                fail("Reader failed: " + e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        };

        Runnable writer = () -> {
            try {
                startLatch.await();
                for (int i = 0; i < ITERATIONS; i++) {
                    if (i % 2 == 0) {
                        // buy one copy
                        Set<BookCopy> toBuy = new HashSet<>();
                        toBuy.add(new BookCopy(TEST_ISBN, 1));
                        client.buyBooks(toBuy);
                    } else {
                        // update editor picks
                        Set<BookEditorPick> picks = new HashSet<>();
                        picks.add(new BookEditorPick(TEST_ISBN, true));
                        storeManager.updateEditorPicks(picks);
                    }
                }
            } catch (Exception e) {
                fail("Writer failed: " + e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        };

        for (int i = 0; i < READERS; i++) {
            new Thread(reader).start();
        }
        for (int i = 0; i < WRITERS; i++) {
            new Thread(writer).start();
        }

        startLatch.countDown();
        doneLatch.await();

        // Just ensure no exceptions were thrown and final state is consistent
        List<StockBook> finalBooks = storeManager.getBooks();
        assertFalse(finalBooks.isEmpty());
    }

    /**
     * Test 4 (Additional):
     * Concurrently add and remove books while other threads are buying and reading.
     * Ensures no invalid states or exceptions occur.
     */
    @Test
    public void testConcurrentAddRemoveAndAccess() throws Exception {
        // Add extra book for complexity
        int extraISBN = TEST_ISBN + 100;
        storeManager.addBooks(Collections.singleton(new ImmutableStockBook(extraISBN, "Extra Book", "Author", 10f, 5, 0, 0, 0, false)));

        final int ADDERS = 1;
        final int REMOVERS = 1;
        final int BUYERS = 1;
        final int READERS = 1;
        final int TOTAL = ADDERS + REMOVERS + BUYERS + READERS;
        final int ITERATIONS = 20;

        final Set<Integer> dynamicISBNs = Collections.synchronizedSet(new HashSet<>(Arrays.asList(TEST_ISBN, extraISBN)));
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(TOTAL);

        Runnable adder = () -> {
            try {
                startLatch.await();
                for (int i = 0; i < ITERATIONS; i++) {
                    int newIsbn = TEST_ISBN + 200 + i;
                    storeManager.addBooks(Collections.singleton(new ImmutableStockBook(newIsbn, "NewBook" + newIsbn, "A", 5f, 5, 0, 0, 0, false)));
                    dynamicISBNs.add(newIsbn);
                }
            } catch (Exception e) {
                fail("Adder failed: " + e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        };

        Runnable remover = () -> {
            try {
                startLatch.await();
                for (int i = 0; i < ITERATIONS; i++) {
                    synchronized (dynamicISBNs) {
                        if (!dynamicISBNs.isEmpty()) {
                            Integer[] arr = dynamicISBNs.toArray(new Integer[0]);
                            Integer toRemove = arr[new Random().nextInt(arr.length)];
                            try {
                                storeManager.removeBooks(Collections.singleton(toRemove));
                                dynamicISBNs.remove(toRemove);
                            } catch (BookStoreException ignore) {
                                // might fail if book not found at removal time
                            }
                        }
                    }
                }
            } catch (Exception e) {
                fail("Remover failed: " + e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        };

        Runnable buyer = () -> {
            try {
                startLatch.await();
                for (int i = 0; i < ITERATIONS; i++) {
                    Set<BookCopy> toBuy = new HashSet<>();
                    synchronized (dynamicISBNs) {
                        for (Integer isbn : dynamicISBNs) {
                            toBuy.add(new BookCopy(isbn, 1));
                        }
                    }
                    if (!toBuy.isEmpty()) {
                        try {
                            client.buyBooks(toBuy);
                        } catch (BookStoreException ignore) {
                            // If not enough copies, no problem
                        }
                    }
                }
            } catch (Exception e) {
                fail("Buyer failed: " + e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        };

        Runnable reader = () -> {
            try {
                startLatch.await();
                for (int i = 0; i < ITERATIONS; i++) {
                    List<StockBook> snapshot = storeManager.getBooks();
                    for (StockBook b : snapshot) {
                        assertTrue("Negative copies found!", b.getNumCopies() >= 0);
                    }
                }
            } catch (Exception e) {
                fail("Reader failed: " + e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        };

        new Thread(adder).start();
        new Thread(remover).start();
        new Thread(buyer).start();
        new Thread(reader).start();

        startLatch.countDown();
        doneLatch.await();

        // If we got here, no exceptions or inconsistencies were caught
        List<StockBook> finalSnapshot = storeManager.getBooks();
        for (StockBook b : finalSnapshot) {
            assertTrue("Negative copies found in the end!", b.getNumCopies() >= 0);
        }
    }


	@AfterClass
	public static void tearDownAfterClass() throws BookStoreException {
		storeManager.removeAllBooks();

		if (!localTest) {
			((BookStoreHTTPProxy) client).stop();
			((StockManagerHTTPProxy) storeManager).stop();
		}
	}
}
