package com.acertainbookstore.business;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;
import com.acertainbookstore.utils.BookStoreUtility;

/** {@link TwoLevelLockingConcurrentCertainBookStore} implements the {@link BookStore} and
 * {@link StockManager} functionalities.
 * 
 * @see BookStore
 * @see StockManager
 */
public class TwoLevelLockingConcurrentCertainBookStore implements BookStore, StockManager {

    /** The mapping of books from ISBN to {@link BookStoreBook}. */
    private Map<Integer, BookStoreBook> bookMap = null;

    // Global lock for structural changes (intention lock)
    private final ReadWriteLock globalLock = new ReentrantReadWriteLock();

    // Per-book locks
    private final Map<Integer, ReadWriteLock> bookLocks = new ConcurrentHashMap<>();

    public TwoLevelLockingConcurrentCertainBookStore() {
        bookMap = new HashMap<>();
    }

    private void validate(StockBook book) throws BookStoreException {
        int isbn = book.getISBN();
        String bookTitle = book.getTitle();
        String bookAuthor = book.getAuthor();
        int noCopies = book.getNumCopies();
        float bookPrice = book.getPrice();

        if (BookStoreUtility.isInvalidISBN(isbn)) {
            throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.INVALID);
        }

        if (BookStoreUtility.isEmpty(bookTitle)) {
            throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
        }

        if (BookStoreUtility.isEmpty(bookAuthor)) {
            throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
        }

        if (BookStoreUtility.isInvalidNoCopies(noCopies)) {
            throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
        }

        if (bookPrice < 0.0) {
            throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
        }

        if (bookMap.containsKey(isbn)) {
            throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.DUPLICATED);
        }
    }
	
    private void validate(BookCopy bookCopy) throws BookStoreException {
        int isbn = bookCopy.getISBN();
        int numCopies = bookCopy.getNumCopies();

        validateISBNInStock(isbn);

        if (BookStoreUtility.isInvalidNoCopies(numCopies)) {
            throw new BookStoreException(BookStoreConstants.NUM_COPIES + numCopies + BookStoreConstants.INVALID);
        }
    }
	
    private void validate(BookEditorPick editorPickArg) throws BookStoreException {
        int isbn = editorPickArg.getISBN();
        validateISBNInStock(isbn);
    }

    private void validateISBNInStock(Integer ISBN) throws BookStoreException {
        if (BookStoreUtility.isInvalidISBN(ISBN)) {
            throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
        }
        if (!bookMap.containsKey(ISBN)) {
            throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addBooks(java.util.Set)
	 */
    @Override
    public void addBooks(Set<StockBook> bookSet) throws BookStoreException {
        globalLock.writeLock().lock();
        try {
            if (bookSet == null) {
                throw new BookStoreException(BookStoreConstants.NULL_INPUT);
            }

            for (StockBook book : bookSet) {
                validate(book);
            }

            for (StockBook book : bookSet) {
                int isbn = book.getISBN();
                bookMap.put(isbn, new BookStoreBook(book));
                bookLocks.put(isbn, new ReentrantReadWriteLock());
            }
        } finally {
            globalLock.writeLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addCopies(java.util.Set)
	 */
    @Override
    public void addCopies(Set<BookCopy> bookCopiesSet) throws BookStoreException {
        if (bookCopiesSet == null) {
            throw new BookStoreException(BookStoreConstants.NULL_INPUT);
        }

        globalLock.readLock().lock();
        List<ReadWriteLock> acquiredLocks = new ArrayList<>();
        try {
            for (BookCopy bookCopy : bookCopiesSet) {
                validate(bookCopy);
            }

            // Acquire locks in sorted order of ISBN to avoid deadlock
            List<Integer> isbns = bookCopiesSet.stream().map(BookCopy::getISBN).sorted().collect(Collectors.toList());

            for (Integer isbn : isbns) {
                ReadWriteLock lock = bookLocks.get(isbn);
                lock.writeLock().lock();
                acquiredLocks.add(lock);
            }

            for (BookCopy bookCopy : bookCopiesSet) {
                BookStoreBook book = bookMap.get(bookCopy.getISBN());
                book.addCopies(bookCopy.getNumCopies());
            }
        } finally {
            for (ReadWriteLock l : acquiredLocks) {
                l.writeLock().unlock();
            }
            globalLock.readLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooks()
	 */
    @Override
    public List<StockBook> getBooks() {
        globalLock.readLock().lock();
        try {
            // This is a read-only operation over all books
            // Acquire all locks in read mode? Not strictly necessary if we consider that no structural changes can happen while we hold global read.
            // But to be safe and consistent, we won't acquire individual locks here since we're just reading the entire collection and no single item is partial-updated without its lock.
            // The book objects are thread-safe for reading immutable fields directly as implemented (immutableStockBook creates a snapshot).
            Collection<BookStoreBook> values = bookMap.values();
            // It's consistent because we hold global read lock, so no structural changes occur and no writes to individual books will conflict due to them needing write locks.
            return values.stream().map(BookStoreBook::immutableStockBook).collect(Collectors.toList());
        } finally {
            globalLock.readLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#updateEditorPicks(java.util
	 * .Set)
	 */
    @Override
    public void updateEditorPicks(Set<BookEditorPick> editorPicks) throws BookStoreException {
        if (editorPicks == null) {
            throw new BookStoreException(BookStoreConstants.NULL_INPUT);
        }
        globalLock.readLock().lock();
        List<ReadWriteLock> acquiredLocks = new ArrayList<>();
        try {
            for (BookEditorPick editorPickArg : editorPicks) {
                validate(editorPickArg);
            }

            // Lock all affected books in a defined order
            List<Integer> isbns = editorPicks.stream().map(BookEditorPick::getISBN).sorted().collect(Collectors.toList());

            for (Integer isbn : isbns) {
                ReadWriteLock lock = bookLocks.get(isbn);
                lock.writeLock().lock();
                acquiredLocks.add(lock);
            }

            for (BookEditorPick editorPickArg : editorPicks) {
                bookMap.get(editorPickArg.getISBN()).setEditorPick(editorPickArg.isEditorPick());
            }
        } finally {
            for (ReadWriteLock l : acquiredLocks) {
                l.writeLock().unlock();
            }
            globalLock.readLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#buyBooks(java.util.Set)
	 */
    @Override
    public void buyBooks(Set<BookCopy> bookCopiesToBuy) throws BookStoreException {
        if (bookCopiesToBuy == null) {
            throw new BookStoreException(BookStoreConstants.NULL_INPUT);
        }

        globalLock.readLock().lock();
        List<ReadWriteLock> acquiredLocks = new ArrayList<>();
        try {
            for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
                validate(bookCopyToBuy);
            }

            List<Integer> isbns = bookCopiesToBuy.stream().map(BookCopy::getISBN).sorted().collect(Collectors.toList());

            for (Integer isbn : isbns) {
                ReadWriteLock lock = bookLocks.get(isbn);
                lock.writeLock().lock();
                acquiredLocks.add(lock);
            }

            // Check availability
            boolean saleMiss = false;
            Map<Integer, Integer> salesMisses = new HashMap<>();

            for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
                BookStoreBook book = bookMap.get(bookCopyToBuy.getISBN());
                if (!book.areCopiesInStore(bookCopyToBuy.getNumCopies())) {
                    salesMisses.put(bookCopyToBuy.getISBN(), bookCopyToBuy.getNumCopies() - book.getNumCopies());
                    saleMiss = true;
                }
            }

            if (saleMiss) {
                for (Map.Entry<Integer, Integer> saleMissEntry : salesMisses.entrySet()) {
                    BookStoreBook book = bookMap.get(saleMissEntry.getKey());
                    book.addSaleMiss(saleMissEntry.getValue());
                }
                throw new BookStoreException(BookStoreConstants.BOOK + BookStoreConstants.NOT_AVAILABLE);
            }

            // Perform the purchase
            for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
                BookStoreBook book = bookMap.get(bookCopyToBuy.getISBN());
                book.buyCopies(bookCopyToBuy.getNumCopies());
            }
        } finally {
            for (ReadWriteLock l : acquiredLocks) {
                l.writeLock().unlock();
            }
            globalLock.readLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#getBooksByISBN(java.util.
	 * Set)
	 */
    @Override
    public List<StockBook> getBooksByISBN(Set<Integer> isbnSet) throws BookStoreException {
        if (isbnSet == null) {
            throw new BookStoreException(BookStoreConstants.NULL_INPUT);
        }

        globalLock.readLock().lock();
        List<ReadWriteLock> acquiredLocks = new ArrayList<>();
        try {
            for (Integer ISBN : isbnSet) {
                validateISBNInStock(ISBN);
            }

            List<Integer> sortedIsbns = isbnSet.stream().sorted().collect(Collectors.toList());
            for (Integer isbn : sortedIsbns) {
                ReadWriteLock lock = bookLocks.get(isbn);
                lock.readLock().lock();
                acquiredLocks.add(lock);
            }

            return sortedIsbns.stream()
                    .map(isbn -> bookMap.get(isbn).immutableStockBook())
                    .collect(Collectors.toList());
        } finally {
            for (ReadWriteLock l : acquiredLocks) {
                l.readLock().unlock();
            }
            globalLock.readLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getBooks(java.util.Set)
	 */
    @Override
    public List<Book> getBooks(Set<Integer> isbnSet) throws BookStoreException {
        if (isbnSet == null) {
            throw new BookStoreException(BookStoreConstants.NULL_INPUT);
        }

        globalLock.readLock().lock();
        List<ReadWriteLock> acquiredLocks = new ArrayList<>();
        try {
            for (Integer ISBN : isbnSet) {
                validateISBNInStock(ISBN);
            }

            List<Integer> sortedIsbns = isbnSet.stream().sorted().collect(Collectors.toList());
            for (Integer isbn : sortedIsbns) {
                ReadWriteLock lock = bookLocks.get(isbn);
                lock.readLock().lock();
                acquiredLocks.add(lock);
            }

            return sortedIsbns.stream()
                    .map(isbn -> bookMap.get(isbn).immutableBook())
                    .collect(Collectors.toList());
        } finally {
            for (ReadWriteLock l : acquiredLocks) {
                l.readLock().unlock();
            }
            globalLock.readLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getEditorPicks(int)
	 */
    @Override
    public List<Book> getEditorPicks(int numBooks) throws BookStoreException {
        globalLock.readLock().lock();
        try {
            if (numBooks < 0) {
                throw new BookStoreException("numBooks = " + numBooks + ", but it must be positive");
            }

            List<BookStoreBook> listAllEditorPicks = bookMap.values().stream()
                    .filter(BookStoreBook::isEditorPick)
                    .collect(Collectors.toList());

            Random rand = new Random();
            Set<Integer> tobePicked = new HashSet<>();
            int rangePicks = listAllEditorPicks.size();

            if (rangePicks <= numBooks) {
                for (int i = 0; i < listAllEditorPicks.size(); i++) {
                    tobePicked.add(i);
                }
            } else {
                while (tobePicked.size() < numBooks) {
                    int randNum = rand.nextInt(rangePicks);
                    tobePicked.add(randNum);
                }
            }

            return tobePicked.stream()
                    .map(index -> listAllEditorPicks.get(index).immutableBook())
                    .collect(Collectors.toList());
        } finally {
            globalLock.readLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getTopRatedBooks(int)
	 */
	@Override
	public List<Book> getTopRatedBooks(int numBooks) throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooksInDemand()
	 */
	@Override
	public List<StockBook> getBooksInDemand() throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#rateBooks(java.util.Set)
	 */
	@Override
	public void rateBooks(Set<BookRating> bookRating) throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#removeAllBooks()
	 */
	@Override
	public void removeAllBooks() throws BookStoreException {
		globalLock.writeLock().lock();
		try {
			bookMap.clear();
			bookLocks.clear();
		} finally {
			globalLock.writeLock().unlock();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#removeBooks(java.util.Set)
	 */
    @Override
    public void removeBooks(Set<Integer> isbnSet) throws BookStoreException {
        globalLock.writeLock().lock();
        try {
            if (isbnSet == null) {
                throw new BookStoreException(BookStoreConstants.NULL_INPUT);
            }

            for (Integer ISBN : isbnSet) {
                if (BookStoreUtility.isInvalidISBN(ISBN)) {
                    throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
                }
                if (!bookMap.containsKey(ISBN)) {
                    throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
                }
            }

            for (int isbn : isbnSet) {
                bookMap.remove(isbn);
                bookLocks.remove(isbn);
            }
        } finally {
            globalLock.writeLock().unlock();
        }
    }
}