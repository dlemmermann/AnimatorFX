package com.dlsc.animatorfx.player;

import javafx.application.Platform;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.testfx.api.FxToolkit;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Starts the headless JavaFX toolkit once and constructs test fixtures and runs every test method
 * on the FX application thread. Use with {@code @ExtendWith(FxThreadExtension.class)}.
 */
public final class FxThreadExtension implements InvocationInterceptor {

    private static boolean started;

    private static synchronized void start() throws Exception {
        if (!started) {
            FxToolkit.registerPrimaryStage();
            started = true;
        }
    }

    @Override
    public <T> T interceptTestClassConstructor(Invocation<T> invocation,
                                               ReflectiveInvocationContext<Constructor<T>> invocationContext,
                                               ExtensionContext extensionContext) throws Throwable {
        start();
        return invokeOnFxThread(invocation);
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) throws Throwable {
        start();
        invokeOnFxThread(invocation);
    }

    private static <T> T invokeOnFxThread(Invocation<T> invocation) throws Throwable {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(invocation.proceed());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Test did not finish on the FX application thread within 30 s");
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }
}
