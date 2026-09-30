package com.example.humantypingime;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Cancels an asynchronous operation when its caller stops waiting. */
final class BoundedFutureWait {
    private BoundedFutureWait() { }

    static <T> T get(Future<T> future, long timeout, TimeUnit unit)
            throws ExecutionException, InterruptedException, TimeoutException {
        try {
            return future.get(timeout, unit);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
