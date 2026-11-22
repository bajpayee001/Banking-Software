package com.bank.transaction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AccountLockManager provides deterministic locking order for account resources.
 * By enforcing a total order (e.g. lexicographical sorting on Account IDs: lock lower ID first,
 * then lock higher ID), Coffman's "Circular Wait" condition is strictly broken.
 * This guarantees deadlock-free concurrent two-party transfers.
 */
@Component
public class AccountLockManager {

    private static final Logger log = LoggerFactory.getLogger(AccountLockManager.class);
    private final ConcurrentHashMap<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();

    public ReentrantLock getLock(String accountId) {
        return accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock(true)); // fair lock
    }

    public static class OrderedLockPair implements AutoCloseable {
        private final ReentrantLock firstLock;
        private final ReentrantLock secondLock;

        public OrderedLockPair(ReentrantLock firstLock, ReentrantLock secondLock) {
            this.firstLock = firstLock;
            this.secondLock = secondLock;
        }

        @Override
        public void close() {
            if (secondLock != null && secondLock.isHeldByCurrentThread()) {
                secondLock.unlock();
            }
            if (firstLock != null && firstLock.isHeldByCurrentThread()) {
                firstLock.unlock();
            }
        }
    }

    /**
     * Acquires locks on two accounts in deterministic order (smaller account ID first).
     */
    public OrderedLockPair acquireDeterministicLocks(String acc1, String acc2) {
        if (acc1 == null && acc2 == null) {
            return new OrderedLockPair(null, null);
        }
        if (acc1 != null && acc2 == null) {
            ReentrantLock lock = getLock(acc1);
            lock.lock();
            return new OrderedLockPair(lock, null);
        }
        if (acc1 == null) {
            ReentrantLock lock = getLock(acc2);
            lock.lock();
            return new OrderedLockPair(lock, null);
        }

        // Both accounts present (TRANSFER operation)
        String firstId = acc1.compareTo(acc2) <= 0 ? acc1 : acc2;
        String secondId = acc1.compareTo(acc2) <= 0 ? acc2 : acc1;

        ReentrantLock lock1 = getLock(firstId);
        ReentrantLock lock2 = getLock(secondId);

        log.debug("Acquiring locks deterministically: first={}, second={}", firstId, secondId);
        lock1.lock();
        lock2.lock();

        return new OrderedLockPair(lock1, lock2);
    }
}
