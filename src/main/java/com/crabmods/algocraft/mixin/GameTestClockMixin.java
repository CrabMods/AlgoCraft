package com.crabmods.algocraft.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/** Keep asynchronous real-judge tests on the same wall-clock tick pace as gameplay. */
@Mixin(GameTestServer.class)
public abstract class GameTestClockMixin {
    @Inject(method = "tickServer", at = @At("TAIL"))
    private void algocraft$paceRealTimeTests(java.util.function.BooleanSupplier hasTime, CallbackInfo callback) {
        if (Boolean.getBoolean("algocraft.tests.realtimeGameTicks")) {
            // GameTestServer normally spins without the ordinary server's wait.
            // Its tick deadline must not outrun the isolated judge's real-time budget.
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50);
            long remaining;
            while ((remaining = deadline - System.nanoTime()) > 0 && !Thread.currentThread().isInterrupted()) {
                LockSupport.parkNanos(remaining);
            }
        }
    }
}
