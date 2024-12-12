package com.acertainbookstore.business;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;
import com.acertainbookstore.utils.BookStoreUtility;

/** {@link SingleLockConcurrentCertainBookStore} implements the {@link BookStore} and
 * {@link StockManager} functionalities.
 * 
 * @see BookStore
 * @see StockManager
 */
public class SingleLockConcurrentCertainBookStore implements BookStore, StockManager {

    /** The mapping of books from ISBN to {@link BookStoreBook}. */
    private Map<Integer, BookStoreBook> bookMap = null;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public SingleLockConcurrentCertainBookStore() {
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
    public void addBooks(Set<StockBook> bookSet) throws BookStoreException {
        rwLock.writeLock().lock();
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
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addCopies(java.util.Set)
	 */
    public void addCopies(Set<BookCopy> bookCopiesSet) throws BookStoreException {
        rwLock.writeLock().lock();
        try {
            if (bookCopiesSet == null) {
                throw new BookStoreException(BookStoreConstants.NULL_INPUT);
            }

            for (BookCopy bookCopy : bookCopiesSet) {
                validate(bookCopy);
            }

            for (BookCopy bookCopy : bookCopiesSet) {
                int isbn = bookCopy.getISBN();
                int numCopies = bookCopy.getNumCopies();
                BookStoreBook book = bookMap.get(isbn);
                book.addCopies(numCopies);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooks()
	 */
    public List<StockBook> getBooks() {
        rwLock.readLock().lock();
        try {
            Collection<BookStoreBook> bookMapValues = bookMap.values();
            return bookMapValues.stream()
                    .map(book -> book.immutableStockBook())
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#updateEditorPicks(java.util
	 * .Set)
	 */
    public void updateEditorPicks(Set<BookEditorPick> editorPicks) throws BookStoreException {
        rwLock.writeLock().lock();
        try {
            if (editorPicks == null) {
                throw new BookStoreException(BookStoreConstants.NULL_INPUT);
            }

            for (BookEditorPick editorPickArg : editorPicks) {
                validate(editorPickArg);
            }

            for (BookEditorPick editorPickArg : editorPicks) {
                bookMap.get(editorPickArg.getISBN()).setEditorPick(editorPickArg.isEditorPick());
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#buyBooks(java.util.Set)
	 */
    public void buyBooks(Set<BookCopy> bookCopiesToBuy) throws BookStoreException {
        rwLock.writeLock().lock();
        try {
            if (bookCopiesToBuy == null) {
                throw new BookStoreException(BookStoreConstants.NULL_INPUT);
            }

            int isbn;
            BookStoreBook book;
            Boolean saleMiss = false;
            Map<Integer, Integer> salesMisses = new HashMap<>();

            for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
                isbn = bookCopyToBuy.getISBN();
                validate(bookCopyToBuy);
                book = bookMap.get(isbn);
                if (!book.areCopiesInStore(bookCopyToBuy.getNumCopies())) {
                    salesMisses.put(isbn, bookCopyToBuy.getNumCopies() - book.getNumCopies());
                    saleMiss = true;
                }
            }

            if (saleMiss) {
                for (Map.Entry<Integer, Integer> saleMissEntry : salesMisses.entrySet()) {
                    book = bookMap.get(saleMissEntry.getKey());
                    book.addSaleMiss(saleMissEntry.getValue());
                }
                throw new BookStoreException(BookStoreConstants.BOOK + BookStoreConstants.NOT_AVAILABLE);
            }

            for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
                book = bookMap.get(bookCopyToBuy.getISBN());
                book.buyCopies(bookCopyToBuy.getNumCopies());
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#getBooksByISBN(java.util.
	 * Set)
	 */
    public List<StockBook> getBooksByISBN(Set<Integer> isbnSet) throws BookStoreException {
        rwLock.readLock().lock();
        try {
            if (isbnSet == null) {
                throw new BookStoreException(BookStoreConstants.NULL_INPUT);
            }

            for (Integer ISBN : isbnSet) {
                validateISBNInStock(ISBN);
            }

            return isbnSet.stream()
                    .map(isbn -> bookMap.get(isbn).immutableStockBook())
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getBooks(java.util.Set)
	 */
    public List<Book> getBooks(Set<Integer> isbnSet) throws BookStoreException {
        rwLock.readLock().lock();
        try {
            if (isbnSet == null) {
                throw new BookStoreException(BookStoreConstants.NULL_INPUT);
            }

            for (Integer ISBN : isbnSet) {
                validateISBNInStock(ISBN);
            }

            return isbnSet.stream()
                    .map(isbn -> bookMap.get(isbn).immutableBook())
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getEditorPicks(int)
	 */
    public List<Book> getEditorPicks(int numBooks) throws BookStoreException {
        rwLock.readLock().lock();
        try {
            if (numBooks < 0) {
                throw new BookStoreException("numBooks = " + numBooks + ", but it must be positive");
            }

            List<BookStoreBook> listAllEditorPicks = bookMap.entrySet().stream()
                    .map(pair -> pair.getValue())
                    .filter(book -> book.isEditorPick())
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
            rwLock.readLock().unlock();
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
    public void removeAllBooks() throws BookStoreException {
        rwLock.writeLock().lock();
        try {
            bookMap.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#removeBooks(java.util.Set)
	 */
    public void removeBooks(Set<Integer> isbnSet) throws BookStoreException {
        rwLock.writeLock().lock();
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
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}